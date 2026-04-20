"""Solve job orchestration.

The solver is pure-Python wrapping a C++ core; it is intrinsically synchronous
and blocks the event loop. We move every solve onto a worker thread via
``asyncio.to_thread`` and communicate partial results back through a per-job
``asyncio.Queue`` hub. SSE endpoints subscribe to the hub; the DB captures
every event so late subscribers can replay.

Concurrency is capped by a semaphore; exhaustion returns 503 solver.poolFull.
Cancellation is cooperative: the CP-SAT ``solution_callback`` calls a user
``ProgressCallback``; we wire that to a flag-checking wrapper that stops
CP-SAT once the client requests cancellation.
"""

from __future__ import annotations

import asyncio
import contextlib
import datetime as dt
import logging
import uuid
from collections import defaultdict
from dataclasses import dataclass, field
from typing import Any

from nsp_core.domain import (
    Instance,
    ObjectiveWeights,
    SolveParams,
    SolveResult,
    SolveStatus,
)
from nsp_core.solver import solve as solver_solve

from py_api.metrics import Metrics
from py_api.serialize import solve_response_dict

log = logging.getLogger("py_api.jobs")


# ------------------------------------------------------------------ #
# Job bookkeeping
# ------------------------------------------------------------------ #


@dataclass
class JobRecord:
    """In-memory, mutable mirror of the DB row plus runtime artifacts."""

    id: str
    instance_id: str
    status: str = "pending"
    created_at: dt.datetime = field(default_factory=lambda: dt.datetime.now(dt.UTC))
    started_at: dt.datetime | None = None
    ended_at: dt.datetime | None = None
    objective: float | None = None
    best_bound: float | None = None
    gap: float | None = None
    solve_time_seconds: float = 0.0
    error: str | None = None

    latest_result: SolveResult | None = None
    log_lines: list[str] = field(default_factory=list)
    cancel_requested: bool = False
    seq: int = 0  # monotonic event counter

    idempotency_key: str | None = None
    idempotency_hash: str | None = None
    params_json: dict[str, Any] = field(default_factory=dict)

    def snapshot_response(self) -> dict[str, Any]:
        return solve_response_dict(
            job_id=self.id,
            instance_id=self.instance_id,
            status=self.status,
            result=self.latest_result,
            created_at=self.created_at,
            started_at=self.started_at,
            ended_at=self.ended_at,
            error=self.error,
        )


# ------------------------------------------------------------------ #
# Per-job pub/sub hub
# ------------------------------------------------------------------ #


class JobHub:
    """Broadcast partial solutions + log lines to any number of SSE listeners.

    Subscribers get a fresh ``asyncio.Queue`` that is fed every event. Events
    also live in a bounded history so a late subscriber can replay what's been
    seen so far.
    """

    def __init__(self, *, history_cap: int) -> None:
        self._history: list[dict[str, Any]] = []
        self._subscribers: set[asyncio.Queue[dict[str, Any] | None]] = set()
        self._history_cap = history_cap
        self._closed = False
        self._lock = asyncio.Lock()

    async def publish(self, event: dict[str, Any]) -> None:
        async with self._lock:
            if self._closed:
                return
            if len(self._history) < self._history_cap:
                self._history.append(event)
            for q in list(self._subscribers):
                with contextlib.suppress(asyncio.QueueFull):
                    q.put_nowait(event)

    async def close(self) -> None:
        async with self._lock:
            self._closed = True
            for q in list(self._subscribers):
                with contextlib.suppress(asyncio.QueueFull):
                    q.put_nowait(None)
            self._subscribers.clear()

    @contextlib.asynccontextmanager
    async def subscribe(self) -> Any:
        q: asyncio.Queue[dict[str, Any] | None] = asyncio.Queue(maxsize=0)
        async with self._lock:
            # Replay history first.
            for ev in self._history:
                q.put_nowait(ev)
            if self._closed:
                q.put_nowait(None)
            else:
                self._subscribers.add(q)
        try:
            yield q
        finally:
            async with self._lock:
                self._subscribers.discard(q)


