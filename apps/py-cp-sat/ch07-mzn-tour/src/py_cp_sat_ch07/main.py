"""Chapter 07 — MiniZinc tour.

Runs the shared MiniZinc models against whatever solver ``minizinc`` picks by
default, then compares the optimum / assignment against the Python CP-SAT
twin in earlier chapters.

If ``minizinc`` is not installed on this machine the script prints a short
banner and exits cleanly — the chapter can be explored later once MiniZinc
is in place.
"""

from __future__ import annotations

from dataclasses import dataclass

from py_cp_sat_ch04.send_more_money import solve_send_more_money
from py_cp_sat_ch05.knapsack import DEMO_CAPACITY, DEMO_ITEMS, solve_knapsack

from py_cp_sat_ch07.comparison import parse_knapsack_value, parse_sendmore
from py_cp_sat_ch07.mzn_runner import (
    MinizincNotInstalled,
    minizinc_available,
    run_model,
)


@dataclass(frozen=True)
class ChapterDemo:
    """Per-model parity status — ``None`` when MiniZinc is not installed."""

    minizinc_available: bool
    knapsack_parity: bool | None
    sendmore_parity: bool | None


def solve() -> ChapterDemo:
    """Run every available model; return parity booleans or ``None`` if skipped."""
    if not minizinc_available():
        return ChapterDemo(
            minizinc_available=False, knapsack_parity=None, sendmore_parity=None
        )

    # Knapsack.
    run = run_model("knapsack")
    mzn_value = parse_knapsack_value(run.stdout)
    py_value = solve_knapsack(list(DEMO_ITEMS), DEMO_CAPACITY).value
    knapsack_parity = mzn_value == py_value

    # SEND+MORE=MONEY.
    run = run_model("sendmore")
    mzn_digits = parse_sendmore(run.stdout)
    py_digits = solve_send_more_money().assignment
    sendmore_parity = mzn_digits.digits == py_digits

    return ChapterDemo(
        minizinc_available=True,
        knapsack_parity=knapsack_parity,
        sendmore_parity=sendmore_parity,
    )


def main() -> None:
    demo = solve()

    if not demo.minizinc_available:
        print("=== MiniZinc not installed ===")
        print("Install MiniZinc from https://www.minizinc.org/software.html and retry.")
        print("This chapter is designed to be re-run once the binary is available.")
        return

    print("=== Chapter 07 — MiniZinc tour ===")
    try:
        nqueens = run_model("nqueens")
        print("--- nqueens (n=8) ---")
        print(nqueens.stdout.strip())
        print()
    except MinizincNotInstalled:
        pass

    print(f"knapsack parity (MZN vs CP-SAT): {demo.knapsack_parity}")
    print(f"sendmore parity (MZN vs CP-SAT): {demo.sendmore_parity}")


if __name__ == "__main__":
    main()
