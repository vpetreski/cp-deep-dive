"""N-Queens via CP-SAT.

Three `AllDifferent`s: one on rows, one on ``q[i] + i`` (``\\`` diagonals),
one on ``q[i] - i`` (``/`` diagonals). The whole chapter 4 intuition lives
in those 3 lines.
"""

from __future__ import annotations

from dataclasses import dataclass, field

from ortools.sat.python import cp_model


@dataclass(frozen=True)
class NQueensResult:
    """Outcome of a single N-Queens solve."""

    status: str
    n: int
    positions: list[int] = field(default_factory=list)


_OK = (cp_model.OPTIMAL, cp_model.FEASIBLE)


def solve_n_queens(n: int = 8, time_limit_s: float = 30.0) -> NQueensResult:
    """Find one N-Queens solution on an ``n x n`` board.

    Returns ``positions[i]`` = row of the queen in column ``i``.
    Uses the diagonal ``AllDifferent`` trick.
    """
    model = cp_model.CpModel()
    queens = [model.new_int_var(0, n - 1, f"q_{i}") for i in range(n)]

    model.add_all_different(queens)
    model.add_all_different([queens[i] + i for i in range(n)])
    model.add_all_different([queens[i] - i for i in range(n)])

    solver = cp_model.CpSolver()
    solver.parameters.random_seed = 42
    solver.parameters.max_time_in_seconds = time_limit_s
    status = solver.solve(model)
    if status not in _OK:
        return NQueensResult(status=solver.status_name(status), n=n)

    positions = [int(solver.value(queens[i])) for i in range(n)]
    return NQueensResult(status=solver.status_name(status), n=n, positions=positions)


def count_n_queens_solutions(n: int = 8, time_limit_s: float = 30.0) -> int:
    """Enumerate and count all distinct N-Queens solutions on an ``n x n`` board.

    Valid for small ``n`` (n=8 takes milliseconds, n=12 a few seconds).
    """
    model = cp_model.CpModel()
    queens = [model.new_int_var(0, n - 1, f"q_{i}") for i in range(n)]

    model.add_all_different(queens)
    model.add_all_different([queens[i] + i for i in range(n)])
    model.add_all_different([queens[i] - i for i in range(n)])

    solver = cp_model.CpSolver()
    solver.parameters.enumerate_all_solutions = True
    solver.parameters.random_seed = 42
    solver.parameters.max_time_in_seconds = time_limit_s

    class Counter(cp_model.CpSolverSolutionCallback):
        def __init__(self) -> None:
            super().__init__()
            self.count = 0

        def on_solution_callback(self) -> None:
            self.count += 1

    counter = Counter()
    solver.solve(model, counter)
    return counter.count
