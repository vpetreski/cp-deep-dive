"""Exercise 5.4 — Multi-objective via lexicographic (solve → fix → re-solve).

CP-SAT doesn't support native lex objectives. The canonical workaround:
    1. Maximize the *primary* objective (value) → get ``best_value``.
    2. Add a hard constraint ``value_expr == best_value``.
    3. Replace the objective with ``minimize(weight_expr)``.
    4. Re-solve.

The result: the single assignment with the smallest total weight among all
assignments achieving ``best_value``.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from ortools.sat.python import cp_model

from py_cp_sat_ch05.knapsack import DEMO_CAPACITY, DEMO_ITEMS, Item, solve_knapsack


@dataclass(frozen=True)
class LexResult:
    """Lex result: primary then secondary objective value."""

    status: str
    value: int
    weight: int
    chosen: list[str]


def solve_lex_knapsack(
    items: Sequence[Item], capacity: int, *, time_limit: float = 5.0
) -> LexResult:
    """Solve for max-value first, then min-weight among max-value packings."""
    primary = solve_knapsack(items, capacity, time_limit=time_limit)
    if primary.value is None:
        return LexResult(status=primary.status, value=0, weight=0, chosen=[])
    best_value = primary.value

    model = cp_model.CpModel()
    x = [model.new_bool_var(f"x_{it.name}") for it in items]
    weight_expr = sum(items[i].weight * x[i] for i in range(len(items)))
    value_expr = sum(items[i].value * x[i] for i in range(len(items)))
    model.add(weight_expr <= capacity)
    model.add(value_expr == best_value)
    model.minimize(weight_expr)

    solver = cp_model.CpSolver()
    solver.parameters.random_seed = 42
    solver.parameters.max_time_in_seconds = time_limit
    status = solver.solve(model)
    status_name = solver.status_name(status)
    if status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        return LexResult(status=status_name, value=best_value, weight=0, chosen=[])

    chosen = [items[i].name for i in range(len(items)) if solver.boolean_value(x[i])]
    return LexResult(
        status=status_name,
        value=best_value,
        weight=int(solver.objective_value),
        chosen=chosen,
    )


def main() -> None:
    first = solve_knapsack(list(DEMO_ITEMS), DEMO_CAPACITY)
    lex = solve_lex_knapsack(list(DEMO_ITEMS), DEMO_CAPACITY)
    assert first.value is not None
    first_weight = sum(
        item.weight for item in DEMO_ITEMS if item.name in set(first.chosen)
    )
    print(f"plain max-value: value={first.value}  weight={first_weight}  chosen={first.chosen}")
    print(f"lex (v,-w):      value={lex.value}  weight={lex.weight}  chosen={lex.chosen}")


if __name__ == "__main__":
    main()
