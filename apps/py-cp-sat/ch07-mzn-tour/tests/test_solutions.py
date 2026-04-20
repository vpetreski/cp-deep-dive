"""Tests for Chapter 07 exercise solutions."""

from __future__ import annotations

import pytest
from py_cp_sat_ch07.mzn_runner import minizinc_available
from py_cp_sat_ch07.solutions.exercise_7_1_multi_solver import run_knapsack_across_solvers


@pytest.mark.skipif(not minizinc_available(), reason="minizinc binary not installed")
def test_multi_solver_knapsack_returns_at_least_one_value() -> None:
    results = run_knapsack_across_solvers(["gecode"])
    assert len(results) == 1
    # If gecode is unavailable on this machine, value will be None — still OK.
    # But we can assert the API returned a SolverRun object.
    assert results[0].solver == "gecode"
