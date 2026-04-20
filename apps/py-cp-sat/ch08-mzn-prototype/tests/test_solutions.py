"""Tests for Chapter 08 exercise solutions."""

from __future__ import annotations

from py_cp_sat_ch08.solutions.exercise_8_1_larger_instance import solve_larger_instance
from py_cp_sat_ch08.solutions.exercise_8_2_tight_workload import solve_infeasible


def test_larger_instance_solves() -> None:
    r = solve_larger_instance()
    assert r.status in {"OPTIMAL", "FEASIBLE"}
    assert r.spread is not None
    # 5 nurses.
    assert len(r.totals) == 5


def test_tight_workload_is_infeasible() -> None:
    r = solve_infeasible()
    assert r.status == "INFEASIBLE"
