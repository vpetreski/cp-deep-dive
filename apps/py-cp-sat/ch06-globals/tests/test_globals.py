"""Tests for the remaining Chapter 06 globals."""

from __future__ import annotations

from py_cp_sat_ch06.automaton import DAYS, LABEL_NIGHT, solve_night_pattern
from py_cp_sat_ch06.element import DEMO_COSTS, solve_min_cost_pick
from py_cp_sat_ch06.inverse import solve_simple_bijection
from py_cp_sat_ch06.lex_leq import count_schedules
from py_cp_sat_ch06.reservoir import solve_reservoir_schedule
from py_cp_sat_ch06.table_skills import DEMO_ALLOWED, solve_best_skill


def test_table_picks_highest_allowed_skill() -> None:
    result = solve_best_skill()
    assert result.status == "OPTIMAL"
    # Highest allowed skill level in DEMO_ALLOWED is 3.
    assert result.skill == 3
    assert (result.nurse, result.ward, result.skill) in DEMO_ALLOWED


def test_element_finds_minimum_cost() -> None:
    result = solve_min_cost_pick()
    assert result.status == "OPTIMAL"
    assert result.cost == min(DEMO_COSTS)
    assert DEMO_COSTS[result.index] == result.cost


def test_automaton_bounded_consecutive_nights() -> None:
    result = solve_night_pattern(days=DAYS, max_consecutive=3, min_total_nights=4)
    assert result.status in {"OPTIMAL", "FEASIBLE"}
    assert len(result.schedule) == DAYS
    # Longest run of nights must be ≤ 3.
    longest = cur = 0
    for label in result.schedule:
        if label == LABEL_NIGHT:
            cur += 1
            longest = max(longest, cur)
        else:
            cur = 0
    assert longest <= 3


def test_inverse_produces_mutual_permutations() -> None:
    result = solve_simple_bijection(n=5, pinned={0: 2})
    assert result.status in {"OPTIMAL", "FEASIBLE"}
    assert sorted(result.nurse_of_task) == list(range(5))
    assert sorted(result.task_of_nurse) == list(range(5))
    for t, n in enumerate(result.nurse_of_task):
        assert result.task_of_nurse[n] == t
    # Pinned constraint held.
    assert result.nurse_of_task[0] == 2


def test_lex_breaking_reduces_solution_count() -> None:
    without_lex = count_schedules(n_nurses=3, days=4, min_work=2, use_lex=False)
    with_lex = count_schedules(n_nurses=3, days=4, min_work=2, use_lex=True)
    assert without_lex > with_lex > 0
    # With 3 interchangeable nurses the ratio should be ≥ 1 and ≤ 3! = 6.
    assert 1 < (without_lex / with_lex) <= 6


def test_reservoir_respects_bounds() -> None:
    result = solve_reservoir_schedule()
    assert result.status in {"OPTIMAL", "FEASIBLE"}
    assert len(result.event_times) == 4
    # Event ordering constraint from the solver.
    assert result.event_times == sorted(result.event_times)
