"""Exercise 5.5 — Enumerate all optimal subsets.

Thin wrapper around :func:`py_cp_sat_ch05.knapsack.enumerate_optimal_subsets` —
this file exists so the solutions folder has one per exercise and the
``main()`` here prints each optimal subset with its total weight/value.
"""

from __future__ import annotations

from py_cp_sat_ch05.knapsack import (
    DEMO_CAPACITY,
    DEMO_ITEMS,
    enumerate_optimal_subsets,
    solve_knapsack,
)


def enumerate_demo_optima() -> list[list[str]]:
    return enumerate_optimal_subsets(list(DEMO_ITEMS), DEMO_CAPACITY)


def main() -> None:
    optimum = solve_knapsack(list(DEMO_ITEMS), DEMO_CAPACITY)
    print(f"Optimal value = {optimum.value}")
    subsets = enumerate_demo_optima()
    print(f"Found {len(subsets)} distinct optimal subsets:")
    by_name = {it.name: it for it in DEMO_ITEMS}
    for i, names in enumerate(subsets):
        total_weight = sum(by_name[n].weight for n in names)
        total_value = sum(by_name[n].value for n in names)
        print(f"  {i+1}. value={total_value} weight={total_weight}  {names}")


if __name__ == "__main__":
    main()
