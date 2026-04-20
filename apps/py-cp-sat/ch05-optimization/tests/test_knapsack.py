"""Tests for 0/1 knapsack solver."""

from __future__ import annotations

from py_cp_sat_ch05.knapsack import (
    DEMO_CAPACITY,
    DEMO_ITEMS,
    enumerate_optimal_subsets,
    solve_knapsack,
)


def test_demo_instance_has_known_optimum() -> None:
    # Computed once by hand via solve_knapsack — pinning it guards against
    # solver regressions (the value is stable for this deterministic instance).
    result = solve_knapsack(list(DEMO_ITEMS), DEMO_CAPACITY)
    assert result.status == "OPTIMAL"
    assert result.value == 53


def test_chosen_subset_respects_capacity() -> None:
    result = solve_knapsack(list(DEMO_ITEMS), DEMO_CAPACITY)
    by_name = {it.name: it for it in DEMO_ITEMS}
    total_weight = sum(by_name[n].weight for n in result.chosen)
    total_value = sum(by_name[n].value for n in result.chosen)
    assert total_weight <= DEMO_CAPACITY
    assert total_value == result.value


def test_trace_is_monotonic_for_single_worker() -> None:
    # With num_workers=1 the incumbent must be monotonically improving — each
    # callback fires only when the solver found a *better* incumbent.
    result = solve_knapsack(list(DEMO_ITEMS), DEMO_CAPACITY, num_workers=1)
    assert len(result.trace) >= 1
    for prev, curr in zip(result.trace, result.trace[1:], strict=False):
        # We're maximizing, so each new incumbent must be ≥ the previous.
        assert curr.objective >= prev.objective


def test_negative_capacity_rejected() -> None:
    import pytest

    with pytest.raises(ValueError):
        solve_knapsack(list(DEMO_ITEMS), -1)


def test_enumerate_optimal_subsets_all_match_optimum() -> None:
    optimum = solve_knapsack(list(DEMO_ITEMS), DEMO_CAPACITY)
    assert optimum.value is not None

    subsets = enumerate_optimal_subsets(list(DEMO_ITEMS), DEMO_CAPACITY)
    assert len(subsets) >= 1

    by_name = {it.name: it for it in DEMO_ITEMS}
    for names in subsets:
        total_weight = sum(by_name[n].weight for n in names)
        total_value = sum(by_name[n].value for n in names)
        assert total_weight <= DEMO_CAPACITY
        assert total_value == optimum.value
