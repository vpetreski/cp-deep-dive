"""9x9 Sudoku solver via CP-SAT.

27 AllDifferent constraints — 9 rows, 9 columns, 9 blocks — plus the givens.
A standard puzzle with a unique solution, solvable in milliseconds.
"""

from __future__ import annotations

from dataclasses import dataclass, field

from ortools.sat.python import cp_model

_OK = (cp_model.OPTIMAL, cp_model.FEASIBLE)


@dataclass(frozen=True)
class SudokuResult:
    """Solved Sudoku grid. ``grid[row][col]`` is the digit 1-9."""

    status: str
    grid: list[list[int]] = field(default_factory=list)


def _validate_input(puzzle: list[list[int]]) -> None:
    if len(puzzle) != 9:
        raise ValueError(f"Sudoku puzzle must have 9 rows (got {len(puzzle)})")
    for r, row in enumerate(puzzle):
        if len(row) != 9:
            raise ValueError(f"Row {r} must have 9 cells (got {len(row)})")
        for c, value in enumerate(row):
            if not 0 <= value <= 9:
                raise ValueError(f"Cell ({r},{c}) = {value} not in 0..9")


def solve_sudoku(puzzle: list[list[int]]) -> SudokuResult:
    """Solve a 9x9 Sudoku. Zeros denote blank cells.

    Returns the filled grid, or an empty grid with a non-OK status if the
    puzzle is infeasible.
    """
    _validate_input(puzzle)

    model = cp_model.CpModel()
    cells = [
        [model.new_int_var(1, 9, f"cell_{r}_{c}") for c in range(9)] for r in range(9)
    ]

    # Respect the givens.
    for r in range(9):
        for c in range(9):
            if puzzle[r][c] != 0:
                model.add(cells[r][c] == puzzle[r][c])

    # Rows, columns, 3x3 blocks.
    for r in range(9):
        model.add_all_different(cells[r])
    for c in range(9):
        model.add_all_different([cells[r][c] for r in range(9)])
    for br in range(3):
        for bc in range(3):
            block = [
                cells[br * 3 + dr][bc * 3 + dc] for dr in range(3) for dc in range(3)
            ]
            model.add_all_different(block)

    solver = cp_model.CpSolver()
    solver.parameters.random_seed = 42
    status = solver.solve(model)
    if status not in _OK:
        return SudokuResult(status=solver.status_name(status))

    grid = [[int(solver.value(cells[r][c])) for c in range(9)] for r in range(9)]
    return SudokuResult(status=solver.status_name(status), grid=grid)
