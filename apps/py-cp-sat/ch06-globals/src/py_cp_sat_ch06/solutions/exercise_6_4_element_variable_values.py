"""Exercise 6.4 — Element with a *variable* array of costs.

Instead of a constant cost list, each cost ``c[i]`` is an ``IntVar`` in
``[0, 100]``. Constraint: ``sum(c) == 200``. Choose ``idx`` so that the cost
at that index is minimum.
"""

from __future__ import annotations

from dataclasses import dataclass

from ortools.sat.python import cp_model


@dataclass(frozen=True)
class VariableElementResult:
    status: str
    index: int
    target: int
    costs: list[int]


def solve_variable_element(
    n: int = 7, total: int = 200, *, time_limit: float = 5.0
) -> VariableElementResult:
    if n <= 0:
        raise ValueError("n must be positive")

    model = cp_model.CpModel()
    costs = [model.new_int_var(0, 100, f"c_{i}") for i in range(n)]
    model.add(sum(costs) == total)
    idx = model.new_int_var(0, n - 1, "idx")
    target = model.new_int_var(0, 100, "target")
    model.add_element(idx, costs, target)
    model.minimize(target)

    solver = cp_model.CpSolver()
    solver.parameters.random_seed = 42
    solver.parameters.max_time_in_seconds = time_limit
    status = solver.solve(model)
    status_name = solver.status_name(status)
    if status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        return VariableElementResult(status=status_name, index=-1, target=-1, costs=[])
    return VariableElementResult(
        status=status_name,
        index=int(solver.value(idx)),
        target=int(solver.value(target)),
        costs=[int(solver.value(c)) for c in costs],
    )


def main() -> None:
    result = solve_variable_element()
    print(f"Status: {result.status}")
    print(f"Costs:  {result.costs}  (sum = {sum(result.costs)})")
    print(f"idx={result.index}  target={result.target}")


if __name__ == "__main__":
    main()
