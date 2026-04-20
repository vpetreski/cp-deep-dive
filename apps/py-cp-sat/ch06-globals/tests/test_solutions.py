"""Tests for Chapter 06 exercise solutions."""

from __future__ import annotations

from py_cp_sat_ch06.solutions.exercise_6_1_mtz_tsp import compare_tsp_formulations
from py_cp_sat_ch06.solutions.exercise_6_2_two_consecutive_nights import (
    longest_night_run,
    solve_max_two_consecutive,
)
from py_cp_sat_ch06.solutions.exercise_6_4_element_variable_values import (
    solve_variable_element,
)
from py_cp_sat_ch06.solutions.exercise_6_5_lex_breaks_symmetry import measure_lex_ratio


def test_exercise_6_1_circuit_and_mtz_agree() -> None:
    cmp = compare_tsp_formulations()
    assert cmp.circuit_length == cmp.mtz_length


def test_exercise_6_2_caps_nights_at_two() -> None:
    result = solve_max_two_consecutive()
    assert result.status in {"OPTIMAL", "FEASIBLE"}
    assert longest_night_run(result.schedule) <= 2


def test_exercise_6_4_variable_element_minimizes_target() -> None:
    result = solve_variable_element(n=7, total=200)
    assert result.status == "OPTIMAL"
    assert sum(result.costs) == 200
    assert result.costs[result.index] == result.target
    # Target should be 0 since sum constraint can concentrate cost elsewhere.
    assert result.target == 0


def test_exercise_6_5_lex_ratio_caps_at_factorial() -> None:
    ratio = measure_lex_ratio(n_nurses=3, days=4, min_work=2)
    # Ratio must be strictly > 1 (symmetry existed) and ≤ n_nurses!
    assert ratio.without_lex > ratio.with_lex > 0
    assert ratio.ratio <= ratio.expected_ratio + 1e-6
