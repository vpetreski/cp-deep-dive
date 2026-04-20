"""Tests for the ``add_circuit`` TSP demo."""

from __future__ import annotations

import pytest
from py_cp_sat_ch06.tsp import DEMO_DISTANCES, solve_tsp


def _tour_is_valid(tour: list[int], n: int) -> bool:
    if len(tour) != n + 1:
        return False
    if tour[0] != 0 or tour[-1] != 0:
        return False
    return sorted(tour[:-1]) == list(range(n))


def test_demo_returns_valid_tour() -> None:
    result = solve_tsp()
    assert result.status == "OPTIMAL"
    assert _tour_is_valid(result.tour, n=len(DEMO_DISTANCES))


def test_demo_tour_length_matches_arcs() -> None:
    result = solve_tsp()
    n = len(DEMO_DISTANCES)
    recomputed = sum(
        DEMO_DISTANCES[result.tour[i]][result.tour[i + 1]] for i in range(n)
    )
    assert recomputed == result.length


def test_demo_tour_length_is_known_optimum() -> None:
    # Pinning the optimum guards against regressions / solver changes.
    # Computed once via solve_tsp() on this 8-city instance.
    result = solve_tsp()
    assert result.length == 235


def test_rejects_non_square_matrix() -> None:
    with pytest.raises(ValueError):
        solve_tsp([[0, 1], [2, 3, 4]])
