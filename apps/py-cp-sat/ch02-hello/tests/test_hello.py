"""Tests for Chapter 2's hello model.

The system has a unique optimal objective:
  3x + 2y = 12, x + y <= 5, x,y in [0,10], maximize x + y.
The only integer pair satisfying `3x + 2y = 12` with `x + y <= 5` is (x=2, y=3),
which gives objective = 5. No higher objective is feasible.
"""

from __future__ import annotations

from py_cp_sat_ch02.main import solve


def test_solver_returns_optimal() -> None:
    sol = solve()
    assert sol.status == "OPTIMAL"


def test_solution_satisfies_constraints() -> None:
    sol = solve()
    assert 3 * sol.x + 2 * sol.y == 12
    assert sol.x + sol.y <= 5
    assert 0 <= sol.x <= 10
    assert 0 <= sol.y <= 10


def test_objective_value() -> None:
    sol = solve()
    assert sol.objective == 5
    assert sol.x + sol.y == sol.objective


def test_unique_integer_witness() -> None:
    """The optimum is witnessed by x=2, y=3 — verify we produce it."""
    sol = solve()
    assert sol.x == 2
    assert sol.y == 3
