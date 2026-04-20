"""Tests for the Sudoku solver."""

from __future__ import annotations

import pytest
from py_cp_sat_ch04.main import DEMO_SUDOKU
from py_cp_sat_ch04.sudoku import solve_sudoku


def test_solves_classic_puzzle() -> None:
    result = solve_sudoku(DEMO_SUDOKU)
    assert result.status in {"OPTIMAL", "FEASIBLE"}

    grid = result.grid
    assert len(grid) == 9
    for row in grid:
        assert len(row) == 9
        assert sorted(row) == list(range(1, 10))

    # Columns
    for col in range(9):
        column = [grid[r][col] for r in range(9)]
        assert sorted(column) == list(range(1, 10))

    # 3x3 blocks
    for br in range(3):
        for bc in range(3):
            block = [
                grid[br * 3 + dr][bc * 3 + dc] for dr in range(3) for dc in range(3)
            ]
            assert sorted(block) == list(range(1, 10))


def test_respects_given_digits() -> None:
    result = solve_sudoku(DEMO_SUDOKU)
    for r in range(9):
        for c in range(9):
            if DEMO_SUDOKU[r][c] != 0:
                assert result.grid[r][c] == DEMO_SUDOKU[r][c]


def test_infeasible_puzzle_returns_empty_grid() -> None:
    # Two 5s in row 0 — infeasible by construction.
    bad = [[0] * 9 for _ in range(9)]
    bad[0][0] = 5
    bad[0][1] = 5
    result = solve_sudoku(bad)
    assert result.status == "INFEASIBLE"
    assert result.grid == []


def test_invalid_shape_raises() -> None:
    with pytest.raises(ValueError):
        solve_sudoku([[0] * 9 for _ in range(8)])
    with pytest.raises(ValueError):
        bad = [[0] * 9 for _ in range(9)]
        bad[0] = [0] * 8
        solve_sudoku(bad)
    with pytest.raises(ValueError):
        bad = [[0] * 9 for _ in range(9)]
        bad[0][0] = 99
        solve_sudoku(bad)
