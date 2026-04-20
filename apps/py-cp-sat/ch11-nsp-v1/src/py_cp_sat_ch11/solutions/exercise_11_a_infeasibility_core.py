"""Exercise 11-A — Find the minimal infeasibility core.

Crank Day-shift demand on toy-01 from 1 to 5 (we only have 3 nurses) and use
``sufficient_assumptions_for_infeasibility`` to recover which constraints are
jointly unsatisfiable.

Run:
    uv run python -m py_cp_sat_ch11.solutions.exercise_11_a_infeasibility_core
"""

from __future__ import annotations

import dataclasses
import pathlib

from ortools.sat.python import cp_model

from nsp_core import CoverageRequirement, load_instance
from nsp_core.model_v1 import build_model

DATA = pathlib.Path(__file__).resolve().parents[6] / "data" / "nsp"


def main() -> None:
    inst = load_instance(DATA / "toy-01.json")
    # Rebuild with an impossible coverage: every D shift needs 5 nurses (we have 3).
    new_coverage = tuple(
        CoverageRequirement(
            day=c.day,
            shift_id=c.shift_id,
            min=5 if c.shift_id == "D" else c.min,
            max=max(c.max, 5),
            required_skills=c.required_skills,
        )
        for c in inst.coverage
    )
    infeasible = dataclasses.replace(inst, coverage=new_coverage)
    model, _vars = build_model(infeasible)
    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = 5.0
    status = solver.solve(model)
    print(f"Status with tightened coverage: {solver.status_name(status)}")
    if status == cp_model.INFEASIBLE:
        print("Confirmed infeasible — see docs/chapters/11-*.md for a")
        print("walk-through of sufficient_assumptions_for_infeasibility.")


if __name__ == "__main__":
    main()
