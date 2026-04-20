"""Exercise 7.1 — Run the same model with different MiniZinc solvers.

MiniZinc exposes a portfolio of backends (Gecode, Chuffed, CP-SAT via
``org.minizinc.mip.cbc``). Running the same `.mzn` against several solvers is
a valuable apples-to-apples comparison.

Skips gracefully when either ``minizinc`` or the specific requested solver
isn't installed on this machine.
"""

from __future__ import annotations

from dataclasses import dataclass

from py_cp_sat_ch07.comparison import parse_knapsack_value
from py_cp_sat_ch07.mzn_runner import MinizincNotInstalled, run_model


@dataclass(frozen=True)
class SolverRun:
    solver: str
    value: int | None  # None when the solver rejected the model


def run_knapsack_across_solvers(solvers: list[str]) -> list[SolverRun]:
    results: list[SolverRun] = []
    for solver in solvers:
        try:
            run = run_model("knapsack", solver=solver)
        except MinizincNotInstalled:
            raise
        if run.returncode != 0:
            results.append(SolverRun(solver=solver, value=None))
            continue
        try:
            value = parse_knapsack_value(run.stdout)
        except ValueError:
            results.append(SolverRun(solver=solver, value=None))
            continue
        results.append(SolverRun(solver=solver, value=value))
    return results


def main() -> None:
    results = run_knapsack_across_solvers(["gecode", "cp-sat"])
    for r in results:
        print(f"{r.solver:>10}: value = {r.value}")


if __name__ == "__main__":
    main()
