"""Exercise 6.1 — TSP via MTZ sub-tour elimination vs ``add_circuit``.

Model both formulations on the same 8-city instance; record wall-times and
confirm the optimal tour length matches. MTZ is usually slower; the gap widens
on bigger instances.
"""

from __future__ import annotations

import time
from dataclasses import dataclass

from ortools.sat.python import cp_model

from py_cp_sat_ch06.tsp import DEMO_DISTANCES, solve_tsp


@dataclass(frozen=True)
class TspComparison:
    circuit_length: int
    circuit_seconds: float
    mtz_length: int
    mtz_seconds: float


def solve_tsp_mtz(
    distances: tuple[tuple[int, ...], ...] = DEMO_DISTANCES, *, time_limit: float = 10.0
) -> tuple[int, float]:
    """Classic MTZ formulation: position variables ``u[i]`` enforce a single tour."""
    n = len(distances)
    model = cp_model.CpModel()
    arcs: dict[tuple[int, int], cp_model.IntVar] = {}
    for i in range(n):
        for j in range(n):
            if i == j:
                continue
            arcs[i, j] = model.new_bool_var(f"arc_{i}_{j}")

    # Each node has exactly one outgoing and one incoming edge.
    for i in range(n):
        model.add(sum(arcs[i, j] for j in range(n) if j != i) == 1)
        model.add(sum(arcs[j, i] for j in range(n) if j != i) == 1)

    # Position variables for MTZ. u[0] is free; u[i] in [1, n-1].
    u = [model.new_int_var(0, n - 1, f"u_{i}") for i in range(n)]
    model.add(u[0] == 0)
    for i in range(1, n):
        model.add(u[i] >= 1)
    # u[i] - u[j] + n * arc[i, j] <= n - 1  for i != j, neither is 0.
    for (i, j), arc_ij in arcs.items():
        if i == 0 or j == 0:
            continue
        model.add(u[i] - u[j] + n * arc_ij <= n - 1)

    model.minimize(sum(distances[i][j] * arcs[i, j] for (i, j) in arcs))

    solver = cp_model.CpSolver()
    solver.parameters.random_seed = 42
    solver.parameters.max_time_in_seconds = time_limit
    t0 = time.perf_counter()
    status = solver.solve(model)
    elapsed = time.perf_counter() - t0
    assert status in (cp_model.OPTIMAL, cp_model.FEASIBLE), solver.status_name(status)
    return int(solver.objective_value), elapsed


def compare_tsp_formulations(
    distances: tuple[tuple[int, ...], ...] = DEMO_DISTANCES, *, time_limit: float = 10.0
) -> TspComparison:
    t0 = time.perf_counter()
    circuit = solve_tsp(distances, time_limit=time_limit)
    t1 = time.perf_counter()
    mtz_length, mtz_seconds = solve_tsp_mtz(distances, time_limit=time_limit)
    return TspComparison(
        circuit_length=circuit.length,
        circuit_seconds=t1 - t0,
        mtz_length=mtz_length,
        mtz_seconds=mtz_seconds,
    )


def main() -> None:
    cmp = compare_tsp_formulations()
    print(f"Circuit: length={cmp.circuit_length}  time={cmp.circuit_seconds:.3f}s")
    print(f"MTZ:     length={cmp.mtz_length}  time={cmp.mtz_seconds:.3f}s")
    print(f"Match:   {cmp.circuit_length == cmp.mtz_length}")


if __name__ == "__main__":
    main()
