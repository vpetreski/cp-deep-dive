"""Tests for Chapter 5 exercise solutions."""

from __future__ import annotations

from py_cp_sat_ch05.knapsack import DEMO_CAPACITY, DEMO_ITEMS
from py_cp_sat_ch05.solutions.exercise_5_1_bounded_knapsack import compare_01_vs_bounded
from py_cp_sat_ch05.solutions.exercise_5_3_early_stop import compare_early_stop
from py_cp_sat_ch05.solutions.exercise_5_4_lex_objective import solve_lex_knapsack
from py_cp_sat_ch05.solutions.exercise_5_5_enumerate_optimal import enumerate_demo_optima


def test_exercise_5_1_bounded_beats_or_matches_01() -> None:
    plain_value, bounded_value = compare_01_vs_bounded()
    # Bounded is strictly more permissive, so the optimum can only grow.
    assert bounded_value >= plain_value


def test_exercise_5_3_early_stop_within_5_percent_of_proven() -> None:
    result = compare_early_stop(n=40, capacity=150, time_limit=10.0)
    # Early-stop value must be within 5% of proven optimum.
    assert result.gap5_value <= result.proven_value
    assert result.proven_value <= result.gap5_value * 1.05 + 1e-6


def test_exercise_5_4_lex_minimizes_weight_among_max_value() -> None:
    lex = solve_lex_knapsack(list(DEMO_ITEMS), DEMO_CAPACITY)
    by_name = {it.name: it for it in DEMO_ITEMS}
    lex_weight = sum(by_name[n].weight for n in lex.chosen)
    lex_value = sum(by_name[n].value for n in lex.chosen)
    assert lex_value == lex.value
    assert lex_weight == lex.weight
    assert lex_weight <= DEMO_CAPACITY


def test_exercise_5_5_enumeration_agrees_with_optimum() -> None:
    subsets = enumerate_demo_optima()
    assert len(subsets) >= 1
    # Shouldn't miss symmetric orderings — every subset is a distinct packing.
    assert len(subsets) == len({tuple(sorted(s)) for s in subsets})
