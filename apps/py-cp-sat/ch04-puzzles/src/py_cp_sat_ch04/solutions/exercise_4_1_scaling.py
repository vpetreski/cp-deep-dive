"""Exercise 4.1 — N=100/200 queens timings.

Measures wall-clock for first-solution solves at a few N values.
"""

from __future__ import annotations

import time
from dataclasses import dataclass

from py_cp_sat_ch04.n_queens import solve_n_queens


@dataclass(frozen=True)
class ScalingRow:
    n: int
    elapsed_seconds: float
    status: str


def measure_scaling(sizes: list[int]) -> list[ScalingRow]:
    """For each N in ``sizes`` find one N-Queens solution and report time."""
    rows: list[ScalingRow] = []
    for n in sizes:
        t0 = time.perf_counter()
        result = solve_n_queens(n=n)
        elapsed = time.perf_counter() - t0
        rows.append(ScalingRow(n=n, elapsed_seconds=elapsed, status=result.status))
    return rows


def main() -> None:
    rows = measure_scaling([8, 20, 50, 100])
    print(f"{'n':>4} | {'status':<10} | {'elapsed_s':>10}")
    for row in rows:
        print(f"{row.n:>4} | {row.status:<10} | {row.elapsed_seconds:>10.4f}")


if __name__ == "__main__":
    main()
