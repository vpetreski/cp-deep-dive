"""Exercise 11-D — Presolve inspection.

Count variables created by the "skip unavailable cell" pattern (what
``nsp_core.model_v1`` uses) and compare against a deliberately sloppy version
that creates a variable for every (nurse, day, shift) triple and pins the
unavailable ones to zero. Print the two counts to show the skip pattern wins
on raw-variable bookkeeping, even if presolve ultimately catches up.

Run:
    uv run python -m py_cp_sat_ch11.solutions.exercise_11_d_presolve_inspection
"""

from __future__ import annotations

import pathlib

from nsp_core import load_instance
from nsp_core.model_v1 import build_model
from ortools.sat.python import cp_model

DATA = pathlib.Path(__file__).resolve().parents[6] / "data" / "nsp"


def _sloppy_variable_count(instance) -> int:  # type: ignore[no-untyped-def]
    """Count variables you'd create if you ignored availability (each cell -> var)."""
    return len(instance.nurses) * instance.horizon_days * len(instance.shifts)


def main() -> None:
    inst = load_instance(DATA / "toy-02.json")
    tight_model, tight_vars = build_model(inst)
    tight_count = len(tight_vars.x)
    sloppy_count = _sloppy_variable_count(inst)

    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = 5.0
    status = solver.solve(tight_model)
    print(f"Skip-cell model: {tight_count} vars, status={solver.status_name(status)}")
    print(f"Sloppy baseline: {sloppy_count} vars (no skip, would be pinned)")
    print(f"Saved {sloppy_count - tight_count} vars upfront.")


if __name__ == "__main__":
    main()
