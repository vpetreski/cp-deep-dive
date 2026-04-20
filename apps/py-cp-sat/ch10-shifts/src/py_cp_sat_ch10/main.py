"""Chapter 10 — Shift grid with forbidden transitions.

Solves the 5-nurse x 7-day x {OFF, DAY, NIGHT} demo instance, then prints the
schedule in ASCII and the per-nurse totals.
"""

from __future__ import annotations

from dataclasses import dataclass

from py_cp_sat_ch10.shifts import (
    ShiftInstance,
    ShiftSolution,
    solve_shifts,
    verify_no_day_after_night,
)


@dataclass(frozen=True)
class ChapterDemo:
    solution: ShiftSolution


def solve() -> ChapterDemo:
    solution = solve_shifts(ShiftInstance())
    assert verify_no_day_after_night(solution), "DAY-after-NIGHT violation leaked"
    return ChapterDemo(solution=solution)


def main() -> None:
    demo = solve()
    print("=== Shifts (5 nurses x 7 days x {OFF, DAY, NIGHT}) ===")
    print(f"Status: {demo.solution.status}")
    print()
    print(demo.solution.render())
    print()
    print(f"Totals (DAY + NIGHT per nurse): {list(demo.solution.totals)}")


if __name__ == "__main__":
    main()
