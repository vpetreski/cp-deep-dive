"""Chapter 05 — Optimization: objectives, bounds, callbacks.

Runs two optimization demos:
    1. 0/1 Knapsack on the 15-item jewelry shop, streaming the incumbent
       trace so you can *see* the bound/incumbent convergence.
    2. Bin Packing on a tiny instance (10 items, capacity 10).
"""

from __future__ import annotations

from dataclasses import dataclass

from py_cp_sat_ch05.bin_packing import (
    DEMO_CAPACITY as BIN_CAPACITY,
)
from py_cp_sat_ch05.bin_packing import (
    DEMO_WEIGHTS as BIN_WEIGHTS,
)
from py_cp_sat_ch05.bin_packing import (
    BinPackingResult,
    solve_bin_packing,
)
from py_cp_sat_ch05.knapsack import (
    DEMO_CAPACITY as KNAPSACK_CAPACITY,
)
from py_cp_sat_ch05.knapsack import (
    DEMO_ITEMS,
    KnapsackResult,
    solve_knapsack,
)


@dataclass(frozen=True)
class ChapterDemo:
    """Results from the chapter-level ``main()`` demo."""

    knapsack: KnapsackResult
    bin_packing: BinPackingResult


def solve() -> ChapterDemo:
    """Run both chapter demos and return their results together."""
    knapsack = solve_knapsack(list(DEMO_ITEMS), KNAPSACK_CAPACITY, time_limit=5.0)
    bin_packing = solve_bin_packing(list(BIN_WEIGHTS), BIN_CAPACITY, time_limit=5.0)
    return ChapterDemo(knapsack=knapsack, bin_packing=bin_packing)


def main() -> None:
    """Print the chapter demos in a human-friendly format."""
    result = solve()

    knapsack = result.knapsack
    print("=== 0/1 Knapsack (15 items, capacity=20) ===")
    print(f"Status: {knapsack.status}")
    print(f"Value:  {knapsack.value}")
    print(f"Bound:  {knapsack.bound}")
    print(f"Chosen: {knapsack.chosen}")
    print("Incumbent trace (wall, obj, bound):")
    for row in knapsack.trace:
        print(f"  t={row.wall_time:7.3f}s  obj={row.objective:6.0f}  bound={row.bound:8.2f}")

    print()
    bp = result.bin_packing
    print(f"=== Bin Packing (n={len(BIN_WEIGHTS)} items, capacity={BIN_CAPACITY}) ===")
    print(f"Status:   {bp.status}")
    print(f"Bins:     {bp.num_bins}")
    for j, (members, load) in enumerate(zip(bp.assignment, bp.bin_loads, strict=True)):
        contents = ", ".join(f"item_{i}({BIN_WEIGHTS[i]})" for i in members)
        print(f"  bin{j} load={load}: {contents}")


if __name__ == "__main__":
    main()
