"""Exercise 12-D - Add a custom user-defined soft constraint.

The built-in SC-1..SC-5 set isn't exhaustive. A hospital might want to say
"please don't assign nurse N3 to N shifts at all, but it's soft - a last-
resort exception is fine." We call this SC-X (eXtension).

This exercise:
1. Builds the v2 soft model with the standard SC-1..SC-5 weights.
2. Adds an extra penalty term: for every (N3, day, N) assignment, count 1.
3. Multiplies that count by a configurable weight and adds it to the existing
   objective expression before solving.

Run:
    uv run python -m py_cp_sat_ch12.solutions.exercise_12_d_custom_objective
"""

from __future__ import annotations

import pathlib

from nsp_core import ObjectiveWeights, SolveParams, SolveStatus, load_instance
from nsp_core.model_v2 import build_model as build_soft_model
from ortools.sat.python import cp_model

DATA = pathlib.Path(__file__).resolve().parents[6] / "data" / "nsp"

SCX_WEIGHT = 8  # N3-avoids-N weight; tunable


def main() -> None:
    inst = load_instance(DATA / "toy-02.json")
    weights = ObjectiveWeights()

    model, vars_, terms = build_soft_model(inst, weights)

    # ---- SC-X: soft constraint "N3 should avoid N shifts" ----
    scx_term = _build_scx(model, vars_, target_nurse="N3", target_shift="N")

    # Re-wire the objective: existing weighted sum + SCX_WEIGHT * scx_term.
    existing_obj = (
        weights.preference * terms.sc1_preference
        + weights.fairness * terms.sc2_fairness_spread
        + weights.workload_balance * terms.sc3_workload_spread_x10
        + weights.weekend_distribution * terms.sc4_weekend_spread
        + weights.consecutive_days_off * terms.sc5_isolated_off
    )
    model.minimize(existing_obj + SCX_WEIGHT * scx_term)

    solver = cp_model.CpSolver()
    params = SolveParams(time_limit_seconds=15.0, num_workers=4, random_seed=42)
    solver.parameters.max_time_in_seconds = params.time_limit_seconds
    solver.parameters.num_search_workers = params.num_workers
    solver.parameters.random_seed = params.random_seed
    status = solver.solve(model)
    wire = _to_wire(status)
    assert wire in {SolveStatus.OPTIMAL, SolveStatus.FEASIBLE}, f"status={wire}"

    scx_count = int(solver.value(scx_term))
    obj = float(solver.objective_value)
    print(f"Instance: {inst.id}")
    print(f"Total weighted objective: {obj:.1f}")
    print(f"SC-X (N3 on nights) raw violations: {scx_count}")
    print(
        f"SC-X weighted contribution: {scx_count * SCX_WEIGHT} "
        f"(= {scx_count} x {SCX_WEIGHT})"
    )

    # With weight 8 and 14 days of night demand, N3 should be off nights if
    # other nurses can cover. We demonstrate by flipping SCX_WEIGHT to 0 and
    # re-solving to show the shift can be pinned on N3 when there's no
    # penalty.
    print()
    print("Re-solve with SCX_WEIGHT=0 (for contrast).")
    model2, vars2, terms2 = build_soft_model(inst, weights)
    scx_term2 = _build_scx(model2, vars2, target_nurse="N3", target_shift="N")
    model2.minimize(
        weights.preference * terms2.sc1_preference
        + weights.fairness * terms2.sc2_fairness_spread
        + weights.workload_balance * terms2.sc3_workload_spread_x10
        + weights.weekend_distribution * terms2.sc4_weekend_spread
        + weights.consecutive_days_off * terms2.sc5_isolated_off
    )
    solver2 = cp_model.CpSolver()
    solver2.parameters.max_time_in_seconds = 15.0
    solver2.parameters.num_search_workers = 4
    solver2.parameters.random_seed = 42
    solver2.solve(model2)
    baseline_count = int(solver2.value(scx_term2))
    print(f"Without SC-X, N3 was scheduled on {baseline_count} night shifts.")


def _build_scx(
    model: cp_model.CpModel,
    vars_: object,
    *,
    target_nurse: str,
    target_shift: str,
) -> cp_model.LinearExprT:
    """Linear expression = number of (target_nurse, *, target_shift) cells = 1."""
    cells: list[cp_model.IntVar] = []
    for (n_id, _, s_id), var in vars_.x.items():  # type: ignore[union-attr]
        if n_id == target_nurse and s_id == target_shift:
            cells.append(var)
    if not cells:
        return 0
    return sum(cells)


def _to_wire(status: int) -> SolveStatus:
    return {
        cp_model.OPTIMAL: SolveStatus.OPTIMAL,
        cp_model.FEASIBLE: SolveStatus.FEASIBLE,
        cp_model.INFEASIBLE: SolveStatus.INFEASIBLE,
        cp_model.UNKNOWN: SolveStatus.UNKNOWN,
        cp_model.MODEL_INVALID: SolveStatus.MODEL_INVALID,
    }.get(status, SolveStatus.UNKNOWN)


if __name__ == "__main__":
    main()
