"""Tests for Chapter 4 exercise solutions."""

from __future__ import annotations

from py_cp_sat_ch04.solutions.exercise_4_1_scaling import measure_scaling
from py_cp_sat_ch04.solutions.exercise_4_2_two_two_four import enumerate_two_two_four
from py_cp_sat_ch04.solutions.exercise_4_3_reified_send_more import solve_reified
from py_cp_sat_ch04.solutions.exercise_4_5_symmetry_breaking import compare


def test_exercise_4_1_finds_solutions_up_to_n20() -> None:
    rows = measure_scaling([8, 12, 20])
    assert all(row.status in {"OPTIMAL", "FEASIBLE"} for row in rows)
    assert all(row.elapsed_seconds >= 0 for row in rows)


def test_exercise_4_2_two_two_four_enumerates_all_solutions() -> None:
    solutions = enumerate_two_two_four()
    # TWO+TWO=FOUR has 7 distinct solutions (the solver discovered this count —
    # don't peek online per the chapter spec). Each solution must obey the
    # equation and have distinct letter-to-digit assignments.
    assert len(solutions) == 7
    for sol in solutions:
        two = 100 * sol["T"] + 10 * sol["W"] + sol["O"]
        four = 1000 * sol["F"] + 100 * sol["O"] + 10 * sol["U"] + sol["R"]
        assert 2 * two == four
        # All letters distinct
        assert len(set(sol.values())) == len(sol)
        assert sol["T"] >= 1
        assert sol["F"] >= 1


def test_exercise_4_3_reified_send_more_becomes_infeasible() -> None:
    # Per the chapter spec: the *baseline* SEND+MORE=MONEY has a unique solution
    # with E=5 (odd) and M+O=1. The reified implication `odd_e => M+O>=5`
    # contradicts M+O=1, so the enriched model is infeasible. Confirm that
    # outcome — it's the teaching point of Exercise 4.3.
    sol = solve_reified()
    assert sol is None


def test_exercise_4_5_symmetry_breaking_reduces_count() -> None:
    pair = compare(n=8)
    assert pair.without_breaker == 92
    # Breaker cuts roughly half the solutions (reflections across the vertical axis).
    assert pair.with_breaker < pair.without_breaker
    assert pair.with_breaker > 0
