"""Exercise 5.3 — Stop early at 5% gap.

Run a bigger knapsack (50 random items) twice:
    - Once to proven optimum (``relative_gap = 0``).
    - Once with ``relative_gap = 0.05`` (accept anything within 5%).

Compare wall time and final objective. In production, the 5% variant is often
good enough — the point of this exercise is to *measure* the tradeoff rather
than fear it.
"""

from __future__ import annotations

import random
import time
from dataclasses import dataclass

from py_cp_sat_ch05.knapsack import Item, solve_knapsack


@dataclass(frozen=True)
class EarlyStopComparison:
    """Elapsed time and objective for each setting."""

    proven_value: int
    proven_time: float
    gap5_value: int
    gap5_time: float

    @property
    def speedup(self) -> float:
        """``proven_time / gap5_time`` — expect ≥ 1.0 when the budget matters."""
        if self.gap5_time == 0:
            return float("inf")
        return self.proven_time / self.gap5_time


def random_items(n: int = 50, seed: int = 42) -> list[Item]:
    """Generate a reproducible random instance."""
    rng = random.Random(seed)
    return [
        Item(name=f"item_{i}", weight=rng.randint(1, 30), value=rng.randint(1, 100))
        for i in range(n)
    ]


def compare_early_stop(
    n: int = 50, capacity: int = 200, time_limit: float = 10.0
) -> EarlyStopComparison:
    items = random_items(n)
    t0 = time.perf_counter()
    proven = solve_knapsack(items, capacity, time_limit=time_limit, relative_gap=0.0)
    t1 = time.perf_counter()
    gap5 = solve_knapsack(items, capacity, time_limit=time_limit, relative_gap=0.05)
    t2 = time.perf_counter()

    assert proven.value is not None
    assert gap5.value is not None
    return EarlyStopComparison(
        proven_value=proven.value,
        proven_time=t1 - t0,
        gap5_value=gap5.value,
        gap5_time=t2 - t1,
    )


def main() -> None:
    result = compare_early_stop()
    print(f"Proven-optimal:  value = {result.proven_value}  time = {result.proven_time:.3f}s")
    print(f"5% gap early-stop: value = {result.gap5_value}  time = {result.gap5_time:.3f}s")
    print(f"Speedup (proven/gap5) = {result.speedup:.2f}x")


if __name__ == "__main__":
    main()
