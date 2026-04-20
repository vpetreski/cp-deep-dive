"""Domain → wire-dict conversion.

The solver layer speaks in the immutable ``nsp_core`` dataclasses
(``Instance``, ``Schedule``, ``SolveResult``, ...). Clients speak camelCase
JSON. This module is the only place that translates between the two, so the
wire shape stays consistent across all routes.
"""

from __future__ import annotations

import datetime as dt
from typing import Any

from nsp_core.domain import (
    Assignment,
    CoverageRequirement,
    Instance,
    Nurse,
    Preference,
    Schedule,
    Shift,
    SolveResult,
    Violation,
)


def _hhmm(total_minutes: int) -> str:
    hh = (total_minutes // 60) % 24
    mm = total_minutes % 60
    return f"{hh:02d}:{mm:02d}"


def shift_to_dict(s: Shift) -> dict[str, Any]:
    # End-minute representation for display; wrap at midnight.
    out: dict[str, Any] = {
        "id": s.id,
        "label": s.label,
        "startMinutes": s.start_minutes,
        "durationMinutes": s.duration_minutes,
        "isNight": s.is_night,
        "start": _hhmm(s.start_minutes),
        "end": _hhmm(s.start_minutes + s.duration_minutes),
    }
    if s.skill is not None:
        out["skill"] = s.skill
    return out


def nurse_to_dict(n: Nurse) -> dict[str, Any]:
    out: dict[str, Any] = {
        "id": n.id,
        "name": n.name,
        "skills": sorted(n.skills),
        "contractHoursPerWeek": n.contract_hours_per_week,
        "unavailable": sorted(n.unavailable),
    }
    if n.max_shifts_per_week is not None:
        out["maxShiftsPerWeek"] = n.max_shifts_per_week
    if n.min_shifts_per_week is not None:
        out["minShiftsPerWeek"] = n.min_shifts_per_week
    if n.max_consecutive_working_days is not None:
        out["maxConsecutiveWorkingDays"] = n.max_consecutive_working_days
    return out


def coverage_to_dict(c: CoverageRequirement) -> dict[str, Any]:
    out: dict[str, Any] = {
        "day": c.day,
        "shiftId": c.shift_id,
        "min": c.min,
        "max": c.max,
    }
    if c.required_skills:
        out["requiredSkills"] = sorted(c.required_skills)
    return out


def preference_to_dict(p: Preference) -> dict[str, Any]:
    out: dict[str, Any] = {
        "nurseId": p.nurse_id,
        "kind": p.kind,
        "weight": p.weight,
    }
    if p.day is not None:
        out["day"] = p.day
    # shiftId is intentionally included even when None (null on the wire
    # means "day off").
    out["shiftId"] = p.shift_id
    return out


def instance_to_dict(inst: Instance) -> dict[str, Any]:
    out: dict[str, Any] = {
        "id": inst.id,
        "name": inst.name,
        "source": inst.source,
        "horizonDays": inst.horizon_days,
        "shifts": [shift_to_dict(s) for s in inst.shifts],
        "nurses": [nurse_to_dict(n) for n in inst.nurses],
        "coverage": [coverage_to_dict(c) for c in inst.coverage],
        "preferences": [preference_to_dict(p) for p in inst.preferences],
        "forbiddenTransitions": [list(pair) for pair in inst.forbidden_transitions],
        "minRestHours": inst.min_rest_hours,
        "maxConsecutiveWorkingDays": inst.max_consecutive_working_days,
        "maxConsecutiveNights": inst.max_consecutive_nights,
        "metadata": {**inst.metadata, "contractTolerance": inst.contract_tolerance_hours},
    }
    return out


def instance_summary(inst: Instance, created_at: dt.datetime | None = None) -> dict[str, Any]:
    out: dict[str, Any] = {
        "id": inst.id,
        "name": inst.name,
        "source": inst.source,
        "horizonDays": inst.horizon_days,
        "nurseCount": len(inst.nurses),
        "shiftCount": len(inst.shifts),
        "coverageSlotCount": len(inst.coverage),
    }
    if created_at is not None:
        out["createdAt"] = created_at.astimezone(dt.UTC).isoformat(
            timespec="seconds"
        ).replace("+00:00", "Z")
    return out


def assignment_to_dict(a: Assignment) -> dict[str, Any]:
    return {
        "nurseId": a.nurse_id,
        "day": a.day,
        "shiftId": a.shift_id,
    }


def violation_to_dict(v: Violation) -> dict[str, Any]:
    out: dict[str, Any] = {
        "code": v.code,
        "message": v.message,
    }
    if v.severity:
        out["severity"] = v.severity
    if v.nurse_id is not None:
        out["nurseId"] = v.nurse_id
    if v.day is not None:
        out["day"] = v.day
    if v.penalty is not None:
        out["penalty"] = v.penalty
    return out


def schedule_to_dict(sched: Schedule) -> dict[str, Any]:
    out: dict[str, Any] = {
        "instanceId": sched.instance_id,
        "assignments": [assignment_to_dict(a) for a in sched.assignments],
    }
    if sched.job_id is not None:
        out["jobId"] = sched.job_id
    if sched.generated_at is not None:
        out["generatedAt"] = sched.generated_at
    if sched.violations:
        out["violations"] = [violation_to_dict(v) for v in sched.violations]
    return out


def _iso(d: dt.datetime | None) -> str | None:
    if d is None:
        return None
    if d.tzinfo is None:
        d = d.replace(tzinfo=dt.UTC)
    return d.astimezone(dt.UTC).isoformat(timespec="seconds").replace("+00:00", "Z")


def solve_response_dict(
    *,
    job_id: str,
    instance_id: str | None,
    status: str,
    result: SolveResult | None,
    created_at: dt.datetime | None = None,
    started_at: dt.datetime | None = None,
    ended_at: dt.datetime | None = None,
    error: str | None = None,
) -> dict[str, Any]:
    """Build the canonical ``SolveResponse`` wire dict from a ``SolveResult``."""
    body: dict[str, Any] = {
        "jobId": job_id,
        "status": status,
    }
    if instance_id:
        body["instanceId"] = instance_id
    if result is not None:
        if result.schedule is not None:
            body["schedule"] = schedule_to_dict(result.schedule)
        if result.violations:
            body["violations"] = [violation_to_dict(v) for v in result.violations]
        if result.objective is not None:
            body["objective"] = result.objective
        if result.best_bound is not None:
            body["bestBound"] = result.best_bound
        if result.gap is not None:
            body["gap"] = result.gap
        body["solveTimeSeconds"] = result.solve_time_seconds
        if result.error and not error:
            error = result.error
    if created_at is not None:
        body["createdAt"] = _iso(created_at)
    if started_at is not None:
        body["startedAt"] = _iso(started_at)
    if ended_at is not None:
        body["endedAt"] = _iso(ended_at)
    if error:
        body["error"] = error
    return body
