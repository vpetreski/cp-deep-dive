"""Exercise 4.5 — Symmetry breaking in N-Queens.

Adds ``q[0] < q[n-1]`` to cut the lex-smallest column-reflected half of the
search space. Compare solution counts with and without the breaker.
"""

from __future__ import annotations

from dataclasses import dataclass

from ortools.sat.python import cp_model


@dataclass(frozen=True)
class CountPair:
    without_breaker: int
    with_breaker: int


def _count(n: int, break_symmetry: bool) -> int:
    model = cp_model.CpModel()
    queens = [model.new_int_var(0, n - 1, f"q_{i}") for i in range(n)]
    model.add_all_different(queens)
    model.add_all_different([queens[i] + i for i in range(n)])
    model.add_all_different([queens[i] - i for i in range(n)])
    if break_symmetry:
        model.add(queens[0] < queens[n - 1])

    solver = cp_model.CpSolver()
    solver.parameters.enumerate_all_solutions = True
    solver.parameters.random_seed = 42

    class Counter(cp_model.CpSolverSolutionCallback):
        def __init__(self) -> None:
            super().__init__()
            self.count = 0

        def on_solution_callback(self) -> None:
            self.count += 1

    counter = Counter()
    solver.solve(model, counter)
    return counter.count


def compare(n: int = 8) -> CountPair:
    """Return counts with and without ``q[0] < q[n-1]`` symmetry-breaker."""
    return CountPair(
        without_breaker=_count(n, break_symmetry=False),
        with_breaker=_count(n, break_symmetry=True),
    )


def main() -> None:
    pair = compare(n=8)
    print(f"n=8 without breaker: {pair.without_breaker}")
    print(f"n=8 with breaker:    {pair.with_breaker}")
    print(f"reduction ratio:     {pair.without_breaker / max(pair.with_breaker, 1):.3f}")


if __name__ == "__main__":
    main()
