"""Exercise 10.4 — Prove that demanding too much coverage becomes infeasible.

With 5 nurses, 7 days, 2 working shifts per day, and a 5-shifts-per-nurse cap,
the total labour available is 5 * 5 = 25 person-shifts. Coverage_min = 2
demands 2 * 2 * 7 = 28 person-shifts — three short. CP-SAT should return
INFEASIBLE.
"""

from __future__ import annotations

from dataclasses import replace

from py_cp_sat_ch10.shifts import ShiftInstance, solve_shifts


def solve_infeasible_instance() -> str:
    """Return the solver status for a deliberately over-constrained instance."""
    instance = replace(ShiftInstance(), coverage_min=2)
    return solve_shifts(instance).status
