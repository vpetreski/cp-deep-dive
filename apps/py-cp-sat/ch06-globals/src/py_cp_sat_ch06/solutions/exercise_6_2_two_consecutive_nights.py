"""Exercise 6.2 — Automaton for ≤ 2 consecutive nights.

Drop the ``(2, N, 3)`` transition from the ch06 demo DFA: now no sequence with
three or more consecutive ``N`` labels is accepted.
"""

from __future__ import annotations

from py_cp_sat_ch06.automaton import (
    DAYS,
    LABEL_NIGHT,
    AutomatonResult,
    solve_night_pattern,
)


def solve_max_two_consecutive(days: int = DAYS) -> AutomatonResult:
    return solve_night_pattern(days=days, max_consecutive=2, min_total_nights=3)


def longest_night_run(schedule: list[int]) -> int:
    """Return the longest streak of consecutive ``N`` labels in ``schedule``."""
    best = 0
    cur = 0
    for label in schedule:
        if label == LABEL_NIGHT:
            cur += 1
            best = max(best, cur)
        else:
            cur = 0
    return best


def main() -> None:
    result = solve_max_two_consecutive()
    print(f"Status:   {result.status}")
    print(f"Schedule: {result.schedule}")
    print(f"Longest night run: {longest_night_run(result.schedule)} (should be ≤ 2)")


if __name__ == "__main__":
    main()
