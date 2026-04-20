#!/usr/bin/env -S uv run python
"""Validate a produced schedule against an NSP instance (hard constraints only).

Usage:
    uv run python tools/validate-schedule.py \\
        --instance data/nsp/toy-01.json \\
        --schedule path/to/schedule.json

Exits with status 0 if every hard constraint is satisfied, 1 otherwise.

Hard constraints checked:
    H1  Coverage (sum of assigned nurses per (day, shift) >= demand.min, <= demand.max)
    H2  At-most-one-shift per nurse per day
    H3  Forbidden transitions (instance.forbiddenTransitions)
    H4  Max consecutive working days (instance.maxConsecutiveWorkingDays)
    H5  Fixed days off (instance.fixedOff)
    H6  Valid references (nurseId / shiftId known to the instance)

Soft constraints (preferences) are *not* scored — use the Chapter 12 solver for that.
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

try:
    import jsonschema
except ImportError:  # pragma: no cover
    print(
        "error: `jsonschema` is not installed. "
        "Run `uv sync --directory tools` or `uv run python tools/validate-schedule.py`.",
        file=sys.stderr,
    )
    sys.exit(2)


# ---------------------------------------------------------------------------
# Report primitives
# ---------------------------------------------------------------------------


@dataclass
class Check:
    """One line of the validation report."""

    passed: bool
    message: str

    def render(self) -> str:
        tick = "[OK]" if self.passed else "[FAIL]"
        return f"{tick} {self.message}"


def _add(checks: list[Check], passed: bool, message: str) -> None:
    checks.append(Check(passed=passed, message=message))


# ---------------------------------------------------------------------------
# Validation
# ---------------------------------------------------------------------------


def _load_json(path: Path) -> dict[str, Any]:
    with path.open() as f:
        return json.load(f)  # type: ignore[no-any-return]


def _validate_instance_schema(instance: dict[str, Any], schema_path: Path) -> Check:
    schema = _load_json(schema_path)
    try:
        jsonschema.validate(instance, schema)
    except jsonschema.ValidationError as exc:
        return Check(
            passed=False,
            message=f"Instance schema: {exc.message} (path: {list(exc.absolute_path)})",
        )
    return Check(passed=True, message="Instance conforms to JSON Schema")


def _check_references(
    instance: dict[str, Any],
    schedule: dict[str, Any],
) -> list[Check]:
    nurse_ids = {n["id"] for n in instance["nurses"]}
    shift_ids = {s["id"] for s in instance["shifts"]}
    horizon = instance["horizonDays"]

    checks: list[Check] = []
    bad = 0
    for a in schedule.get("assignments", []):
        if a["nurseId"] not in nurse_ids:
            bad += 1
        if a["shiftId"] not in shift_ids:
            bad += 1
        if not (0 <= a["day"] < horizon):
            bad += 1
    _add(
        checks,
        passed=(bad == 0),
        message=f"References ({bad} unknown nurse/shift/day reference{'s' if bad != 1 else ''})",
    )
    return checks


def _check_at_most_one_per_day(schedule: dict[str, Any]) -> Check:
    seen: dict[tuple[str, int], int] = {}
    for a in schedule.get("assignments", []):
        key = (a["nurseId"], a["day"])
        seen[key] = seen.get(key, 0) + 1
    violators = [(n, d) for (n, d), c in seen.items() if c > 1]
    if violators:
        msg = (
            f"H2 At-most-one-shift/day: {len(violators)} violation(s) "
            f"(e.g. nurse {violators[0][0]} day {violators[0][1]})"
        )
        return Check(passed=False, message=msg)
    return Check(passed=True, message="H2 At-most-one-shift/day OK")


def _check_coverage(instance: dict[str, Any], schedule: dict[str, Any]) -> Check:
    covered: dict[tuple[int, str], int] = {}
    for a in schedule.get("assignments", []):
        key = (a["day"], a["shiftId"])
        covered[key] = covered.get(key, 0) + 1

    errors: list[str] = []
    for demand in instance["demand"]:
        key = (demand["day"], demand["shiftId"])
        n = covered.get(key, 0)
        if n < demand["min"]:
            errors.append(
                f"day {demand['day']} shift {demand['shiftId']}: {n} < min {demand['min']}"
            )
        if n > demand["max"]:
            errors.append(
                f"day {demand['day']} shift {demand['shiftId']}: {n} > max {demand['max']}"
            )
    if errors:
        return Check(
            passed=False,
            message=f"H1 Coverage: {len(errors)} violation(s); first: {errors[0]}",
        )
    return Check(passed=True, message="H1 Coverage OK")


def _check_forbidden_transitions(
    instance: dict[str, Any], schedule: dict[str, Any]
) -> Check:
    forbidden = {tuple(pair) for pair in instance["forbiddenTransitions"]}
    by_nurse_day: dict[tuple[str, int], str] = {}
    for a in schedule.get("assignments", []):
        by_nurse_day[(a["nurseId"], a["day"])] = a["shiftId"]
    horizon = instance["horizonDays"]
    violations: list[str] = []
    for n in instance["nurses"]:
        nurse_id = n["id"]
        for d in range(horizon - 1):
            s1 = by_nurse_day.get((nurse_id, d))
            s2 = by_nurse_day.get((nurse_id, d + 1))
            if s1 is not None and s2 is not None and (s1, s2) in forbidden:
                violations.append(f"nurse {nurse_id} {s1}(d{d}) -> {s2}(d{d + 1})")
    if violations:
        return Check(
            passed=False,
            message=f"H3 Forbidden transitions: {len(violations)} violation(s); first: {violations[0]}",
        )
    return Check(passed=True, message="H3 Forbidden transitions OK")


def _check_max_consecutive(instance: dict[str, Any], schedule: dict[str, Any]) -> Check:
    max_consec = instance["maxConsecutiveWorkingDays"]
    worked: dict[tuple[str, int], bool] = {}
    for a in schedule.get("assignments", []):
        worked[(a["nurseId"], a["day"])] = True
    horizon = instance["horizonDays"]
    violations: list[str] = []
    for n in instance["nurses"]:
        nurse_id = n["id"]
        streak = 0
        for d in range(horizon):
            if worked.get((nurse_id, d), False):
                streak += 1
                if streak > max_consec:
                    violations.append(
                        f"nurse {nurse_id} has {streak} consecutive working days "
                        f"through day {d} (max: {max_consec})"
                    )
                    break
            else:
                streak = 0
    if violations:
        return Check(
            passed=False,
            message=f"H4 Max consecutive working days: {len(violations)} violation(s); first: {violations[0]}",
        )
    return Check(passed=True, message=f"H4 Max consecutive working days (<= {max_consec}) OK")


def _check_fixed_off(instance: dict[str, Any], schedule: dict[str, Any]) -> Check:
    fixed_off = {(fo["nurseId"], fo["day"]) for fo in instance.get("fixedOff", [])}
    if not fixed_off:
        return Check(passed=True, message="H5 Fixed days off (none specified)")
    violations = [
        (a["nurseId"], a["day"])
        for a in schedule.get("assignments", [])
        if (a["nurseId"], a["day"]) in fixed_off
    ]
    if violations:
        return Check(
            passed=False,
            message=f"H5 Fixed days off: {len(violations)} violation(s); first: nurse {violations[0][0]} day {violations[0][1]}",
        )
    return Check(passed=True, message="H5 Fixed days off OK")


def _check_rest(instance: dict[str, Any], schedule: dict[str, Any]) -> Check:
    """Minimum-rest check via shift end/start times.

    This is an approximation: uses the declared `start` / `end` of each shift, assumes
    a shift with `end < start` wraps to the next morning. Reports any consecutive-day
    pair of assignments whose inter-shift gap is below `instance.minRestHours`.
    """
    min_rest = instance["minRestHours"]
    shift_by_id = {s["id"]: s for s in instance["shifts"]}
    by_nurse_day: dict[tuple[str, int], str] = {}
    for a in schedule.get("assignments", []):
        by_nurse_day[(a["nurseId"], a["day"])] = a["shiftId"]
    horizon = instance["horizonDays"]

    def minutes(t: str) -> int:
        hh, mm = t.split(":")
        return int(hh) * 60 + int(mm)

    def end_abs(day: int, shift_id: str) -> int:
        s = shift_by_id[shift_id]
        start = minutes(s["start"])
        end = minutes(s["end"])
        if end <= start:  # wraps past midnight
            end += 24 * 60
        return day * 24 * 60 + end

    def start_abs(day: int, shift_id: str) -> int:
        s = shift_by_id[shift_id]
        return day * 24 * 60 + minutes(s["start"])

    violations: list[str] = []
    for n in instance["nurses"]:
        nurse_id = n["id"]
        for d in range(horizon - 1):
            s1 = by_nurse_day.get((nurse_id, d))
            s2 = by_nurse_day.get((nurse_id, d + 1))
            if s1 is None or s2 is None:
                continue
            gap_minutes = start_abs(d + 1, s2) - end_abs(d, s1)
            if gap_minutes < min_rest * 60:
                violations.append(
                    f"nurse {nurse_id} {s1}(d{d}) -> {s2}(d{d + 1}): "
                    f"{gap_minutes / 60:.1f}h rest"
                )
    if violations:
        return Check(
            passed=False,
            message=f"Rest >= {min_rest}h: {len(violations)} violation(s); first: {violations[0]}",
        )
    return Check(passed=True, message=f"Rest >= {min_rest}h OK")


def validate(
    instance: dict[str, Any],
    schedule: dict[str, Any],
    schema_path: Path | None = None,
) -> list[Check]:
    checks: list[Check] = []
    if schema_path is not None:
        checks.append(_validate_instance_schema(instance, schema_path))
    checks.extend(_check_references(instance, schedule))
    checks.append(_check_coverage(instance, schedule))
    checks.append(_check_at_most_one_per_day(schedule))
    checks.append(_check_forbidden_transitions(instance, schedule))
    checks.append(_check_max_consecutive(instance, schedule))
    checks.append(_check_fixed_off(instance, schedule))
    checks.append(_check_rest(instance, schedule))
    return checks


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


def _parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--instance", required=True, type=Path, help="Path to the NSP instance JSON.")
    p.add_argument("--schedule", required=True, type=Path, help="Path to the schedule JSON to validate.")
    p.add_argument(
        "--schema",
        type=Path,
        default=None,
        help="Optional: path to data/nsp/schema.json for schema validation.",
    )
    return p.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv)
    instance = _load_json(args.instance)
    schedule = _load_json(args.schedule)

    schema_path = args.schema
    # Auto-discover schema next to the instance if not provided.
    if schema_path is None:
        candidate = args.instance.parent / "schema.json"
        if candidate.exists():
            schema_path = candidate

    checks = validate(instance, schedule, schema_path=schema_path)
    for c in checks:
        print(c.render())

    failed = sum(1 for c in checks if not c.passed)
    if failed:
        print(f"\n{failed} check(s) failed.", file=sys.stderr)
        return 1
    print("\nAll hard constraints OK.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
