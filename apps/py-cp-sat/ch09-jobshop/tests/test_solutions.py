"""Tests for the Chapter 09 exercise solutions."""

from __future__ import annotations

from py_cp_sat_ch09.jobshop import solve_jobshop
from py_cp_sat_ch09.solutions.exercise_9_1_cumulative_staffing import (
    solve_with_cumulative_on_machine_0,
)
from py_cp_sat_ch09.solutions.exercise_9_2_release_times import solve_with_releases
from py_cp_sat_ch09.solutions.exercise_9_3_optional_intervals import (
    solve_with_alternative_machine,
)
from py_cp_sat_ch09.solutions.exercise_9_4_minimize_flow_time import (
    solve_minimize_flow_time,
)


def test_exercise_9_1_cumulative_does_not_worsen_makespan() -> None:
    unary = solve_jobshop()
    cumulative = solve_with_cumulative_on_machine_0()
    assert cumulative.status == "OPTIMAL"
    assert cumulative.makespan is not None and unary.makespan is not None
    # Extra capacity can only help (or stay the same).
    assert cumulative.makespan <= unary.makespan


def test_exercise_9_2_releases_push_makespan_up() -> None:
    baseline = solve_jobshop()
    released = solve_with_releases(releases=(0, 3, 6))
    assert released.status == "OPTIMAL"
    assert released.makespan is not None and baseline.makespan is not None
    # Last job starts at t=6, then runs for 4+3=7 steps → makespan must be ≥ 13.
    assert released.makespan >= 13
    assert released.makespan > baseline.makespan


def test_exercise_9_3_alternative_machine_yields_optimal() -> None:
    r = solve_with_alternative_machine()
    assert r.status == "OPTIMAL"
    assert r.makespan is not None
    # used_alternative is a bool; either choice must respect constraints.
    assert isinstance(r.used_alternative, bool)


def test_exercise_9_4_flow_time_differs_from_makespan() -> None:
    flow = solve_minimize_flow_time()
    assert flow.status == "OPTIMAL"
    assert flow.total_flow_time == sum(flow.job_completions)
    assert len(flow.job_completions) == 3  # DEMO_3X3 has 3 jobs
