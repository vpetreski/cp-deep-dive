"""Exercise 13-A - `num_search_workers` scaling.

Sweep 1, 2, 4, 8 workers on toy-02 and print wall time per run. The lesson
is twofold:

1. More workers isn't always faster - at toy-scale, fork/setup cost
   dominates and the serial solve finishes in a blink.
2. CP-SAT's worker pool runs a *portfolio* (different strategies), not just
   parallel copies; the speedup profile is stochastic, not linear.

Run:
    uv run python -m py_cp_sat_ch13.solutions.exercise_13_a_num_workers
"""

from __future__ import annotations

import pathlib
import time

from nsp_core import SolveParams, SolveStatus, load_instance, solve

DATA = pathlib.Path(__file__).resolve().parents[6] / "data" / "nsp"


def main() -> None:
    inst = load_instance(DATA / "toy-02.json")
    print(f"{'workers':>8} {'status':>10} {'obj':>8} {'wall_s':>8}")
    print("-" * 37)
    for workers in (1, 2, 4, 8):
        params = SolveParams(
            time_limit_seconds=10.0,
            num_workers=workers,
            random_seed=42,
        )
        t0 = time.perf_counter()
        result = solve(inst, params, objective="weighted")
        wall = time.perf_counter() - t0
        assert result.status in {SolveStatus.OPTIMAL, SolveStatus.FEASIBLE}
        obj = result.objective if result.objective is not None else float("nan")
        print(f"{workers:>8} {result.status.value:>10} {obj:>8.1f} {wall:>8.2f}")


if __name__ == "__main__":
    main()
