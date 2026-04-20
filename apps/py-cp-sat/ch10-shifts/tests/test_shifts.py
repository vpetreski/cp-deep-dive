"""Tests for the base shift solver."""

from __future__ import annotations

from py_cp_sat_ch10.shifts import (
    DAY,
    NIGHT,
    OFF,
    ShiftInstance,
    solve_shifts,
    verify_no_day_after_night,
)


def test_base_instance_solves_and_respects_transitions() -> None:
    sol = solve_shifts(ShiftInstance())
    assert sol.status in {"OPTIMAL", "FEASIBLE"}
    assert len(sol.schedule) == 5
    for row in sol.schedule:
        assert len(row) == 7
        assert all(v in {OFF, DAY, NIGHT} for v in row)
    assert verify_no_day_after_night(sol)


def test_coverage_is_met() -> None:
    sol = solve_shifts(ShiftInstance())
    schedule = sol.schedule
    for d in range(7):
        day_workers = sum(1 for row in schedule if row[d] == DAY)
        night_workers = sum(1 for row in schedule if row[d] == NIGHT)
        assert day_workers >= 1
        assert night_workers >= 1


def test_max_workload_respected() -> None:
    sol = solve_shifts(ShiftInstance())
    for row in sol.schedule:
        worked = sum(1 for v in row if v != OFF)
        assert worked <= 5
