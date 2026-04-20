"""Tests for N-Queens.

The count for n=8 is 92 — a classic result used as a smoke test for N-Queens
solvers since the 1800s.
"""

from __future__ import annotations

import pytest
from py_cp_sat_ch04.n_queens import count_n_queens_solutions, solve_n_queens


@pytest.mark.parametrize("n", [4, 5, 6, 8])
def test_n_queens_finds_valid_solution(n: int) -> None:
    result = solve_n_queens(n=n)
    assert result.status in {"OPTIMAL", "FEASIBLE"}
    assert result.n == n
    assert len(result.positions) == n

    rows = result.positions
    # rows distinct
    assert len(set(rows)) == n
    # diagonals distinct
    assert len({rows[i] + i for i in range(n)}) == n
    assert len({rows[i] - i for i in range(n)}) == n


def test_n_queens_count_for_n8_equals_92() -> None:
    assert count_n_queens_solutions(n=8) == 92


def test_n_queens_count_for_n6_equals_4() -> None:
    # n=6 has 4 distinct solutions — a useful quick-check number.
    assert count_n_queens_solutions(n=6) == 4


def test_n_queens_n2_is_infeasible() -> None:
    # No solution on a 2x2 or 3x3 board.
    result = solve_n_queens(n=2)
    assert result.status == "INFEASIBLE"
    assert result.positions == []
