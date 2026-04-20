"""Chapter 13 - NSP v3 - benchmark harness pattern on medium instances.

Runs the same NSP instance under a handful of different solver parameter
sets and prints a small table comparing time-to-first-feasible, final
objective, best bound, and gap. The chapter teaches how to read these
numbers and how to pick one tuning knob at a time.

The "medium instance" here is `data/nsp/toy-02.json` until we add the real
INRC-II benchmark loader; the harness itself generalises to any instance
that fits the shared NSP schema.
"""

from __future__ import annotations

import argparse
import pathlib
import sys
import time
from dataclasses import dataclass

from nsp_core import SolveParams, SolveStatus, load_instance, solve

DEFAULT_INSTANCE = (
    pathlib.Path(__file__).resolve().parents[5]
    / "data"
    / "nsp"
    / "toy-02.json"
)


@dataclass
class RunRow:
    """One row in the benchmark table."""

    label: str
    status: SolveStatus
    objective: float | None
    bound: float | None
    gap: float | None
    solve_seconds: float
    wall_seconds: float


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(
        description="Chapter 13 benchmark harness - try a few tunings, print a table.",
    )
    p.add_argument("instance", nargs="?", default=str(DEFAULT_INSTANCE))
    p.add_argument("--time-limit", type=float, default=10.0)
    args = p.parse_args(argv)

    instance = load_instance(args.instance)
    print(
        f"Instance: {instance.id}  "
        f"({instance.horizon_days}d x {len(instance.nurses)}n x "
        f"{len(instance.shifts)}s)"
    )
    print(f"Time limit per run: {args.time_limit:.1f}s\n")

    configs: list[tuple[str, SolveParams, str]] = [
        (
            "default",
            SolveParams(time_limit_seconds=args.time_limit, num_workers=8, random_seed=42),
            "weighted",
        ),
        (
            "single-worker",
            SolveParams(time_limit_seconds=args.time_limit, num_workers=1, random_seed=42),
            "weighted",
        ),
        (
            "aggressive-linearization",
            SolveParams(
                time_limit_seconds=args.time_limit,
                num_workers=8,
                random_seed=42,
                linearization_level=2,
            ),
            "weighted",
        ),
        (
            "gap-5pct",
            SolveParams(
                time_limit_seconds=args.time_limit,
                num_workers=8,
                random_seed=42,
                relative_gap_limit=0.05,
            ),
            "weighted",
        ),
        (
            "lexicographic",
            SolveParams(time_limit_seconds=args.time_limit, num_workers=8, random_seed=42),
            "lexicographic",
        ),
    ]

    rows: list[RunRow] = []
    for label, params, objective in configs:
        t0 = time.perf_counter()
        result = solve(instance, params, objective=objective)
        wall = time.perf_counter() - t0
        rows.append(
            RunRow(
                label=label,
                status=result.status,
                objective=result.objective,
                bound=result.best_bound,
                gap=result.gap,
                solve_seconds=result.solve_time_seconds,
                wall_seconds=wall,
            )
        )

    _print_table(rows)
    return 0


def _print_table(rows: list[RunRow]) -> None:
    header = f"{'config':>26}  {'status':>10}  {'obj':>7}  {'bound':>7}  {'gap':>6}  {'solve_s':>8}  {'wall_s':>8}"
    print(header)
    print("-" * len(header))
    for r in rows:
        obj = f"{r.objective:>7.1f}" if r.objective is not None else f"{'-':>7}"
        bound = f"{r.bound:>7.1f}" if r.bound is not None else f"{'-':>7}"
        gap = f"{r.gap:>6.3f}" if r.gap is not None else f"{'-':>6}"
        print(
            f"{r.label:>26}  {r.status.value:>10}  {obj}  {bound}  {gap}"
            f"  {r.solve_seconds:>8.2f}  {r.wall_seconds:>8.2f}"
        )


if __name__ == "__main__":
    sys.exit(main())
