"""FastAPI dependency accessors for app-state singletons.

Singletons (``Database``, ``SolvePool``, ``Metrics``, ``Settings``) live on
``app.state``. Routes obtain them via these ``Depends(...)`` helpers; tests
override them cleanly through ``app.dependency_overrides``.
"""

from __future__ import annotations

from typing import cast

from fastapi import Request

from py_api.config import Settings
from py_api.db import Database
from py_api.jobs import SolvePool
from py_api.metrics import Metrics


def get_settings(request: Request) -> Settings:
    return cast(Settings, request.app.state.settings)


def get_db(request: Request) -> Database:
    return cast(Database, request.app.state.db)


def get_pool(request: Request) -> SolvePool:
    return cast(SolvePool, request.app.state.solve_pool)


def get_metrics(request: Request) -> Metrics:
    return cast(Metrics, request.app.state.metrics)
