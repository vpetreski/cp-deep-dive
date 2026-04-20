"""Pydantic v2 models for request / response bodies.

The authoritative wire shape lives in ``apps/shared/openapi.yaml`` and the JSON
Schemas under ``apps/shared/schemas/``. The models below mirror those; the
``model_config`` uses ``populate_by_name=True`` so camelCase wire keys map onto
Python ``snake_case`` attributes via aliases.

For the NSP instance / schedule payloads we intentionally keep the validation
shallow — ``nsp_core.loader.parse_instance`` does the deep JSON-Schema check
and semantic normalisation. This module accepts the raw dict as
``dict[str, Any]`` at the boundary where that makes sense (POST /instances,
POST /solve) and re-serialises the canonical form for responses.
"""

from __future__ import annotations

import datetime as dt
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field

# --------------------------------------------------------------------------- #
# Common config — camelCase on the wire, snake_case in Python.
# --------------------------------------------------------------------------- #


class _WireModel(BaseModel):
    model_config = ConfigDict(
        populate_by_name=True,
        extra="ignore",
        ser_json_bytes="utf8",
    )


# --------------------------------------------------------------------------- #
# /health, /version
# --------------------------------------------------------------------------- #


class HealthResponse(_WireModel):
    status: Literal["ok", "degraded"] = "ok"
    service: str
    checks: dict[str, Any] | None = None


class VersionResponse(_WireModel):
    version: str
    ortools: str
    runtime: str | None = None
    service: str | None = None


# --------------------------------------------------------------------------- #
# /instances
# --------------------------------------------------------------------------- #


class InstanceSummary(_WireModel):
    id: str
    name: str | None = None
    source: str | None = None
    horizon_days: int = Field(alias="horizonDays")
    nurse_count: int = Field(alias="nurseCount")
    shift_count: int = Field(alias="shiftCount")
    coverage_slot_count: int | None = Field(default=None, alias="coverageSlotCount")
    created_at: dt.datetime | None = Field(default=None, alias="createdAt")


class InstanceList(_WireModel):
    items: list[InstanceSummary]
    next_cursor: str | None = Field(default=None, alias="nextCursor")


# --------------------------------------------------------------------------- #
# /solve
# --------------------------------------------------------------------------- #


class SolveAccepted(_WireModel):
    job_id: str = Field(alias="jobId")
    status: Literal["pending", "queued", "running"] = "pending"
    instance_id: str | None = Field(default=None, alias="instanceId")
    created_at: dt.datetime | None = Field(default=None, alias="createdAt")


class ViolationDto(_WireModel):
    code: str
    message: str
    severity: Literal["hard", "soft"] | None = None
    nurse_id: str | None = Field(default=None, alias="nurseId")
    day: int | None = None
    penalty: float | None = None


class AssignmentDto(_WireModel):
    nurse_id: str = Field(alias="nurseId")
    day: int
    shift_id: str | None = Field(default=None, alias="shiftId")


class ScheduleDto(_WireModel):
    instance_id: str = Field(alias="instanceId")
    job_id: str | None = Field(default=None, alias="jobId")
    generated_at: str | None = Field(default=None, alias="generatedAt")
    assignments: list[AssignmentDto] = []
    violations: list[ViolationDto] = []


SolveStatusLiteral = Literal[
    "pending",
    "queued",
    "running",
    "feasible",
    "optimal",
    "infeasible",
    "unknown",
    "timeout",
    "cancelled",
    "modelInvalid",
    "error",
]


class SolveResponse(_WireModel):
    job_id: str = Field(alias="jobId")
    instance_id: str | None = Field(default=None, alias="instanceId")
    status: SolveStatusLiteral
    schedule: ScheduleDto | None = None
    violations: list[ViolationDto] = []
    objective: float | None = None
    best_bound: float | None = Field(default=None, alias="bestBound")
    gap: float | None = None
    solve_time_seconds: float | None = Field(default=None, alias="solveTimeSeconds")
    created_at: dt.datetime | None = Field(default=None, alias="createdAt")
    started_at: dt.datetime | None = Field(default=None, alias="startedAt")
    ended_at: dt.datetime | None = Field(default=None, alias="endedAt")
    error: str | None = None
