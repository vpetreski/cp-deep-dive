"""Exercise 8.1 — Scale the toy NSP up.

Grow ``n_nurses``/``n_days``/``n_shifts`` and observe how Python-CP-SAT
still finds the optimum in a second or two, while the fair-spread objective
remains interpretable.
"""

from __future__ import annotations

from py_cp_sat_ch08.toy_nsp import ToyNspInstance, ToyNspResult, solve_toy_nsp


def solve_larger_instance() -> ToyNspResult:
    instance = ToyNspInstance(
        n_nurses=5, n_days=14, n_shifts=3, max_work=10, min_work=7
    )
    return solve_toy_nsp(instance, time_limit=10.0)


def main() -> None:
    r = solve_larger_instance()
    print(f"Status:  {r.status}")
    print(f"Spread:  {r.spread}")
    print(f"Totals:  {r.totals}")


if __name__ == "__main__":
    main()