# ------------------------------------------------------------------ #
# Solve pool
# ------------------------------------------------------------------ #


class SolvePool:
    """Tracks jobs + caps concurrent solves.

    Public surface:
      - ``submit(instance, params, objective, idempotency_key=...)`` → JobRecord
      - ``get(job_id)`` → JobRecord | None
      - ``cancel(job_id)`` → sets the flag (returns the current record)
      - ``hub(job_id)`` → JobHub for SSE listeners
      - ``await shutdown()`` — cancel in-flight, wait for tasks
    """

    def __init__(
        self,
        *,
        metrics: Metrics,
        max_concurrent: int,
        event_cap: int = 10_000,
    ) -> None:
        self._metrics = metrics
        self._max_concurrent = max(1, max_concurrent)
        self._event_cap = event_cap
        self._semaphore = asyncio.Semaphore(self._max_concurrent)
        self._jobs: dict[str, JobRecord] = {}
        self._hubs: dict[str, JobHub] = {}
        self._tasks: dict[str, asyncio.Task[None]] = {}
        self._db_writer: DbJobWriter | None = None
        self._idempotency: dict[str, str] = {}  # key -> job_id
        self._idempotency_hashes: dict[str, str] = {}  # key -> body hash
        self._idempotency_ts: dict[str, dt.datetime] = {}
        self._lock = asyncio.Lock()

    def bind_db_writer(self, writer: DbJobWriter) -> None:
        self._db_writer = writer

    @property
    def pool_saturated(self) -> bool:
        return self._semaphore.locked() and self._semaphore._value <= 0  # noqa: SLF001

    def active_count(self) -> int:
        return sum(1 for j in self._jobs.values() if j.status == "running")

    def get(self, job_id: str) -> JobRecord | None:
        return self._jobs.get(job_id)

    def hub(self, job_id: str) -> JobHub | None:
        return self._hubs.get(job_id)

    async def purge_stale_idempotency(self, now: dt.datetime | None = None) -> None:
        now = now or dt.datetime.now(dt.UTC)
        cutoff = now - dt.timedelta(hours=24)
        async with self._lock:
            stale = [k for k, ts in self._idempotency_ts.items() if ts < cutoff]
            for k in stale:
                self._idempotency.pop(k, None)
                self._idempotency_hashes.pop(k, None)
                self._idempotency_ts.pop(k, None)

    async def lookup_idempotency(
        self, key: str, body_hash: str
    ) -> tuple[str | None, bool]:
        """Return (job_id, conflict).

        - ``(job_id, False)`` — this key+hash was seen before, reuse the job.
        - ``(None, True)`` — key was seen with a different body → conflict.
        - ``(None, False)`` — key unknown → caller proceeds with creation.
        """
        await self.purge_stale_idempotency()
        async with self._lock:
            existing_hash = self._idempotency_hashes.get(key)
            if existing_hash is None:
                return (None, False)
            if existing_hash != body_hash:
                return (None, True)
            return (self._idempotency.get(key), False)

    async def _register_idempotency(
        self, key: str, body_hash: str, job_id: str
    ) -> None:
        async with self._lock:
            self._idempotency[key] = job_id
            self._idempotency_hashes[key] = body_hash
            self._idempotency_ts[key] = dt.datetime.now(dt.UTC)

    async def create_and_run(
        self,
        *,
        instance: Instance,
        params: SolveParams,
        objective: str,
        weights: ObjectiveWeights | None = None,
        idempotency_key: str | None = None,
        idempotency_hash: str | None = None,
        params_json: dict[str, Any] | None = None,
    ) -> JobRecord:
        job_id = f"job-{uuid.uuid4().hex[:12]}"
        record = JobRecord(
            id=job_id,
            instance_id=instance.id,
            params_json=params_json or {},
            idempotency_key=idempotency_key,
            idempotency_hash=idempotency_hash,
        )
        hub = JobHub(history_cap=self._event_cap)
        self._jobs[job_id] = record
        self._hubs[job_id] = hub
        if idempotency_key and idempotency_hash:
            await self._register_idempotency(idempotency_key, idempotency_hash, job_id)
        if self._db_writer is not None:
            await self._db_writer.insert_job(record)
        task = asyncio.create_task(
            self._run_job(record, instance, params, objective, weights),
            name=f"solve:{job_id}",
        )
        self._tasks[job_id] = task
        return record

    async def cancel(self, job_id: str) -> JobRecord | None:
        record = self._jobs.get(job_id)
        if record is None:
            return None
        record.cancel_requested = True
        return record

    async def shutdown(self) -> None:
        for task in list(self._tasks.values()):
            task.cancel()
        for task in list(self._tasks.values()):
            with contextlib.suppress(BaseException):
                await task
        for hub in list(self._hubs.values()):
            await hub.close()

    # ---------------------------------------------------------------- #
    # Internals
    # ---------------------------------------------------------------- #

    async def _run_job(
        self,
        record: JobRecord,
        instance: Instance,
        params: SolveParams,
        objective: str,
        weights: ObjectiveWeights | None,
    ) -> None:
        hub = self._hubs[record.id]

        async with self._semaphore:
            # Transition → running
            record.status = "running"
            record.started_at = dt.datetime.now(dt.UTC)
            self._metrics.active_solves.inc()
            await self._emit_and_persist(record, kind="status")

            def _maybe_cancelled() -> None:
                if record.cancel_requested:
                    raise _CancelledDuringSearch(record.id)

            # Incumbent streaming. CP-SAT invokes the callback on its solver
            # thread; we marshal back to the asyncio loop via
            # ``run_coroutine_threadsafe`` so ``hub.publish`` runs under the
            # loop that owns its lock. The callback also relays cooperative
            # cancellation by raising an exception the solver respects.
            loop = asyncio.get_running_loop()

            def _progress(result: SolveResult) -> None:  # CP-SAT thread
                if record.cancel_requested:
                    raise _CancelledDuringSearch(record.id)
                record.latest_result = result
                if result.objective is not None:
                    record.objective = result.objective
                if result.best_bound is not None:
                    record.best_bound = result.best_bound
                snapshot = record.snapshot_response()
                snapshot["status"] = "running"
                event = {
                    "seq": 0,  # _publish_progress fills this in under the lock
                    "kind": "solution",
                    "data": snapshot,
                }
                asyncio.run_coroutine_threadsafe(
                    self._publish_progress(record, event), loop
                )

            try:
                _maybe_cancelled()
                result = await asyncio.to_thread(
                    solver_solve,
                    instance,
                    params,
                    objective=objective,
                    weights=weights,
                    job_id=record.id,
                    progress_callback=_progress,
                )
                record.latest_result = result
                record.solve_time_seconds = result.solve_time_seconds
                if result.objective is not None:
                    record.objective = result.objective
                    self._metrics.objective.observe(result.objective)
                if result.best_bound is not None:
                    record.best_bound = result.best_bound
                if result.gap is not None:
                    record.gap = result.gap
                if record.cancel_requested:
                    record.status = "cancelled"
                else:
                    record.status = _map_status(result.status)
                self._metrics.solve_seconds.observe(result.solve_time_seconds)
            except _CancelledDuringSearch:
                record.status = "cancelled"
            except asyncio.CancelledError:
                record.status = "cancelled"
                raise
            except Exception as exc:  # pragma: no cover - defensive
                log.exception("solver error job=%s", record.id)
                record.status = "error"
                record.error = str(exc)
            finally:
                record.ended_at = dt.datetime.now(dt.UTC)
                self._metrics.active_solves.dec()
                self._metrics.solves.labels(status=record.status).inc()
                snapshot = record.snapshot_response()
                await self._emit_and_persist(record, kind="terminal", snapshot=snapshot)
                await hub.close()
                self._tasks.pop(record.id, None)

    async def _emit_and_persist(
        self,
        record: JobRecord,
        *,
        kind: str,
        snapshot: dict[str, Any] | None = None,
    ) -> None:
        if snapshot is None:
            snapshot = record.snapshot_response()
        record.seq += 1
        event = {"seq": record.seq, "kind": kind, "data": snapshot}
        hub = self._hubs.get(record.id)
        if hub is not None:
            await hub.publish(event)
        if self._db_writer is not None:
            await self._db_writer.append_event(record.id, event, record)

    async def _publish_progress(
        self,
        record: JobRecord,
        event: dict[str, Any],
    ) -> None:
        """Publish an incumbent event from a solver-thread callback.

        Separate from ``_emit_and_persist`` so we can assign the sequence
        number under the hub lock and keep the CP-SAT callback side pure.
        """
        record.seq += 1
        event["seq"] = record.seq
        hub = self._hubs.get(record.id)
        if hub is not None:
            await hub.publish(event)
        if self._db_writer is not None:
            await self._db_writer.append_event(record.id, event, record)


