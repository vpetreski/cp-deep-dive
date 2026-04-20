"""Load NSP instances from JSON (file or dict), validate against the shared
JSON Schema, and normalise to the frozen-dataclass domain model.

The wire schema lives at ``apps/shared/schemas/nsp-instance.schema.json``. This
module imports it lazily so the rest of the package can be used in contexts
(tests, py-api requests) where the schema file is already loaded.
"""

from __future__ import annotations

import json
import pathlib
import uuid
from typing import Any, cast

from jsonschema import Draft202012Validator

from nsp_core.domain import (
    CoverageRequirement,
    Instance,
    Nurse,
    Preference,
    Shift,
)

def _discover_schema_path() -> pathlib.Path:
    """Locate apps/shared/schemas/nsp-instance.schema.json relative to this file.

    Works in the uv workspace (editable install, file at
    apps/py-cp-sat/nsp-core/src/nsp_core/loader.py) and in a Docker layout where
    the repo root has been copied intact. The search walks up from this file and
    picks the first ancestor directory containing ``apps/shared/schemas``.
    """
    here = pathlib.Path(__file__).resolve()
    for ancestor in here.parents:
        candidate = ancestor / "apps" / "shared" / "schemas" / "nsp-instance.schema.json"
        if candidate.is_file():
            return candidate
    raise FileNotFoundError(
        "Could not locate apps/shared/schemas/nsp-instance.schema.json from "
        f"{here}. Ensure the repo layout is intact or set NSP_INSTANCE_SCHEMA_PATH."
    )


_SHARED_SCHEMA_PATH = _discover_schema_path()


class InstanceValidationError(ValueError):
    """Raised when a payload fails JSON Schema validation.

    ``errors`` is a list of ``(field_path, message)`` tuples describing each
    violation. The ``__str__`` representation is a multi-line summary suitable
    for logging and for feeding into the FastAPI error envelope.
    """

    def __init__(self, errors: list[tuple[str, str]]) -> None:
        self.errors = errors
        head = f"{len(errors)} validation error(s):"
        body = "\n".join(f"  {path}: {msg}" for path, msg in errors)
        super().__init__(f"{head}\n{body}" if errors else head)


def _load_schema() -> dict[str, Any]:
    return cast(dict[str, Any], json.loads(_SHARED_SCHEMA_PATH.read_text()))


def _validate(raw: dict[str, Any]) -> None:
    validator = Draft202012Validator(_load_schema())
    errors = sorted(validator.iter_errors(raw), key=lambda e: list(e.absolute_path))
    if not errors:
        return
    reports: list[tuple[str, str]] = []
    for err in errors:
        path = "/".join(str(p) for p in err.absolute_path) or "<root>"
        reports.append((path, err.message))
    raise InstanceValidationError(reports)


def _minutes_from_hhmm(hhmm: str) -> int:
    """Parse "HH:MM" → minutes-of-day."""
    hh, mm = hhmm.split(":")
    return int(hh) * 60 + int(mm)


def _shift_from_raw(raw: dict[str, Any]) -> Shift:
    """Accept either toy-style start/end strings or wire-style startMinutes+
    durationMinutes. Either form ends up as canonical minute offsets on the
    Shift dataclass.
    """
    shift_id = str(raw["id"])
    label = str(raw.get("label", shift_id))

    start_minutes = raw.get("startMinutes")
    duration_minutes = raw.get("durationMinutes")
    start_str = raw.get("start")
    end_str = raw.get("end")

    if start_minutes is not None and duration_minutes is not None:
        sm = int(start_minutes)
        dm = int(duration_minutes)
    elif isinstance(start_str, str) and isinstance(end_str, str):
        sm = _minutes_from_hhmm(start_str)
        em = _minutes_from_hhmm(end_str)
        # Night shifts that cross midnight: end <= start -> wrap
        dm = em - sm if em > sm else (24 * 60 - sm) + em
    else:
        raise InstanceValidationError(
            [("shifts", f"shift {shift_id!r} needs either startMinutes+durationMinutes or start+end")]
        )

    is_night_raw = raw.get("isNight")
    if isinstance(is_night_raw, bool):
        is_night = is_night_raw
    else:
        # Infer: shift starts at or after 20:00 or id/label contains 'night'
        is_night = (
            sm >= 20 * 60
            or sm + dm > 24 * 60
            or "night" in shift_id.lower()
            or "night" in label.lower()
            or shift_id.upper() == "N"
        )

    skill = raw.get("skill")
    return Shift(
        id=shift_id,
        label=label,
        start_minutes=sm,
        duration_minutes=dm,
        is_night=is_night,
        skill=str(skill) if skill is not None else None,
    )


