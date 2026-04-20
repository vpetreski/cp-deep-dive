"""Exercise 8.2 — Tighten the workload window until the model is infeasible.

With ``n_nurses=3``, ``n_days=7``, ``n_shifts=2`` the workload sum is fixed at
``7 * 2 = 14``. If ``min_work=5`` and ``max_work=5`` then total demand ``15``
must equal ``3 * 5 = 15`` — feasible. Push ``max_work`` down to ``4`` (total
``12 < 14``) and the model must report ``INFEASIBLE``.
"""

from __future__ import annotations

from py_cp_sat_ch08.toy_nsp import ToyNspInstance, ToyNspResult, solve_toy_nsp


def solve_infeasible() -> ToyNspResult:
    instance = ToyNspInstance(
        n_nurses=3, n_days=7, n_shifts=2, max_work=4, min_work=2
    )
    return solve_toy_nsp(instance, time_limit=5.0)


def main() -> None:
    r = solve_infeasible()
    print(f"Status: {r.status}")


if __name__ == "__main__":
    main()
