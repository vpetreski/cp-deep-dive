"""Chapter 2 — Hello, CP-SAT.

Minimal end-to-end CP-SAT model:

    variables : x, y in [0, 10]  (integers)
    constraints : 3*x + 2*y == 12
                  x + y    <= 5
    objective : maximize x + y

If you can read + run this, you can read CP-SAT.
"""

from __future__ import annotations

from dataclasses import dataclass

from ortools.sat.python import cp_model


@dataclass(frozen=True)
class Solution:
    """The result of solving the tiny model."""

    status: str  # OPTIMAL / FEASIBLE / INFEASIBLE / UNKNOWN / MODEL_INVALID
    x: int
    y: int
    objective: int


# CpSolverStatus values are hashable; keep the annotation loose so mypy accepts
# whatever the OR-Tools binding uses (int in some versions, enum in others).
_STATUS_NAMES: dict[object, str] = {
    cp_model.OPTIMAL: "OPTIMAL",
    cp_model.FEASIBLE: "FEASIBLE",
    cp_model.INFEASIBLE: "INFEASIBLE",
    cp_model.UNKNOWN: "UNKNOWN",
    cp_model.MODEL_INVALID: "MODEL_INVALID",
}


def solve() -> Solution:
    """Build the model, solve it, return the result.

    Separated from ``main()`` so tests can assert on the solution directly.
    """
    model = cp_model.CpModel()

    x = model.new_int_var(0, 10, "x")
    y = model.new_int_var(0, 10, "y")

    model.add(3 * x + 2 * y == 12)
    model.add(x + y <= 5)

    model.maximize(x + y)

    solver = cp_model.CpSolver()
    status = solver.solve(model)

    if status in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        return Solution(
            status=_STATUS_NAMES[status],
            x=int(solver.value(x)),
            y=int(solver.value(y)),
            objective=int(solver.objective_value),
        )
    return Solution(
        status=_STATUS_NAMES.get(status, "UNKNOWN"),
        x=-1,
        y=-1,
        objective=-1,
    )


def main() -> None:
    """CLI entrypoint. Prints the solution in a one-line-per-field format."""
    sol = solve()
    print(f"Status: {sol.status}")
    print(f"x = {sol.x}, y = {sol.y}, objective = {sol.objective}")


if __name__ == "__main__":
    main()
