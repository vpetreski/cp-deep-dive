"""Solve routes — /solve, /solution/{id}, SSE stream, cancel, log."""

from __future__ import annotations

import asyncio
import datetime as dt
import hashlib
import json
from collections.abc import AsyncIterator
from typing import Any, cast

from fastapi import APIRouter, Depends, Header, Request, Response, status
from nsp_core.domain import ObjectiveWeights, SolveParams
from nsp_core.loader import InstanceValidationError, parse_instance
from sqlmodel import select
from sse_starlette.sse import EventSourceResponse

from py_api.config import Settings
from py_api.db import Database, InstanceRow, JobEventRow, JobRow
from py_api.dependencies import get_db, get_pool, get_settings
from py_api.errors import ApiError, ErrorCode
from py_api.jobs import SolvePool
from py_api.serialize import solve_response_dict

router = APIRouter(tags=["solve"])


# ------------------------------------------------------------------ #
# Helpers
# ------------------------------------------------------------------ #


async def _read_solve_body(request: Request, settings: Settings) -> dict[str, Any]:
    raw = await request.body()
    if len(raw) > settings.max_body_bytes:
        raise ApiError(
            status_code=413,
            code=ErrorCode.INSTANCE_TOO_LARGE,
            message=f"Payload {len(raw)} bytes exceeds limit {settings.max_body_bytes}.",
        )
    if not raw:
        raise ApiError(
            status_code=400,
            code=ErrorCode.REQUEST_MALFORMED,
            message="Request body is empty.",
        )
    try:
        body = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise ApiError(
            status_code=400,
            code=ErrorCode.REQUEST_MALFORMED,
            message=f"Invalid JSON: {exc.msg}",
        ) from exc
    if not isinstance(body, dict):
        raise ApiError(
            status_code=400,
            code=ErrorCode.REQUEST_MALFORMED,
            message="Request body must be a JSON object.",
        )
    return cast(dict[str, Any], body)


async def _resolve_instance(
    body: dict[str, Any], db: Database
) -> tuple[Any, dict[str, Any]]:
    """Return (Instance, raw_instance_json) from a SolveRequest body."""
    inline = body.get("instance")
    instance_id = body.get("instanceId")
    if inline is not None and isinstance(inline, dict):
        try:
            inst = parse_instance(inline)
        except InstanceValidationError as exc:
            raise ApiError(
                status_code=422,
                code=ErrorCode.INSTANCE_INVALID,
                message="Instance failed schema validation.",
                details={
                    "errors": [{"path": p, "message": m} for p, m in exc.errors]
                },
            ) from exc
        return inst, cast(dict[str, Any], inline)
    if instance_id:
        async with db.session() as sess:
            row = await sess.get(InstanceRow, str(instance_id))
        if row is None:
            raise ApiError(
                status_code=404,
                code=ErrorCode.INSTANCE_NOT_FOUND,
                message=f"No instance with id {instance_id!r}.",
            )
        raw: dict[str, Any] = dict(row.raw_json)
        return parse_instance(raw), raw
    raise ApiError(
        status_code=400,
        code=ErrorCode.REQUEST_MALFORMED,
        message="SolveRequest must include 'instance' or 'instanceId'.",
    )


