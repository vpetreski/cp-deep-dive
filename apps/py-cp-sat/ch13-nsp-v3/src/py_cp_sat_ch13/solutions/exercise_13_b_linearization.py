"""Exercise 13-B - linearization_level sensitivity.

`linearization_level` controls how aggressively CP-SAT builds an LP
relaxation for the model:

- 0: no LP
- 1: default - moderate LP building
- 2: aggressive - build LP for most constraints

Aggressive linearization gives tighter dual bounds (so `gap` closes
faster) at the cost of slower search iterations. For the NSP the coverage
and fairness constraints are already fairly linear, so level 2 often helps.

Run:
    uv run python -m py_cp_sat_ch13.solutions.exercise_13_b_linearization
"""

from __future__ import annotations

import pathlib
import time

from nsp_core import SolveParams, SolveStatus, load_instance, solve

DATA = pathlib.Path(__file__).resolve().parents[6] / "data" / "nsp"


def main() -> None:
    inst = load_instance(DATA / "toy-02.json")
    print(f"{'lin_level':>10} {'status':>10} {'obj':>8} {'bound':>8} {'gap':>8} {'wall_s':>8}")
    print("-" * 57)
    for lin in (0, 1, 2):
        params = SolveParams(
            time_limit_seconds=10.0,
            num_workers=4,
            random_seed=42,
            linearization_level=lin,
        )
        t0 = time.perf_counter()
        result = solve(inst, params, objective="weighted")
        wall = time.perf_counter() - t0
        assert result.status in {SolveStatus.OPTIMAL, SolveStatus.FEASIBLE}
        obj = result.objective
        bound = result.best_bound
        gap = result.gap
        print(
            f"{lin:>10} {result.status.value:>10}"
            f" {(f'{obj:.1f}' if obj is not None else '-'):>8}"
            f" {(f'{bound:.1f}' if bound is not None else '-'):>8}"
            f" {(f'{gap:.3f}' if gap is not None else '-'):>8}"
            f" {wall:>8.2f}"
        )


if __name__ == "__main__":
    main()
