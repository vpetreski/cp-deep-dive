"""Chapter 5 — Bin Packing — minimize number of bins used.

Decision: ``assign[i, j] = 1`` iff item ``i`` goes into bin ``j``, plus
``used[j] = 1`` iff any item is packed into bin ``j``. Hard constraints enforce
each item lands in exactly one bin and the capacity is respected in every bin.
The objective minimizes ``sum(used)``.

This is a companion optimization example to 0/1 knapsack — a COP where the
objective is a *count* rather than a weighted sum.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from ortools.sat.python import cp_model


@dataclass(frozen=True)
class BinPackingResult:
    """Result of a bin packing solve."""

    status: str
    num_bins: int | None
    assignment: list[list[int]]  # bins[j] = list of item indices in bin j
    bin_loads: list[int]  # total weight per used bin, in the same order


# Small demo instance — weights chosen so optimum is 3 bins of capacity 10.
DEMO_WEIGHTS: tuple[int, ...] = (5, 4, 4, 3, 3, 3, 2, 2, 2, 2)
DEMO_CAPACITY: int = 10


def solve_bin_packing(
    weights: Sequence[int],
    capacity: int,
    *,
    time_limit: float = 5.0,
    num_workers: int = 8,
    log_progress: bool = False,
) -> BinPackingResult:
    """Minimize the number of bins of ``capacity`` needed to pack ``weights``."""
    if capacity <= 0:
        raise ValueError(f"capacity must be positive, got {capacity}")
    for i, w in enumerate(weights):
        if w <= 0:
            raise ValueError(f"weights[{i}] = {w} must be positive")
        if w > capacity:
            raise ValueError(f"weights[{i}] = {w} exceeds capacity {capacity}")

    n_items = len(weights)
    # Upper bound: every item in its own bin.
    n_bins = n_items

    model = cp_model.CpModel()
    assign = [
        [model.new_bool_var(f"x_{i}_{j}") for j in range(n_bins)] for i in range(n_items)
    ]
    used = [model.new_bool_var(f"used_{j}") for j in range(n_bins)]

    # Each item lands in exactly one bin.
    for i in range(n_items):
        model.add_exactly_one(assign[i])

    # Capacity respected per bin; any assignment implies the bin is used.
    for j in range(n_bins):
        model.add(sum(weights[i] * assign[i][j] for i in range(n_items)) <= capacity)
        for i in range(n_items):
            model.add_implication(assign[i][j], used[j])

    # Symmetry breaking: bins are interchangeable, so force the used-mask to be
    # "left-aligned" — if bin j+1 is used, bin j must be too.
    for j in range(n_bins - 1):
        model.add_implication(used[j + 1], used[j])

    model.minimize(sum(used))

    solver = cp_model.CpSolver()
    solver.parameters.random_seed = 42
    solver.parameters.max_time_in_seconds = time_limit
    solver.parameters.num_search_workers = num_workers
    solver.parameters.log_search_progress = log_progress

    status = solver.solve(model)
    status_name = solver.status_name(status)

    if status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        return BinPackingResult(status=status_name, num_bins=None, assignment=[], bin_loads=[])

    # Collect non-empty bins, in order.
    bins: list[list[int]] = []
    loads: list[int] = []
    for j in range(n_bins):
        if not solver.boolean_value(used[j]):
            continue
        members = [i for i in range(n_items) if solver.boolean_value(assign[i][j])]
        bins.append(members)
        loads.append(sum(weights[i] for i in members))

    return BinPackingResult(
        status=status_name,
        num_bins=int(solver.objective_value),
        assignment=bins,
        bin_loads=loads,
    )
