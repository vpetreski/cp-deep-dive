"""Element — variable-index lookup into a cost array."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from ortools.sat.python import cp_model

DEMO_COSTS: tuple[int, ...] = (7, 3, 12, 1, 8, 5, 9)


@dataclass(frozen=True)
class ElementResult:
    status: str
    index: int
    cost: int


def solve_min_cost_pick(
    costs: Sequence[int] = DEMO_COSTS, *, time_limit: float = 5.0
) -> ElementResult:
    """Pick the index whose cost is minimum via ``add_element``."""
    if not costs:
        raise ValueError("costs must be non-empty")

    model = cp_model.CpModel()
    idx = model.new_int_var(0, len(costs) - 1, "idx")
    cost = model.new_int_var(min(costs), max(costs), "cost")
    model.add_element(idx, list(costs), cost)
    model.minimize(cost)

    solver = cp_model.CpSolver()
    solver.parameters.random_seed = 42
    solver.parameters.max_time_in_seconds = time_limit
    status = solver.solve(model)
    status_name = solver.status_name(status)
    if status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        return ElementResult(status=status_name, index=-1, cost=-1)
    return ElementResult(
        status=status_name, index=int(solver.value(idx)), cost=int(solver.value(cost))
    )
