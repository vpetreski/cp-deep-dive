"""FastAPI entrypoint for the NSP backend skeleton."""

from __future__ import annotations

from fastapi import FastAPI
from fastapi.responses import JSONResponse
from pydantic import BaseModel

from py_api import __version__


def _ortools_version() -> str:
    """Best-effort probe of the installed OR-Tools version.

    The `ortools` wheels ship a version via `ortools.init.python.init.OrToolsVersion`
    starting in 9.x; fall back to `importlib.metadata`.
    """
    try:
        from importlib.metadata import version as _pkg_version

        return _pkg_version("ortools")
    except Exception:  # pragma: no cover - defensive fallback
        return "unknown"


app = FastAPI(
    title="cp-deep-dive py-api",
    version=__version__,
    description="FastAPI backend for the Nurse Scheduling Problem (Phase 7 / Chapter 15).",
)


# -- schemas -----------------------------------------------------------------


class HealthResponse(BaseModel):
    """GET /health body."""

    model_config = {"json_schema_extra": {"examples": [{"status": "ok", "service": "py-api"}]}}

    status: str
    service: str


class VersionResponse(BaseModel):
    """GET /version body."""

    version: str
    ortools: str


class NotImplementedResponse(BaseModel):
    """Shape of 501 stubs until Phase 7 ships."""

    todo: str


# -- endpoints ---------------------------------------------------------------


@app.get("/health", response_model=HealthResponse, tags=["meta"])
def health() -> HealthResponse:
    """Liveness probe."""
    return HealthResponse(status="ok", service="py-api")


@app.get("/version", response_model=VersionResponse, tags=["meta"])
def version() -> VersionResponse:
    """Report the app version + the CP-SAT engine version."""
    return VersionResponse(version=__version__, ortools=_ortools_version())


@app.post(
    "/solve",
    status_code=501,
    response_model=NotImplementedResponse,
    tags=["nsp"],
    summary="Submit an NSP instance for solving (not yet implemented).",
)
def solve() -> JSONResponse:
    """Stub — wiring lands in Phase 7 / Chapter 15."""
    return JSONResponse(
        status_code=501,
        content={"todo": "Phase 7 Chapter 15"},
    )


@app.get(
    "/solution/{solution_id}",
    status_code=501,
    response_model=NotImplementedResponse,
    tags=["nsp"],
    summary="Fetch a solved/in-progress schedule by id (not yet implemented).",
)
def solution(solution_id: str) -> JSONResponse:
    """Stub — wiring lands in Phase 7 / Chapter 15."""
    del solution_id  # unused until implemented
    return JSONResponse(
        status_code=501,
        content={"todo": "Phase 7 Chapter 15"},
    )
