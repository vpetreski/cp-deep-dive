"""Exercise 13-C - nurse symmetry breaking.

Nurses with identical attributes create a symmetric search space: any
valid schedule has many "equivalent" siblings obtained by permuting
them. CP-SAT auto-detects most of this, but manual lexicographic
ordering on identical-nurse groups can still help on large instances.

In this exercise we add a lexicographic constraint on the two
identical-skill nurses (N1, N2 in toy-02 — both general-only, same
contract hours) and compare solver behaviour with vs without the break.

Run:
    uv run python -m py_cp_sat_ch13.solutions.exercise_13_c_symmetry
"""

from __future__ import annotations

import pathlib
import time

from nsp_core import ObjectiveWeights, SolveParams, SolveStatus, load_instance
from nsp_core.model_v2 import build_model as build_soft_model
from ortools.sat.python import cp_model

DATA = pathlib.Path(__file__).resolve().parents[6] / "data" / "nsp"


def main() -> None:
    inst = load_instance(DATA / "toy-02.json")
    weights = ObjectiveWeights()
    params = SolveParams(time_limit_seconds=10.0, num_workers=4, random_seed=42)

    print(f"{'variant':>22} {'status':>10} {'obj':>8} {'wall_s':>8}")
    print("-" * 52)

    # Baseline: no manual symmetry break.
    baseline = _solve_with_optional_break(
        inst, weights, params, break_symmetry=False,
    )
    print(
        f"{'baseline':>22} {baseline.status:>10}"
        f" {(f'{baseline.obj:.1f}' if baseline.obj is not None else '-'):>8}"
        f" {baseline.wall_seconds:>8.2f}"
    )

    broken = _solve_with_optional_break(
        inst, weights, params, break_symmetry=True,
    )
    print(
        f"{'with N1<=N2 break':>22} {broken.status:>10}"
        f" {(f'{broken.obj:.1f}' if broken.obj is not None else '-'):>8}"
        f" {broken.wall_seconds:>8.2f}"
    )
    print()
    print(
        "Manual symmetry breaks usually help most when:\n"
        "  - two or more nurses are truly interchangeable (same skills, contract).\n"
        "  - the instance is large enough that the solver's auto-detection is noisy.\n"
        "On toy-02 the effect is within noise; the point here is the mechanism."
    )


class _Outcome:
    status: str
    obj: float | None
    wall_seconds: float

    def __init__(self, status: str, obj: float | None, wall_seconds: float) -> None:
        self.status = status
        self.obj = obj
        self.wall_seconds = wall_seconds


def _solve_with_optional_break(
    inst: object,
    weights: ObjectiveWeights,
    params: SolveParams,
    *,
    break_symmetry: bool,
) -> _Outcome:
    model, vars_, terms = build_soft_model(inst, weights)  # type: ignore[arg-type]

    if break_symmetry:
        # N1 and N2 are both general-only in toy-02; force total(N1) <= total(N2).
        n_ids_by_day_shift: dict[str, list[cp_model.IntVar]] = {"N1": [], "N2": []}
        for (n_id, _d, _s), var in vars_.x.items():
            if n_id in n_ids_by_day_shift:
                n_ids_by_day_shift[n_id].append(var)
        if n_ids_by_day_shift["N1"] and n_ids_by_day_shift["N2"]:
            tot1 = sum(n_ids_by_day_shift["N1"])
            tot2 = sum(n_ids_by_day_shift["N2"])
            model.add(tot1 <= tot2)

    # Re-attach the weighted-sum objective (model.minimize replaces it).
    model.minimize(
        weights.preference * terms.sc1_preference
        + weights.fairness * terms.sc2_fairness_spread
        + weights.workload_balance * terms.sc3_workload_spread_x10
        + weights.weekend_distribution * terms.sc4_weekend_spread
        + weights.consecutive_days_off * terms.sc5_isolated_off
    )

    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = params.time_limit_seconds
    solver.parameters.num_search_workers = params.num_workers
    solver.parameters.random_seed = params.random_seed
    t0 = time.perf_counter()
    status = solver.solve(model)
    wall = time.perf_counter() - t0
    wire = {
        cp_model.OPTIMAL: SolveStatus.OPTIMAL,
        cp_model.FEASIBLE: SolveStatus.FEASIBLE,
        cp_model.INFEASIBLE: SolveStatus.INFEASIBLE,
        cp_model.UNKNOWN: SolveStatus.UNKNOWN,
    }.get(status, SolveStatus.UNKNOWN)
    obj = float(solver.objective_value) if wire in {
        SolveStatus.OPTIMAL, SolveStatus.FEASIBLE,
    } else None
    return _Outcome(status=wire.value, obj=obj, wall_seconds=wall)


if __name__ == "__main__":
    main()
