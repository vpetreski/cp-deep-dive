"""SQLModel + aiosqlite storage layer.

Three tables:
  - InstanceRow: persisted NSP instances (the raw JSON body plus a summary row)
  - JobRow: solve jobs (status, timings, objective, idempotency metadata)
  - JobEventRow: append-only event log (partial incumbents + log lines) for
    SSE replay and log retrieval

All persistence goes through the async engine; raw SQL is never used.
"""

from __future__ import annotations

import datetime as dt
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Any

from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)
from sqlmodel import JSON, Column, Field, SQLModel


class InstanceRow(SQLModel, table=True):
    __tablename__ = "instances"

    id: str = Field(primary_key=True)
    name: str = ""
    source: str = "custom"
    horizon_days: int
    nurse_count: int
    shift_count: int
    coverage_slot_count: int = 0
    raw_json: dict[str, Any] = Field(sa_column=Column("raw_json", JSON, nullable=False))
    created_at: dt.datetime = Field(default_factory=lambda: dt.datetime.now(dt.UTC))


class JobRow(SQLModel, table=True):
    __tablename__ = "jobs"

    id: str = Field(primary_key=True)
    instance_id: str = Field(index=True, foreign_key="instances.id")
    status: str = "pending"
    objective: float | None = None
    best_bound: float | None = None
    gap: float | None = None
    solve_time_seconds: float = 0.0
    error: str | None = None
    idempotency_key: str | None = Field(default=None, index=True)
    idempotency_hash: str | None = None
    params_json: dict[str, Any] = Field(
        sa_column=Column("params_json", JSON, nullable=False),
        default_factory=dict,
    )
    created_at: dt.datetime = Field(default_factory=lambda: dt.datetime.now(dt.UTC))
    started_at: dt.datetime | None = None
    ended_at: dt.datetime | None = None


class JobEventRow(SQLModel, table=True):
    __tablename__ = "job_events"

    id: int | None = Field(default=None, primary_key=True)
    job_id: str = Field(index=True, foreign_key="jobs.id")
    seq: int = 0
    kind: str  # "incumbent" | "log" | "terminal"
    payload_json: dict[str, Any] = Field(
        sa_column=Column("payload_json", JSON, nullable=False),
        default_factory=dict,
    )
    created_at: dt.datetime = Field(default_factory=lambda: dt.datetime.now(dt.UTC))


class Database:
    """Async engine + session factory wrapper."""

    def __init__(self, url: str) -> None:
        self.url = url
        # check_same_thread=False is SQLite-specific; harmless elsewhere.
        connect_args: dict[str, Any] = {}
        if url.startswith("sqlite"):
            connect_args["check_same_thread"] = False
        self._engine: AsyncEngine = create_async_engine(
            url,
            future=True,
            connect_args=connect_args,
        )
        self._session_maker: async_sessionmaker[AsyncSession] = async_sessionmaker(
            self._engine,
            expire_on_commit=False,
            class_=AsyncSession,
        )

    async def create_all(self) -> None:
        async with self._engine.begin() as conn:
            await conn.run_sync(SQLModel.metadata.create_all)

    async def dispose(self) -> None:
        await self._engine.dispose()

    @asynccontextmanager
    async def session(self) -> AsyncIterator[AsyncSession]:
        async with self._session_maker() as sess:
            yield sess
