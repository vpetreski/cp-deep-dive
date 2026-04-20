"""Tiny benchmark wrapper: solve one NSP instance with CP-SAT, print one
JSON line to stdout, exit.

Used by the benchmarks/runner/ harness (see CpSatAdapter.kt). Keeping this
script in-tree rather than adding a CLI to nsp-core itself because it's
strictly for benchmarking: no progress rendering, no schedule emission,
just a machine-readable result record.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import sys
import time

from nsp_core import SolveParams, SolveStatus, load_instance, solve


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--instance", required=True, type=pathlib.Path)
    p.add_argument("--time-limit", required=True, type=int)
    p.add_argument("--workers", type=int, default=8)
    p.add_argument("--seed", type=int, default=42)
    p.add_argument(
        "--objective",
        choices=["hard", "weighted", "lexicographic"],
        default="weighted",
    )
    args = p.parse_args(argv)

    instance = load_instance(args.instance)
    params = SolveParams(
        time_limit_seconds=float(args.time_limit),
        num_workers=args.workers,
        random_seed=args.seed,
    )
    t0 = time.perf_counter()
    result = solve(instance, params, objective=args.objective)
    wall = time.perf_counter() - t0

    # Minimal trajectory: only the final incumbent. A future extension could
    # plumb progress_callback into a list of (t, obj) points.
    trajectory = (
        [{"tSeconds": result.solve_time_seconds, "objective": float(result.objective)}]
        if result.objective is not None
        else []
    )

    out = {
        "instanceId": instance.id,
        "status": _wire_status(result.status),
        "objective": float(result.objective) if result.objective is not None else None,
        "bound": float(result.best_bound) if result.best_bound is not None else None,
        "gap": float(result.gap) if result.gap is not None else None,
        "solveSeconds": float(result.solve_time_seconds or wall),
        "trajectory": trajectory,
    }
    print(json.dumps(out))
    return 0


def _wire_status(status: SolveStatus) -> str:
    return status.value


if __name__ == "__main__":
    sys.exit(main())