def _nurse_from_raw(raw: dict[str, Any]) -> Nurse:
    return Nurse(
        id=str(raw["id"]),
        name=str(raw.get("name", "")),
        skills=frozenset(str(s) for s in raw.get("skills", [])),
        contract_hours_per_week=int(raw.get("contractHoursPerWeek", 40)),
        max_shifts_per_week=(
            int(raw["maxShiftsPerWeek"]) if "maxShiftsPerWeek" in raw else None
        ),
        min_shifts_per_week=(
            int(raw["minShiftsPerWeek"]) if "minShiftsPerWeek" in raw else None
        ),
        max_consecutive_working_days=(
            int(raw["maxConsecutiveWorkingDays"]) if "maxConsecutiveWorkingDays" in raw else None
        ),
        unavailable=frozenset(int(d) for d in raw.get("unavailable", [])),
    )


def _coverage_from_raw(raw: dict[str, Any]) -> CoverageRequirement:
    required = raw.get("required")
    min_val_raw = raw.get("min")
    max_val_raw = raw.get("max")
    if min_val_raw is None and max_val_raw is None and required is not None:
        min_val = int(required)
        max_val = int(required)
    else:
        min_val = int(min_val_raw) if min_val_raw is not None else 0
        max_val = int(max_val_raw) if max_val_raw is not None else 999
    return CoverageRequirement(
        day=int(raw["day"]),
        shift_id=str(raw["shiftId"]),
        min=min_val,
        max=max_val,
        required_skills=frozenset(str(s) for s in raw.get("requiredSkills", [])),
    )


def _preference_from_raw(raw: dict[str, Any]) -> Preference:
    shift_id_raw = raw.get("shiftId")
    return Preference(
        nurse_id=str(raw["nurseId"]),
        kind=str(raw["kind"]),
        weight=int(raw["weight"]),
        day=int(raw["day"]) if "day" in raw else None,
        shift_id=str(shift_id_raw) if shift_id_raw is not None else None,
    )


def parse_instance(raw: dict[str, Any], *, assign_id: bool = True) -> Instance:
    """Validate ``raw`` against the shared schema and return an Instance.

    If ``assign_id`` is True and the payload has no ``id``, a random UUID4 is
    generated. Other computed defaults mirror the schema's ``default`` blocks:

    - ``minRestHours`` defaults to 11
    - ``maxConsecutiveWorkingDays`` defaults to 6
    - ``maxConsecutiveNights`` defaults to 3
    - ``metadata.contractTolerance`` defaults to 4 (hours) per spec FR HC-8

    Raises :class:`InstanceValidationError` on schema failure.
    """
    _validate(raw)

    shifts = tuple(_shift_from_raw(s) for s in raw["shifts"])
    nurses = tuple(_nurse_from_raw(n) for n in raw["nurses"])
    coverage = tuple(_coverage_from_raw(c) for c in raw.get("coverage", []))
    preferences = tuple(_preference_from_raw(p) for p in raw.get("preferences", []))
    forbidden = tuple(
        (str(pair[0]), str(pair[1])) for pair in raw.get("forbiddenTransitions", [])
    )

    metadata = dict(raw.get("metadata", {}) or {})
    # Pull contract tolerance out into a typed field; keep the full metadata blob too.
    contract_tol = int(metadata.get("contractTolerance", 4))

    instance_id = raw.get("id")
    if not instance_id and assign_id:
        instance_id = f"instance-{uuid.uuid4().hex[:8]}"

    return Instance(
        id=str(instance_id) if instance_id else "",
        name=str(raw.get("name", "")),
        source=str(raw.get("source", "custom")),
        horizon_days=int(raw["horizonDays"]),
        shifts=shifts,
        nurses=nurses,
        coverage=coverage,
        preferences=preferences,
        forbidden_transitions=forbidden,
        min_rest_hours=int(raw.get("minRestHours", 11)),
        max_consecutive_working_days=int(raw.get("maxConsecutiveWorkingDays", 6)),
        max_consecutive_nights=int(raw.get("maxConsecutiveNights", 3)),
        contract_tolerance_hours=contract_tol,
        metadata=metadata,
    )


def load_instance(path: str | pathlib.Path) -> Instance:
    """Convenience wrapper: read a JSON file and parse it."""
    text = pathlib.Path(path).read_text()
    raw = cast(dict[str, Any], json.loads(text))
    return parse_instance(raw)
