"""Runtime configuration for py-api.

All tunables are driven by environment variables so the same image can run in
dev, CI, and prod. Defaults are safe for local development.
"""

from __future__ import annotations

import os
import pathlib
from dataclasses import dataclass


def _env_int(name: str, default: int) -> int:
    raw = os.environ.get(name)
    if raw is None or raw == "":
        return default
    try:
        return int(raw)
    except ValueError:
        return default


def _env_bool(name: str, default: bool) -> bool:
    raw = os.environ.get(name, "").strip().lower()
    if not raw:
        return default
    return raw in ("1", "true", "yes", "on")


def _repo_root() -> pathlib.Path:
    """Walk up from this file until we find ``apps/`` — the repo root."""
    here = pathlib.Path(__file__).resolve()
    for ancestor in here.parents:
        if (ancestor / "apps").is_dir() and (ancestor / "apps" / "shared").is_dir():
            return ancestor
    # Fallback: the uv-editable install path (apps/py-api/src/py_api/config.py).
    return here.parents[3]


def _default_db_url() -> str:
    # Local SQLite file; override in prod via NSP_API_DB_URL.
    return "sqlite+aiosqlite:///./nsp-api.sqlite"


@dataclass(frozen=True)
class Settings:
    """Process-wide configuration snapshot."""

    service_name: str = "py-api"
    db_url: str = ""
    cors_origins: tuple[str, ...] = ()
    max_body_bytes: int = 1 * 1024 * 1024  # 1 MiB (spec)
    solve_concurrency: int = 0
    sse_event_cap: int = 10_000
    default_solve_objective: str = "weighted"
    repo_root: pathlib.Path = pathlib.Path(".")

    @classmethod
    def from_env(cls) -> Settings:
        origins_raw = os.environ.get(
            "CORS_ORIGINS",
            "http://localhost:5173,http://localhost:4173,http://localhost:3000",
        )
        origins = tuple(o.strip() for o in origins_raw.split(",") if o.strip())

        cpu = os.cpu_count() or 2
        default_pool = min(4, cpu)

        return cls(
            service_name=os.environ.get("NSP_API_SERVICE", "py-api"),
            db_url=os.environ.get("NSP_API_DB_URL", _default_db_url()),
            cors_origins=origins,
            max_body_bytes=_env_int("NSP_API_MAX_BODY_BYTES", 1 * 1024 * 1024),
            solve_concurrency=_env_int("NSP_API_SOLVE_CONCURRENCY", default_pool),
            sse_event_cap=_env_int("NSP_API_SSE_EVENT_CAP", 10_000),
            default_solve_objective=os.environ.get("NSP_API_DEFAULT_OBJECTIVE", "weighted"),
            repo_root=_repo_root(),
        )

    @property
    def openapi_path(self) -> pathlib.Path:
        return self.repo_root / "apps" / "shared" / "openapi.yaml"

    @property
    def seed_dir(self) -> pathlib.Path:
        return self.repo_root / "data" / "nsp"
