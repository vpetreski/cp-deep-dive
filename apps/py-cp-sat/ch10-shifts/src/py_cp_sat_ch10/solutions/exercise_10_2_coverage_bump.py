"""Exercise 10.2 — Raise coverage_min from 1 to 2 and re-solve.

Demonstrates that the base model's coverage parameter is a lever: with 5
nurses and a 5-shift cap each we have 25 person-shifts worth of labour, which
is enough to cover 2 nurses per shift for 7 days (2 * 2 * 7 = 28 — not quite
enough, actually — so the test also verifies infeasibility kicks in above a
threshold).
"""

from __future__ import annotations

from dataclasses import replace

from py_cp_sat_ch10.shifts import ShiftInstance, ShiftSolution, solve_shifts


def solve_with_coverage(coverage_min: int) -> ShiftSolution:
    """Solve the standard instance with an overridden coverage floor."""
    return solve_shifts(replace(ShiftInstance(), coverage_min=coverage_min))
