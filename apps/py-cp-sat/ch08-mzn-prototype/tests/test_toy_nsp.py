"""Tests for the Python toy NSP solver."""

from __future__ import annotations

import pytest
from py_cp_sat_ch08.toy_nsp import DEMO_INSTANCE, ToyNspInstance, solve_toy_nsp


def test_demo_instance_solves_optimally() -> None:
    result = solve_toy_nsp(DEMO_INSTANCE)
    assert result.status == "OPTIMAL"
    assert result.spread is not None
    assert result.spread >= 0
    # Totals must match: 3 nurses × something.
    assert len(result.totals) == DEMO_INSTANCE.n_nurses


def test_schedule_covers_every_shift_cell_exactly_once() -> None:
    result = solve_toy_nsp(DEMO_INSTANCE)
    assert result.status == "OPTIMAL"
    # For each (day, shift) exactly one nurse.
    n_nurses = DEMO_INSTANCE.n_nurses
    n_days = DEMO_INSTANCE.n_days
    n_shifts = DEMO_INSTANCE.n_shifts
    for d in range(n_days):
        for s in range(n_shifts):
            covered = sum(1 for n in range(n_nurses) if result.schedule[n][d] == s)
            assert covered == 1, f"day {d} shift {s} covered {covered} times"


def test_workload_bounds_respected() -> None:
    result = solve_toy_nsp(DEMO_INSTANCE)
    assert result.status == "OPTIMAL"
    for total in result.totals:
        assert DEMO_INSTANCE.min_work <= total <= DEMO_INSTANCE.max_work


def test_at_most_one_shift_per_nurse_per_day() -> None:
    result = solve_toy_nsp(DEMO_INSTANCE)
    for row in result.schedule:
        # row[d] is the assigned shift or -1; already only one slot per day.
        assert len(row) == DEMO_INSTANCE.n_days


def test_infeasible_when_workload_too_low() -> None:
    instance = ToyNspInstance(
        n_nurses=3, n_days=7, n_shifts=2, max_work=4, min_work=2
    )
    result = solve_toy_nsp(instance)
    assert result.status == "INFEASIBLE"
    assert result.spread is None
    assert result.schedule == []


def test_rejects_min_above_max() -> None:
    with pytest.raises(ValueError):
        solve_toy_nsp(
            ToyNspInstance(n_nurses=2, n_days=2, n_shifts=1, max_work=1, min_work=5)
        )
