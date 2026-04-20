"""Exercise 4.2 — TWO + TWO = FOUR enumeration.

Letters: T, W, O, F, U, R (6 distinct letters, so a solution exists).
Leading zeros forbidden on T and F. Find all solutions.
"""

from __future__ import annotations

from ortools.sat.python import cp_model

_LETTERS = ("T", "W", "O", "F", "U", "R")


def enumerate_two_two_four() -> list[dict[str, int]]:
    """Return every ``(T, W, O, F, U, R)`` assignment satisfying TWO+TWO=FOUR."""
    model = cp_model.CpModel()
    v = {letter: model.new_int_var(0, 9, letter) for letter in _LETTERS}
    model.add_all_different(list(v.values()))
    model.add(v["T"] >= 1)
    model.add(v["F"] >= 1)

    two = 100 * v["T"] + 10 * v["W"] + v["O"]
    four = 1000 * v["F"] + 100 * v["O"] + 10 * v["U"] + v["R"]
    model.add(2 * two == four)

    solver = cp_model.CpSolver()
    solver.parameters.enumerate_all_solutions = True
    solver.parameters.random_seed = 42

    solutions: list[dict[str, int]] = []

    class Collector(cp_model.CpSolverSolutionCallback):
        def __init__(self) -> None:
            super().__init__()

        def on_solution_callback(self) -> None:
            solutions.append({k: int(self.value(var)) for k, var in v.items()})

    solver.solve(model, Collector())
    return solutions


def main() -> None:
    solutions = enumerate_two_two_four()
    print(f"TWO + TWO = FOUR: {len(solutions)} distinct solutions")
    for i, sol in enumerate(solutions[:5]):
        two = 100 * sol["T"] + 10 * sol["W"] + sol["O"]
        four = 1000 * sol["F"] + 100 * sol["O"] + 10 * sol["U"] + sol["R"]
        print(f"  [{i}] {two} + {two} = {four}  {sol}")
    if len(solutions) > 5:
        print(f"  ... and {len(solutions) - 5} more")


if __name__ == "__main__":
    main()
