"""Shared test fixtures.

Each test gets a throwaway FastAPI app bound to an in-memory SQLite database
and a fresh ``SolvePool``. The ``client`` fixture is an ``httpx.AsyncClient``
driving the app via ``ASGITransport``.
"""

from __future__ import annotations

import os
import uuid
from collections.abc import AsyncIterator, Iterator

import pytest
import pytest_asyncio
from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient

from py_api.config import Settings
from py_api.main import create_app


@pytest.fixture(scope="session")
def repo_root_path() -> str:
    # tests run from apps/py-api/, repo root is three up.
    return os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))


@pytest.fixture()
def tmp_settings(tmp_path, repo_root_path: str) -> Settings:
    # unique filename per test ensures parallel-safe isolation.
    db_file = tmp_path / f"test-{uuid.uuid4().hex[:8]}.sqlite"
    # NOTE: pass the repo_root via env so Settings.from_env picks it up.
    return Settings(
        service_name="py-api",
        db_url=f"sqlite+aiosqlite:///{db_file}",
        cors_origins=("http://localhost:5173",),
        max_body_bytes=1024 * 1024,
        solve_concurrency=2,
        sse_event_cap=200,
        default_solve_objective="hard",
        repo_root=__import__("pathlib").Path(repo_root_path),
    )


@pytest_asyncio.fixture()
async def app(tmp_settings: Settings) -> AsyncIterator[FastAPI]:
    application = create_app(tmp_settings)
    # Manually run lifespan.
    async with application.router.lifespan_context(application):
        yield application


@pytest_asyncio.fixture()
async def client(app: FastAPI) -> AsyncIterator[AsyncClient]:
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac


@pytest.fixture()
def toy_01_body(repo_root_path: str) -> dict:
    import pathlib

    path = pathlib.Path(repo_root_path) / "data" / "nsp" / "toy-01.json"
    return __import__("json").loads(path.read_text())  # reuse json import


@pytest.fixture()
def empty_run() -> Iterator[None]:
    """No-op placeholder — kept for API stability in tests."""
    yield
