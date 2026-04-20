"""Tests for the Chapter 10 exercise solutions."""

from __future__ import annotations

from py_cp_sat_ch10.solutions.exercise_10_1_rest_after_night import (
    solve_with_strict_rest,
    violates_strict_rest,
)
from py_cp_sat_ch10.solutions.exercise_10_2_coverage_bump import solve_with_coverage
from py_cp_sat_ch10.solutions.exercise_10_3_weekend_off import (
    everyone_has_weekend_off,
    solve_with_weekend_off,
)
from py_cp_sat_ch10.solutions.exercise_10_4_infeasible import solve_infeasible_instance


def test_exercise_10_1_strict_rest_holds() -> None:
    r = solve_with_strict_rest()
    assert r.status in {"OPTIMAL", "FEASIBLE"}
    assert not violates_strict_rest(r.schedule)


def test_exercise_10_2_coverage_1_feasible() -> None:
    r = solve_with_coverage(1)
    assert r.status in {"OPTIMAL", "FEASIBLE"}


def test_exercise_10_3_weekend_off_respected() -> None:
    r = solve_with_weekend_off()
    assert r.status in {"OPTIMAL", "FEASIBLE"}
    assert everyone_has_weekend_off(r.schedule)


def test_exercise_10_4_coverage_2_infeasible() -> None:
    # 5 nurses * 5-shift cap = 25 person-shifts < 2 * 2 * 7 = 28 demanded.
    assert solve_infeasible_instance() == "INFEASIBLE"
