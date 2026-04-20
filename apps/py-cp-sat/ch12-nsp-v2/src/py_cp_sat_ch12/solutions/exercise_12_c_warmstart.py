"""Exercise 12-C - Warm-start a solve with a previous solution as hints.

CP-SAT accepts variable hints via ``model.add_hint(var, value)``. If the hint
is consistent with the hard constraints, CP-SAT repairs what it can't use and
often finds a first incumbent much faster.

This exercise:
1. Solves toy-02 from scratch, records the full `(nurse, day, shift)` schedule.
2. Perturbs the instance by flipping one nurse's availability.
3. Re-solves the perturbed instance two ways:
   - cold (no hints) for baseline wall time.
   - warm (hint every `x[n,d,s]` from the previous solution).

Run:
    uv run python -m py_cp_sat_ch12.solutions.exercise_12_c_warmstart
"""

from __future__ import annotations

import dataclasses
import pathlib
import time

from nsp_core import (
    Instance,
    ObjectiveWeights,
    Schedule,
    SolveParams,
    SolveStatus,
    load_instance,
    solve,
)
from nsp_core.model_v2 import build_model as build_soft_model
from ortools.sat.python import cp_model

DATA = pathlib.Path(__file__).resolve().parents[6] / "data" / "nsp"


def main() -> None:
    base = load_instance(DATA / "toy-02.json")
    weights = ObjectiveWeights()

    print("Step 1: initial solve (cold).")
    t0 = time.perf_counter()
    init = solve(
        base,
        SolveParams(time_limit_seconds=10.0, num_workers=4, random_seed=42),
        objective="weighted",
        weights=weights,
    )
    cold_init_time = time.perf_counter() - t0
    print(
        f"  status={init.status.value}  obj={init.objective}  wall={cold_init_time:.2f}s"
    )
    assert init.status in {SolveStatus.OPTIMAL, SolveStatus.FEASIBLE}
    assert init.schedule is not None

    print("\nStep 2: perturb (mark N1 unavailable on day 4).")
    n1 = next(n for n in base.nurses if n.id == "N1")
    n1_new = dataclasses.replace(n1, unavailable=frozenset({*n1.unavailable, 4}))
    perturbed = dataclasses.replace(
        base,
        nurses=tuple(n1_new if n.id == "N1" else n for n in base.nurses),
    )

    print("\nStep 3a: cold solve of the perturbed instance.")
    t0 = time.perf_counter()
    cold = solve(
        perturbed,
        SolveParams(time_limit_seconds=10.0, num_workers=4, random_seed=42),
        objective="weighted",
        weights=weights,
    )
    cold_time = time.perf_counter() - t0
    print(
        f"  status={cold.status.value}  obj={cold.objective}  wall={cold_time:.2f}s"
    )

    print("\nStep 3b: warm solve (hints from the initial schedule).")
    t0 = time.perf_counter()
    warm = _solve_with_hints(
        perturbed,
        SolveParams(time_limit_seconds=10.0, num_workers=4, random_seed=42),
        weights=weights,
        hints=init.schedule,
    )
    warm_time = time.perf_counter() - t0
    print(
        f"  status={warm['status'].value}  obj={warm['objective']}  wall={warm_time:.2f}s"
    )

    print("\nObservation: on toy-scale instances cold and warm solves look the")
    print("same because CP-SAT finishes in a fraction of a second either way.")
    print("On INRC-II-scale instances warm starts routinely cut TTF by 30-70%.")


def _solve_with_hints(
    instance: Instance,
    params: SolveParams,
    *,
    weights: ObjectiveWeights,
    hints: Schedule,
) -> dict[str, object]:
    model, vars_, _ = build_soft_model(instance, weights)
    hinted: set[tuple[str, int, str]] = set()
    for a in hints.assignments:
        if a.shift_id is None:
            continue
        key = (a.nurse_id, a.day, a.shift_id)
        hinted.add(key)
        var = vars_.x.get(key)
        if var is not None:
            model.add_hint(var, 1)
    # Hint every other (n, d, s) cell to 0 so the repair has maximal info.
    for (n_id, d, s_id), var in vars_.x.items():
        if (n_id, d, s_id) not in hinted:
            model.add_hint(var, 0)

    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = params.time_limit_seconds
    solver.parameters.num_search_workers = params.num_workers
    solver.parameters.random_seed = params.random_seed
    status = solver.solve(model)
    wire = _to_wire(status)
    return {
        "status": wire,
        "objective": float(solver.objective_value) if wire in {
            SolveStatus.OPTIMAL, SolveStatus.FEASIBLE,
        } else None,
    }


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
