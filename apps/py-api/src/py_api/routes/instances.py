"""Instance CRUD routes — POST / GET / DELETE / list.

Persists instances as ``InstanceRow`` with the raw JSON body stored intact
(so GET /instances/{id} returns what the client uploaded, not a lossy
projection). The canonical parse + validation uses
``nsp_core.loader.parse_instance`` which enforces the shared JSON Schema.
"""

from __future__ import annotations

import base64
import json
from typing import Any, cast

from fastapi import APIRouter, Depends, Query, Request, Response, status
from nsp_core.loader import InstanceValidationError, parse_instance
from sqlmodel import select

from py_api.db import Database, InstanceRow, JobEventRow, JobRow
from py_api.dependencies import get_db, get_metrics, get_settings
from py_api.errors import ApiError, ErrorCode
from py_api.metrics import Metrics
from py_api.serialize import instance_summary

router = APIRouter(tags=["instances"])


def _parse_body(raw: Any) -> dict[str, Any]:
    if not isinstance(raw, dict):
        raise ApiError(
            status_code=400,
            code=ErrorCode.REQUEST_MALFORMED,
            message="Request body must be a JSON object.",
        )
    return cast(dict[str, Any], raw)


def _validate_body_size(raw_bytes: int, limit: int) -> None:
    if raw_bytes > limit:
        raise ApiError(
            status_code=413,
            code=ErrorCode.INSTANCE_TOO_LARGE,
            message=f"Payload {raw_bytes} bytes exceeds limit {limit}.",
        )


async def _read_json_body(request: Request) -> dict[str, Any]:
    settings = request.app.state.settings
    raw = await request.body()
    _validate_body_size(len(raw), settings.max_body_bytes)
    if not raw:
        raise ApiError(
            status_code=400,
            code=ErrorCode.REQUEST_MALFORMED,
            message="Request body is empty.",
        )
    try:
        loaded = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise ApiError(
            status_code=400,
            code=ErrorCode.REQUEST_MALFORMED,
            message=f"Invalid JSON: {exc.msg}",
            details={"line": exc.lineno, "column": exc.colno},
        ) from exc
    return _parse_body(loaded)


async def _upsert_instance(db: Database, raw_body: dict[str, Any]) -> dict[str, Any]:
    try:
        inst = parse_instance(raw_body)
    except InstanceValidationError as exc:
        raise ApiError(
            status_code=422,
            code=ErrorCode.INSTANCE_INVALID,
            message="Instance body failed schema validation.",
            details={"errors": [{"path": p, "message": m} for p, m in exc.errors]},
        ) from exc
    # Persist the canonical form (parsed → dict); that fills defaults and
    # normalises shift minute-offsets.
    from py_api.serialize import instance_to_dict

    canonical = instance_to_dict(inst)
    row = InstanceRow(
        id=inst.id,
        name=inst.name,
        source=inst.source,
        horizon_days=inst.horizon_days,
        nurse_count=len(inst.nurses),
        shift_count=len(inst.shifts),
        coverage_slot_count=len(inst.coverage),
        raw_json=canonical,
    )
    async with db.session() as sess:
        existing = await sess.get(InstanceRow, row.id)
        if existing is not None:
            # Update in place (POST acts as upsert — the client asked for this id).
            existing.name = row.name
            existing.source = row.source
            existing.horizon_days = row.horizon_days
            existing.nurse_count = row.nurse_count
            existing.shift_count = row.shift_count
            existing.coverage_slot_count = row.coverage_slot_count
            existing.raw_json = row.raw_json
            sess.add(existing)
        else:
            sess.add(row)
        await sess.commit()
    return canonical


@router.post("/instances", status_code=status.HTTP_201_CREATED)
async def create_instance(
    request: Request,
    response: Response,
    db: Database = Depends(get_db),
    metrics: Metrics = Depends(get_metrics),
    _settings: Any = Depends(get_settings),
) -> dict[str, Any]:
    raw_body = await _read_json_body(request)
    canonical = await _upsert_instance(db, raw_body)
    metrics.instances.inc()
    response.headers["Location"] = f"/instances/{canonical['id']}"
    return canonical


@router.get("/instances")
async def list_instances(
    limit: int = Query(default=20, ge=1, le=100),
    cursor: str | None = Query(default=None),
    db: Database = Depends(get_db),
) -> dict[str, Any]:
    offset = 0
    if cursor:
        try:
            decoded = base64.urlsafe_b64decode(cursor.encode("ascii")).decode("ascii")
            payload = json.loads(decoded)
            offset = int(payload.get("offset", 0))
            if offset < 0:
                raise ValueError
        except (ValueError, json.JSONDecodeError, UnicodeDecodeError) as exc:
            raise ApiError(
                status_code=400,
                code=ErrorCode.REQUEST_MALFORMED,
                message="Invalid cursor.",
            ) from exc
    async with db.session() as sess:
        stmt = (
            select(InstanceRow)
            .order_by(InstanceRow.created_at.desc(), InstanceRow.id)  # type: ignore[attr-defined]
            .offset(offset)
            .limit(limit + 1)
        )
        rows = (await sess.execute(stmt)).scalars().all()
    has_more = len(rows) > limit
    page = rows[:limit]
    items = [
        instance_summary(parse_instance(dict(r.raw_json)), r.created_at)
        for r in page
    ]
    out: dict[str, Any] = {"items": items}
    if has_more:
        token = base64.urlsafe_b64encode(
            json.dumps({"offset": offset + limit}).encode("ascii")
        ).decode("ascii")
        out["nextCursor"] = token
    return out


@router.get("/instances/{instance_id}")
async def get_instance(
    instance_id: str,
    db: Database = Depends(get_db),
) -> dict[str, Any]:
    async with db.session() as sess:
        row = await sess.get(InstanceRow, instance_id)
    if row is None:
        raise ApiError(
            status_code=404,
            code=ErrorCode.INSTANCE_NOT_FOUND,
            message=f"No instance with id {instance_id!r}.",
        )
    raw: dict[str, Any] = dict(row.raw_json)
    return raw


@router.delete("/instances/{instance_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_instance(
    instance_id: str,
    db: Database = Depends(get_db),
) -> Response:
    async with db.session() as sess:
        row = await sess.get(InstanceRow, instance_id)
        if row is None:
            raise ApiError(
                status_code=404,
                code=ErrorCode.INSTANCE_NOT_FOUND,
                message=f"No instance with id {instance_id!r}.",
            )
        # Cascade — manual SQLModel delete for jobs + events tied to this instance.
        job_rows = (
            await sess.execute(select(JobRow).where(JobRow.instance_id == instance_id))
        ).scalars().all()
        for job in job_rows:
            events = (
                await sess.execute(
                    select(JobEventRow).where(JobEventRow.job_id == job.id)
                )
            ).scalars().all()
            for ev in events:
                await sess.delete(ev)
            await sess.delete(job)
        await sess.delete(row)
        await sess.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