def _map_status(status: SolveStatus) -> str:
    # SolveStatus values already carry the wire-name; guard against the
    # solver returning a status we don't enumerate.
    value: str = status.value
    if value == "modelInvalid":
        return "modelInvalid"
    return value


class _CancelledDuringSearch(RuntimeError):
    """Signalling exception — the progress callback raised to stop CP-SAT."""

    def __init__(self, job_id: str) -> None:
        super().__init__(f"cancelled: {job_id}")


# ------------------------------------------------------------------ #
# DB persistence for jobs / events
# ------------------------------------------------------------------ #


class DbJobWriter:
    """Writes job events + terminal summaries to SQLite via SQLModel.

    Kept separate from SolvePool so the pool stays DB-agnostic (useful for
    unit tests that swap in an in-memory writer stub).
    """

    def __init__(self, db: Any) -> None:
        self._db = db
        self._lock = asyncio.Lock()
        self._seq: dict[str, int] = defaultdict(int)

    async def insert_job(self, record: JobRecord) -> None:
        from py_api.db import JobRow

        row = JobRow(
            id=record.id,
            instance_id=record.instance_id,
            status=record.status,
            idempotency_key=record.idempotency_key,
            idempotency_hash=record.idempotency_hash,
            params_json=record.params_json,
            created_at=record.created_at,
        )
        async with self._lock, self._db.session() as sess:
            sess.add(row)
            await sess.commit()

    async def append_event(
        self, job_id: str, event: dict[str, Any], record: JobRecord
    ) -> None:
        from py_api.db import JobEventRow, JobRow

        async with self._lock, self._db.session() as sess:
            self._seq[job_id] += 1
            ev = JobEventRow(
                job_id=job_id,
                seq=self._seq[job_id],
                kind=str(event.get("kind", "event")),
                payload_json=event.get("data", {}) or {},
            )
            sess.add(ev)
            # Update the top-level job row.
            job_row = await sess.get(JobRow, job_id)
            if job_row is not None:
                job_row.status = record.status
                job_row.objective = record.objective
                job_row.best_bound = record.best_bound
                job_row.gap = record.gap
                job_row.solve_time_seconds = record.solve_time_seconds
                job_row.error = record.error
                job_row.started_at = record.started_at
                job_row.ended_at = record.ended_at
                sess.add(job_row)
            await sess.commit()

    async def append_log(self, job_id: str, line: str) -> None:
        from py_api.db import JobEventRow

        async with self._lock, self._db.session() as sess:
            self._seq[job_id] += 1
            ev = JobEventRow(
                job_id=job_id,
                seq=self._seq[job_id],
                kind="log",
                payload_json={"line": line},
            )
            sess.add(ev)
            await sess.commit()
