"""FastAPI ASGI entrypoint for py-api.

Composes the app:
  - settings, metrics, database, solver pool attached to ``app.state``
  - request-id + CORS middleware
  - error handlers
  - routers (meta, instances, solve)
  - startup seeding + graceful shutdown

Run: ``uv run uvicorn py_api.main:app --reload``
"""

from __future__ import annotations

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from py_api import __version__, errors
from py_api.config import Settings
from py_api.db import Database
from py_api.jobs import DbJobWriter, SolvePool
from py_api.logging_setup import RequestIdMiddleware, setup_logging
from py_api.metrics import Metrics
from py_api.routes import instances as instances_routes
from py_api.routes import meta as meta_routes
from py_api.routes import solve as solve_routes
from py_api.startup import seed_instances

log = logging.getLogger("py_api.main")


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    settings: Settings = app.state.settings
    metrics = Metrics()
    db = Database(settings.db_url)
    await db.create_all()
    pool = SolvePool(
        metrics=metrics,
        max_concurrent=settings.solve_concurrency,
        event_cap=settings.sse_event_cap,
    )
    pool.bind_db_writer(DbJobWriter(db))
    app.state.metrics = metrics
    app.state.db = db
    app.state.solve_pool = pool

    await seed_instances(db, settings)
    log.info(
        "py-api started version=%s pool=%d db=%s",
        __version__,
        settings.solve_concurrency,
        settings.db_url,
    )

    try:
        yield
    finally:
        log.info("py-api shutdown — cancelling in-flight solves")
        await pool.shutdown()
        await db.dispose()


def create_app(settings: Settings | None = None) -> FastAPI:
    """Factory — useful for tests that want a fresh isolated app."""
    settings = settings or Settings.from_env()
    setup_logging()

    app = FastAPI(
        title="cp-deep-dive NSP API",
        version=__version__,
        description=(
            "FastAPI backend for the Nurse Scheduling Problem. "
            "Matches apps/shared/openapi.yaml v1.0."
        ),
        lifespan=lifespan,
    )
    app.state.settings = settings

    # Middleware — order matters: CORS outermost, then request-id.
    app.add_middleware(
        CORSMiddleware,
        allow_origins=list(settings.cors_origins),
        allow_methods=["GET", "POST", "DELETE", "OPTIONS"],
        allow_headers=["Content-Type", "Idempotency-Key", "X-Request-Id"],
        max_age=600,
    )
    app.add_middleware(RequestIdMiddleware)

    errors.install(app)

    app.include_router(meta_routes.router)
    app.include_router(instances_routes.router)
    app.include_router(solve_routes.router)

    return app


app = create_app()
