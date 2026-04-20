"""Chapter 5 — 0/1 Knapsack with incumbent-trace callback.

Models the classic robber-in-a-jewelry-shop scenario:
    - One boolean per item (take / leave).
    - Capacity constraint on total weight.
    - Maximize total value.

Exposes a :class:`IncumbentListener` that records every improving incumbent the
solver reports, so downstream code can plot the bound/incumbent convergence
dance.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass, field

from ortools.sat.python import cp_model


@dataclass(frozen=True)
class Item:
    """A single knapsack item — name, weight (kg), value (gold)."""

    name: str
    weight: int
    value: int


@dataclass(frozen=True)
class IncumbentTrace:
    """One row of the solver's incumbent log: wall time, incumbent, bound."""

    wall_time: float
    objective: float
    bound: float


@dataclass(frozen=True)
class KnapsackResult:
    """Outcome of a knapsack solve."""

    status: str
    value: int | None
    bound: float
    chosen: list[str]
    trace: list[IncumbentTrace] = field(default_factory=list)


# Classic 15-item jewelry shop — stable ordering so tests are deterministic.
DEMO_ITEMS: tuple[Item, ...] = (
    Item("gold-ring", weight=2, value=7),
    Item("silver-cup", weight=3, value=5),
    Item("bronze-bust", weight=4, value=3),
    Item("emerald", weight=5, value=12),
    Item("ruby", weight=5, value=10),
    Item("pearl-necklace", weight=3, value=8),
    Item("brass-clock", weight=6, value=4),
    Item("diamond", weight=7, value=20),
    Item("sapphire", weight=4, value=9),
    Item("opal", weight=3, value=6),
    Item("platinum-bar", weight=8, value=18),
    Item("jade-figurine", weight=5, value=8),
    Item("amber-stone", weight=2, value=4),
    Item("ivory-carving", weight=6, value=11),
    Item("copper-coin", weight=1, value=2),
)

DEMO_CAPACITY: int = 20


class _IncumbentListener(cp_model.CpSolverSolutionCallback):
    """Callback that records every improving incumbent the solver finds."""

    def __init__(self) -> None:
        super().__init__()
        self.trace: list[IncumbentTrace] = []

    def on_solution_callback(self) -> None:  # noqa: D401 — OR-Tools API
        self.trace.append(
            IncumbentTrace(
                wall_time=float(self.wall_time),
                objective=float(self.objective_value),
                bound=float(self.best_objective_bound),
            )
        )


def solve_knapsack(
    items: Sequence[Item],
    capacity: int,
    *,
    time_limit: float = 5.0,
    relative_gap: float | None = None,
    num_workers: int = 8,
    log_progress: bool = False,
) -> KnapsackResult:
    """Solve the 0/1 knapsack for ``items`` under ``capacity`` (kg).

    Returns a :class:`KnapsackResult` including the solver's incumbent trace so
    callers can plot bound vs incumbent over wall time (Exercise 5.2).
    """
    if capacity < 0:
        raise ValueError(f"capacity must be non-negative, got {capacity}")
    for item in items:
        if item.weight < 0 or item.value < 0:
            raise ValueError(f"{item.name} has negative weight or value")

    model = cp_model.CpModel()
    x = [model.new_bool_var(f"x_{it.name}") for it in items]

    model.add(sum(it.weight * x[i] for i, it in enumerate(items)) <= capacity)
    model.maximize(sum(it.value * x[i] for i, it in enumerate(items)))

    solver = cp_model.CpSolver()
    solver.parameters.random_seed = 42
    solver.parameters.max_time_in_seconds = time_limit
    solver.parameters.num_search_workers = num_workers
    solver.parameters.log_search_progress = log_progress
    if relative_gap is not None:
        solver.parameters.relative_gap_limit = relative_gap

    listener = _IncumbentListener()
    status = solver.solve(model, listener)

    status_name = solver.status_name(status)
    if status in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        value = int(solver.objective_value)
        chosen = [items[i].name for i in range(len(items)) if solver.boolean_value(x[i])]
    else:
        value = None
        chosen = []

    return KnapsackResult(
        status=status_name,
        value=value,
        bound=float(solver.best_objective_bound),
        chosen=chosen,
        trace=listener.trace,
    )


def enumerate_optimal_subsets(
    items: Sequence[Item], capacity: int, *, time_limit: float = 5.0
) -> list[list[str]]:
    """Enumerate every subset that achieves the optimal knapsack value.

    Pattern (Exercise 5.5): solve once to find the optimum, freeze the objective
    as an equality constraint, drop the ``maximize`` by building a *fresh*
    CSP-only model, then enumerate every feasible subset.
    """
    first = solve_knapsack(items, capacity, time_limit=time_limit)
    if first.value is None:
        return []

    best_value = first.value
    csp = cp_model.CpModel()
    y = [csp.new_bool_var(f"y_{it.name}") for it in items]
    csp.add(sum(it.weight * y[i] for i, it in enumerate(items)) <= capacity)
    csp.add(sum(it.value * y[i] for i, it in enumerate(items)) == best_value)

    solver = cp_model.CpSolver()
    solver.parameters.random_seed = 42
    solver.parameters.enumerate_all_solutions = True
    solver.parameters.max_time_in_seconds = time_limit
    # Enumeration needs a single worker — parallel workers are incompatible.
    solver.parameters.num_search_workers = 1

    subsets: list[list[str]] = []

    class _Collector(cp_model.CpSolverSolutionCallback):
        def on_solution_callback(self) -> None:  # noqa: D401 — OR-Tools API
            subsets.append(
                [items[i].name for i in range(len(items)) if self.boolean_value(y[i])]
            )

    solver.solve(csp, _Collector())
    return subsets
