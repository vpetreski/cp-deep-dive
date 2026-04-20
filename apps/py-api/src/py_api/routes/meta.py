"""Meta endpoints: /health, /version, /openapi.yaml, /metrics."""

from __future__ import annotations

import platform
from importlib.metadata import PackageNotFoundError
from importlib.metadata import version as pkg_version

from fastapi import APIRouter, Depends
from fastapi.responses import PlainTextResponse, Response
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest
from sqlalchemy import text as sql_text

from py_api import __version__
from py_api.config import Settings
from py_api.db import Database
from py_api.dependencies import get_db, get_metrics, get_settings
from py_api.metrics import Metrics
from py_api.schemas import HealthResponse, VersionResponse

router = APIRouter(tags=["meta"])


def _ortools_version() -> str:
    try:
        return pkg_version("ortools")
    except PackageNotFoundError:  # pragma: no cover
        return "unknown"


@router.get("/health", response_model=HealthResponse)
async def health(
    db: Database = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> HealthResponse:
    checks: dict[str, str] = {}
    status_val: str = "ok"
    try:
        async with db.session() as sess:
            await sess.execute(sql_text("SELECT 1"))
        checks["db"] = "ok"
    except Exception as exc:  # pragma: no cover
        checks["db"] = f"error: {exc}"
        status_val = "degraded"
    return HealthResponse(
        status="ok" if status_val == "ok" else "degraded",
        service=settings.service_name,
        checks=checks,
    )


@router.get("/version", response_model=VersionResponse)
async def version(settings: Settings = Depends(get_settings)) -> VersionResponse:
    runtime = (
        f"python {platform.python_version()} ({platform.python_implementation().lower()})"
    )
    return VersionResponse(
        version=__version__,
        ortools=_ortools_version(),
        runtime=runtime,
        service=settings.service_name,
    )


@router.get("/openapi.yaml", include_in_schema=False)
async def openapi_yaml(settings: Settings = Depends(get_settings)) -> Response:
    path = settings.openapi_path
    data = path.read_bytes()
    return Response(content=data, media_type="application/yaml")


@router.get("/metrics", include_in_schema=False)
async def metrics(m: Metrics = Depends(get_metrics)) -> PlainTextResponse:
    return PlainTextResponse(
        content=generate_latest(m.registry).decode("utf-8"),
        media_type=CONTENT_TYPE_LATEST,
    )
