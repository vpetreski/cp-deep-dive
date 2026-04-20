"""Exercise 5.1 — Bounded knapsack.

Replace each ``x[i] ∈ {0, 1}`` with ``x[i] ∈ {0, …, max_count[i]}``. The
bounded version must be at least as good as the 0/1 version (more freedom).
"""

from __future__ import annotations

import random
from collections.abc import Sequence
from dataclasses import dataclass

from ortools.sat.python import cp_model

from py_cp_sat_ch05.knapsack import DEMO_CAPACITY, DEMO_ITEMS, Item, solve_knapsack


@dataclass(frozen=True)
class BoundedResult:
    status: str
    value: int
    counts: dict[str, int]


def solve_bounded_knapsack(
    items: Sequence[Item],
    capacity: int,
    max_counts: Sequence[int],
    *,
    time_limit: float = 5.0,
) -> BoundedResult:
    """Bounded knapsack: each item has its own per-item upper bound."""
    if len(items) != len(max_counts):
        raise ValueError("items and max_counts must have equal length")

    model = cp_model.CpModel()
    x = [
        model.new_int_var(0, max_counts[i], f"x_{items[i].name}") for i in range(len(items))
    ]
    model.add(sum(items[i].weight * x[i] for i in range(len(items))) <= capacity)
    model.maximize(sum(items[i].value * x[i] for i in range(len(items))))

    solver = cp_model.CpSolver()
    solver.parameters.random_seed = 42
    solver.parameters.max_time_in_seconds = time_limit
    status = solver.solve(model)
    status_name = solver.status_name(status)
    if status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        return BoundedResult(status=status_name, value=0, counts={})
    counts = {items[i].name: int(solver.value(x[i])) for i in range(len(items))}
    return BoundedResult(status=status_name, value=int(solver.objective_value), counts=counts)


def compare_01_vs_bounded() -> tuple[int, int]:
    """Return ``(plain_01_value, bounded_value)`` for the demo instance."""
    rng = random.Random(42)
    max_counts = [rng.randint(1, 3) for _ in DEMO_ITEMS]
    plain = solve_knapsack(list(DEMO_ITEMS), DEMO_CAPACITY, time_limit=5.0)
    bounded = solve_bounded_knapsack(
        list(DEMO_ITEMS), DEMO_CAPACITY, max_counts, time_limit=5.0
    )
    assert plain.value is not None
    return plain.value, bounded.value


def main() -> None:
    plain_value, bounded_value = compare_01_vs_bounded()
    print(f"0/1 knapsack optimum    = {plain_value}")
    print(f"bounded knapsack optimum = {bounded_value}")
    print(f"improvement = +{bounded_value - plain_value}")


if __name__ == "__main__":
    main()
