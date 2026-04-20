"""TSP via ``add_circuit`` — 8 cities, symmetric distance matrix.

The ``Circuit`` global treats each boolean ``arc[i, j]`` as an edge; the
propagator guarantees the selected arcs form exactly one Hamiltonian cycle.
"""

from __future__ import annotations

from dataclasses import dataclass

from ortools.sat.python import cp_model

# Symmetric 8-city distance matrix — reusable demo instance for the chapter.
DEMO_DISTANCES: tuple[tuple[int, ...], ...] = (
    (0, 29, 20, 21, 16, 31, 100, 12),
    (29, 0, 15, 29, 28, 40, 72, 21),
    (20, 15, 0, 15, 14, 25, 81, 9),
    (21, 29, 15, 0, 4, 12, 92, 12),
    (16, 28, 14, 4, 0, 16, 94, 9),
    (31, 40, 25, 12, 16, 0, 95, 24),
    (100, 72, 81, 92, 94, 95, 0, 90),
    (12, 21, 9, 12, 9, 24, 90, 0),
)


@dataclass(frozen=True)
class TspResult:
    """Outcome of a TSP solve."""

    status: str
    tour: list[int]  # city indices in visit order, starting and ending at 0
    length: int


def solve_tsp(
    distances: list[list[int]] | tuple[tuple[int, ...], ...] = DEMO_DISTANCES,
    *,
    time_limit: float = 10.0,
) -> TspResult:
    """Solve TSP by posting ``add_circuit`` on directed-arc booleans."""
    n = len(distances)
    if any(len(row) != n for row in distances):
        raise ValueError("distances must be square")

    model = cp_model.CpModel()
    arcs: dict[tuple[int, int], cp_model.IntVar] = {}
    for i in range(n):
        for j in range(n):
            if i == j:
                continue
            arcs[i, j] = model.new_bool_var(f"arc_{i}_{j}")

    model.add_circuit([(i, j, arcs[i, j]) for (i, j) in arcs])
    model.minimize(sum(distances[i][j] * arcs[i, j] for (i, j) in arcs))

    solver = cp_model.CpSolver()
    solver.parameters.random_seed = 42
    solver.parameters.max_time_in_seconds = time_limit
    status = solver.solve(model)
    status_name = solver.status_name(status)
    if status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        return TspResult(status=status_name, tour=[], length=0)

    tour = [0]
    cur = 0
    while True:
        next_city = next(
            j for j in range(n) if j != cur and solver.boolean_value(arcs[cur, j])
        )
        tour.append(next_city)
        cur = next_city
        if cur == 0:
            break

    return TspResult(
        status=status_name,
        tour=tour,
        length=int(solver.objective_value),
    )