def _hash_body(body: dict[str, Any]) -> str:
    canonical = json.dumps(body, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def _build_params(raw_params: dict[str, Any] | None) -> tuple[SolveParams, str]:
    """Return (SolveParams, objective-mode)."""
    params = SolveParams.from_mapping(raw_params or {})
    # If any objective weight is explicitly >0, run the weighted model.
    has_weights = bool(raw_params and raw_params.get("objectiveWeights"))
    mode = "weighted" if has_weights else "hard"
    return params, mode


# ------------------------------------------------------------------ #
# POST /solve
# ------------------------------------------------------------------ #


@router.post("/solve", status_code=status.HTTP_202_ACCEPTED)
async def post_solve(
    request: Request,
    response: Response,
    idempotency_key: str | None = Header(default=None, alias="Idempotency-Key"),
    settings: Settings = Depends(get_settings),
    db: Database = Depends(get_db),
    pool: SolvePool = Depends(get_pool),
) -> dict[str, Any]:
    body = await _read_solve_body(request, settings)
    # Basic shape
    if not isinstance(body.get("instance") or body.get("instanceId"), (dict, str)):
        raise ApiError(
            status_code=400,
            code=ErrorCode.REQUEST_MALFORMED,
            message="SolveRequest must include 'instance' or 'instanceId'.",
        )

    body_hash = _hash_body(body) if idempotency_key else None

    if idempotency_key and body_hash:
        existing_id, conflict = await pool.lookup_idempotency(idempotency_key, body_hash)
        if conflict:
            raise ApiError(
                status_code=409,
                code=ErrorCode.JOB_CONFLICT,
                message="Idempotency-Key already used with a different body.",
            )
        if existing_id:
            record = pool.get(existing_id)
            if record is not None:
                response.headers["Location"] = f"/solution/{record.id}"
                return {
                    "jobId": record.id,
                    "status": record.status if record.status in {"pending", "queued", "running"}
                    else "queued",
                    "instanceId": record.instance_id,
                    "createdAt": record.created_at.isoformat(timespec="seconds"),
                }

    instance, _raw_instance = await _resolve_instance(body, db)
    raw_params = body.get("params") if isinstance(body.get("params"), dict) else None
    params, mode_from_weights = _build_params(raw_params)
    # Default to the richer weighted model when there are soft preferences.
    objective_mode = settings.default_solve_objective
    if mode_from_weights == "weighted":
        objective_mode = "weighted"
    if not instance.preferences and objective_mode == "weighted":
        # No soft preferences — hard-only model is cheaper.
        objective_mode = "weighted"
    weights: ObjectiveWeights | None = None
    if raw_params and isinstance(raw_params.get("objectiveWeights"), dict):
        weights = ObjectiveWeights.from_mapping(
            cast(dict[str, int], raw_params.get("objectiveWeights"))
        )

    # Pool saturation → 503
    if pool.active_count() >= pool._max_concurrent:  # noqa: SLF001
        raise ApiError(
            status_code=503,
            code=ErrorCode.SOLVER_POOL_FULL,
            message="Solver pool saturated; retry later.",
        )

    record = await pool.create_and_run(
        instance=instance,
        params=params,
        objective=objective_mode,
        weights=weights,
        idempotency_key=idempotency_key,
        idempotency_hash=body_hash,
        params_json=raw_params or {},
    )
    response.headers["Location"] = f"/solution/{record.id}"
    return {
        "jobId": record.id,
        "status": "queued",
        "instanceId": record.instance_id,
        "createdAt": record.created_at.isoformat(timespec="seconds"),
    }


# ------------------------------------------------------------------ #
# GET /solution/{jobId}
# ------------------------------------------------------------------ #


async def _response_from_db(
    db: Database, job_id: str
) -> dict[str, Any] | None:
    async with db.session() as sess:
        row = await sess.get(JobRow, job_id)
        if row is None:
            return None
        # Find the most recent incumbent / terminal event payload.
        stmt = (
            select(JobEventRow)
            .where(JobEventRow.job_id == job_id)
            .where(JobEventRow.kind.in_(("incumbent", "terminal")))  # type: ignore[attr-defined]
            .order_by(JobEventRow.seq.desc())  # type: ignore[attr-defined]
            .limit(1)
        )
        latest = (await sess.execute(stmt)).scalars().first()
    if latest is not None and isinstance(latest.payload_json, dict):
        return dict(latest.payload_json)
    # Fall back to a minimal envelope from the row.
    return solve_response_dict(
        job_id=row.id,
        instance_id=row.instance_id,
        status=row.status,
        result=None,
        created_at=row.created_at,
        started_at=row.started_at,
        ended_at=row.ended_at,
        error=row.error,
    )


@router.get("/solution/{job_id}")
async def get_solution(
    job_id: str,
    pool: SolvePool = Depends(get_pool),
    db: Database = Depends(get_db),
) -> dict[str, Any]:
    record = pool.get(job_id)
    if record is not None:
        return record.snapshot_response()
    body = await _response_from_db(db, job_id)
    if body is None:
        raise ApiError(
            status_code=404,
            code=ErrorCode.JOB_NOT_FOUND,
            message=f"No job with id {job_id!r}.",
        )
    return body


# ------------------------------------------------------------------ #
# SSE stream
# ------------------------------------------------------------------ #


@router.get("/solutions/{job_id}/stream")
async def stream_solutions(
    job_id: str,
    settings: Settings = Depends(get_settings),
    pool: SolvePool = Depends(get_pool),
    db: Database = Depends(get_db),
) -> EventSourceResponse:
    record = pool.get(job_id)
    hub = pool.hub(job_id)
    if record is None and hub is None:
        # Job not in-memory — maybe it's in the DB (terminal snapshot only).
        body = await _response_from_db(db, job_id)
        if body is None:
            raise ApiError(
                status_code=404,
                code=ErrorCode.JOB_NOT_FOUND,
                message=f"No job with id {job_id!r}.",
            )
        return EventSourceResponse(_single_event(body))

    async def event_generator() -> AsyncIterator[dict[str, Any]]:
        assert hub is not None
        async with hub.subscribe() as q:
            count = 0
            while True:
                try:
                    event = await asyncio.wait_for(q.get(), timeout=60.0)
                except TimeoutError:
                    # Heartbeat ping.
                    yield {"event": "ping", "data": ""}
                    continue
                if event is None:
                    break
                count += 1
                if count > settings.sse_event_cap:
                    yield {
                        "event": "limit_reached",
                        "data": json.dumps({"cap": settings.sse_event_cap}),
                    }
                    break
                yield {
                    "event": "solution",
                    "data": json.dumps(event.get("data", {})),
                }

    return EventSourceResponse(event_generator())


async def _single_event(body: dict[str, Any]) -> AsyncIterator[dict[str, Any]]:
    yield {"event": "solution", "data": json.dumps(body)}


# ------------------------------------------------------------------ #
# Cancel + log
# ------------------------------------------------------------------ #

_TERMINAL = {
    "feasible",
    "optimal",
    "infeasible",
    "unknown",
    "timeout",
    "cancelled",
    "modelInvalid",
    "error",
}


@router.post(
    "/solve/{job_id}/cancel", status_code=status.HTTP_202_ACCEPTED, response_model=None
)
async def cancel_solve(
    job_id: str,
    pool: SolvePool = Depends(get_pool),
    db: Database = Depends(get_db),
) -> dict[str, Any]:
    record = pool.get(job_id)
    if record is None:
        # Check DB for terminal.
        body = await _response_from_db(db, job_id)
        if body is None:
            raise ApiError(
                status_code=404,
                code=ErrorCode.JOB_NOT_FOUND,
                message=f"No job with id {job_id!r}.",
            )
        status_val = str(body.get("status", ""))
        if status_val in _TERMINAL:
            raise ApiError(
                status_code=409,
                code=ErrorCode.JOB_CONFLICT,
                message="Job is already in a terminal state.",
            )
        return body
    if record.status in _TERMINAL:
        raise ApiError(
            status_code=409,
            code=ErrorCode.JOB_CONFLICT,
            message="Job is already in a terminal state.",
        )
    await pool.cancel(job_id)
    # Wait briefly for the solver to observe the flag so the response reflects it.
    for _ in range(10):
        if record.status in _TERMINAL:
            break
        await asyncio.sleep(0.05)
    # Also assume cooperative cancellation — flip state to cancelled if still running.
    if record.status == "running":
        record.status = "cancelled"
        record.ended_at = dt.datetime.now(dt.UTC)
    return record.snapshot_response()


@router.get("/solve/{job_id}/log")
async def get_solve_log(
    job_id: str,
    pool: SolvePool = Depends(get_pool),
    db: Database = Depends(get_db),
) -> Response:
    record = pool.get(job_id)
    async with db.session() as sess:
        job_row = await sess.get(JobRow, job_id)
        if job_row is None and record is None:
            raise ApiError(
                status_code=404,
                code=ErrorCode.JOB_NOT_FOUND,
                message=f"No job with id {job_id!r}.",
            )
        stmt = (
            select(JobEventRow)
            .where(JobEventRow.job_id == job_id)
            .where(JobEventRow.kind == "log")
            .order_by(JobEventRow.seq)  # type: ignore[arg-type]
        )
        events = (await sess.execute(stmt)).scalars().all()
    lines = [str(e.payload_json.get("line", "")) for e in events]
    if record is not None:
        lines = record.log_lines + lines
    return Response(content="\n".join(lines), media_type="text/plain; charset=utf-8")
