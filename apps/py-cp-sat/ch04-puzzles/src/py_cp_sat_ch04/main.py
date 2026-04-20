"""Chapter 04 — Classic puzzles: N-Queens, SEND+MORE=MONEY, Sudoku.

Each puzzle lives in its own submodule; ``solve()`` here orchestrates a demo
run of all three so ``python -m py_cp_sat_ch04`` prints something useful.
"""

from __future__ import annotations

from dataclasses import dataclass, field

from py_cp_sat_ch04.n_queens import (
    NQueensResult,
    count_n_queens_solutions,
    solve_n_queens,
)
from py_cp_sat_ch04.send_more_money import SendMoreMoneyResult, solve_send_more_money
from py_cp_sat_ch04.sudoku import SudokuResult, solve_sudoku


@dataclass(frozen=True)
class ChapterDemo:
    """Aggregate of all three puzzle results for a single demo run."""

    n_queens: NQueensResult
    n_queens_count_n8: int
    send_more: SendMoreMoneyResult
    sudoku: SudokuResult
    unsolved_sudoku: list[list[int]] = field(default_factory=list)


# A classic puzzle (5-starred in most newspapers).
DEMO_SUDOKU: list[list[int]] = [
    [5, 3, 0, 0, 7, 0, 0, 0, 0],
    [6, 0, 0, 1, 9, 5, 0, 0, 0],
    [0, 9, 8, 0, 0, 0, 0, 6, 0],
    [8, 0, 0, 0, 6, 0, 0, 0, 3],
    [4, 0, 0, 8, 0, 3, 0, 0, 1],
    [7, 0, 0, 0, 2, 0, 0, 0, 6],
    [0, 6, 0, 0, 0, 0, 2, 8, 0],
    [0, 0, 0, 4, 1, 9, 0, 0, 5],
    [0, 0, 0, 0, 8, 0, 0, 7, 9],
]


def solve() -> ChapterDemo:
    """Run all three classic puzzles and return their solutions.

    Used by the CLI and tests alike. Deterministic — uses fixed seeds so the
    same inputs always yield the same outputs.
    """
    n_queens = solve_n_queens(n=8)
    n_queens_count = count_n_queens_solutions(n=8)
    send_more = solve_send_more_money()
    sudoku = solve_sudoku(DEMO_SUDOKU)
    return ChapterDemo(
        n_queens=n_queens,
        n_queens_count_n8=n_queens_count,
        send_more=send_more,
        sudoku=sudoku,
        unsolved_sudoku=[row[:] for row in DEMO_SUDOKU],
    )


def _render_board(positions: list[int]) -> str:
    n = len(positions)
    rows = []
    for row in range(n):
        cells = ["Q" if positions[col] == row else "." for col in range(n)]
        rows.append(" ".join(cells))
    return "\n".join(rows)


def _render_sudoku(grid: list[list[int]]) -> str:
    lines = []
    for i, row in enumerate(grid):
        if i % 3 == 0 and i > 0:
            lines.append("-" * 21)
        cells = []
        for j, v in enumerate(row):
            if j % 3 == 0 and j > 0:
                cells.append("|")
            cells.append(str(v))
        lines.append(" ".join(cells))
    return "\n".join(lines)


def main() -> None:
    """CLI entrypoint — print the three demos."""
    demo = solve()

    print("=== N-Queens (n=8) ===")
    print(f"Status: {demo.n_queens.status}")
    print(f"Queen columns -> rows: {demo.n_queens.positions}")
    print(_render_board(demo.n_queens.positions))
    print(f"Total distinct solutions: {demo.n_queens_count_n8}")

    print()
    print("=== SEND + MORE = MONEY ===")
    print(f"Status: {demo.send_more.status}")
    for letter, digit in demo.send_more.assignment.items():
        print(f"  {letter} = {digit}")
    send = demo.send_more.assignment
    n1 = 1000 * send["S"] + 100 * send["E"] + 10 * send["N"] + send["D"]
    n2 = 1000 * send["M"] + 100 * send["O"] + 10 * send["R"] + send["E"]
    tot = (
        10000 * send["M"]
        + 1000 * send["O"]
        + 100 * send["N"]
        + 10 * send["E"]
        + send["Y"]
    )
    print(f"  {n1} + {n2} = {tot}")

    print()
    print("=== Sudoku ===")
    print(f"Status: {demo.sudoku.status}")
    print(_render_sudoku(demo.sudoku.grid))


if __name__ == "__main__":
    main()
