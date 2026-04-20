"""Exercise 11-E — Certificate round-trip.

Solve toy-01, dump the schedule as JSON, then re-read the JSON and feed it
through ``nsp_core.validate_schedule``. Any mutation of a single cell should
cause the validator to surface a hard violation.

Run:
    uv run python -m py_cp_sat_ch11.solutions.exercise_11_e_certificate_roundtrip
"""

from __future__ import annotations

import dataclasses
import json
import pathlib
import tempfile

from nsp_core import (
    Assignment,
    Schedule,
    SolveParams,
    load_instance,
    solve,
    validate_schedule,
)

DATA = pathlib.Path(__file__).resolve().parents[6] / "data" / "nsp"


def _schedule_to_json(sched: Schedule) -> str:
    return json.dumps(
        {
            "instanceId": sched.instance_id,
            "jobId": sched.job_id,
            "assignments": [
                {"nurseId": a.nurse_id, "day": a.day, "shiftId": a.shift_id}
                for a in sched.assignments
            ],
        },
        indent=2,
    )


def _schedule_from_json(raw: dict[str, object]) -> Schedule:
    assignments_raw = raw.get("assignments", [])
    assert isinstance(assignments_raw, list)
    assignments = tuple(
        Assignment(
            nurse_id=str(a["nurseId"]),  # type: ignore[index]
            day=int(a["day"]),  # type: ignore[index]
            shift_id=(
                None if a.get("shiftId") is None else str(a["shiftId"])  # type: ignore[index]
            ),
        )
        for a in assignments_raw
    )
    return Schedule(
        instance_id=str(raw.get("instanceId", "")),
        assignments=assignments,
        job_id=raw.get("jobId") if isinstance(raw.get("jobId"), str) else None,
    )


def main() -> None:
    inst = load_instance(DATA / "toy-01.json")
    result = solve(
        inst,
        SolveParams(time_limit_seconds=10.0, num_workers=2, random_seed=42),
        objective="hard",
    )
    assert result.schedule is not None

    with tempfile.TemporaryDirectory() as tmp:
        out = pathlib.Path(tmp) / "schedule.json"
        out.write_text(_schedule_to_json(result.schedule))
        print(f"Dumped {len(result.schedule.assignments)} assignments to {out}")

        reloaded = _schedule_from_json(json.loads(out.read_text()))
        errs = validate_schedule(inst, reloaded)
        print(f"Validator on clean schedule: {len(errs)} violations")
        assert all(v.severity == "soft" for v in errs)

        # Mutate: remove the first assignment to create a coverage gap.
        if reloaded.assignments:
            mutated = dataclasses.replace(
                reloaded, assignments=reloaded.assignments[1:]
            )
            mut_errs = validate_schedule(inst, mutated)
            hard = [v for v in mut_errs if v.severity == "hard"]
            print(f"Validator on mutated schedule: {len(hard)} hard violations surfaced")


if __name__ == "__main__":
    main()
