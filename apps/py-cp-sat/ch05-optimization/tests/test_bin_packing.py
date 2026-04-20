"""Tests for bin packing."""

from __future__ import annotations

import pytest
from py_cp_sat_ch05.bin_packing import DEMO_CAPACITY, DEMO_WEIGHTS, solve_bin_packing


def test_demo_instance_fits_in_three_bins() -> None:
    total = sum(DEMO_WEIGHTS)
    # Lower bound from volume: ceil(total / capacity).
    lb = (total + DEMO_CAPACITY - 1) // DEMO_CAPACITY
    result = solve_bin_packing(list(DEMO_WEIGHTS), DEMO_CAPACITY)
    assert result.status == "OPTIMAL"
    assert result.num_bins is not None
    assert result.num_bins >= lb
    assert result.num_bins == 3  # Known optimum for this instance.


def test_each_item_assigned_exactly_once() -> None:
    result = solve_bin_packing(list(DEMO_WEIGHTS), DEMO_CAPACITY)
    assigned = [i for members in result.assignment for i in members]
    assert sorted(assigned) == list(range(len(DEMO_WEIGHTS)))


def test_no_bin_exceeds_capacity() -> None:
    result = solve_bin_packing(list(DEMO_WEIGHTS), DEMO_CAPACITY)
    for load in result.bin_loads:
        assert load <= DEMO_CAPACITY


def test_rejects_item_too_large_for_capacity() -> None:
    with pytest.raises(ValueError):
        solve_bin_packing([5, 12, 3], capacity=10)


def test_rejects_nonpositive_weight() -> None:
    with pytest.raises(ValueError):
        solve_bin_packing([3, 0, 2], capacity=10)
