"""Chapter 11 — NSP v1 — toy instance, hard constraints only.

Runs the HC-1..HC-8 model against a given instance JSON (defaults to
``data/nsp/toy-01.json``) and prints an ASCII roster. Heavy lifting lives in
``nsp_core`` so the chapter code stays focused on the teaching story:
    load -> build -> solve -> render.
"""

from __future__ import annotations

import argparse
import pathlib
import sys

from nsp_core import SolveParams, load_instance, solve

from py_cp_sat_ch11.render import render_ascii

DEFAULT_INSTANCE = (
    pathlib.Path(__file__).resolve().parents[5]
    / "data"
    / "nsp"
    / "toy-01.json"
)


def main(argv: list[str] | None = None) -> int:
    """CLI entrypoint. Returns a POSIX-style exit code."""
    parser = argparse.ArgumentParser(
        description="Solve an NSP instance with HC-1..HC-8 only and print the roster.",
    )
    parser.add_argument(
        "instance",
        nargs="?",
        default=str(DEFAULT_INSTANCE),
        help=f"Path to an NSP instance JSON file (default: {DEFAULT_INSTANCE}).",
    )
    parser.add_argument("--time-limit", type=float, default=30.0)
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--log", action="store_true")
    args = parser.parse_args(argv)

    instance = load_instance(args.instance)
    params = SolveParams(
        time_limit_seconds=args.time_limit,
        num_workers=args.workers,
        random_seed=args.seed,
        log_search_progress=args.log,
    )
    result = solve(instance, params, objective="hard")

    print(f"Instance: {instance.id}  ({instance.horizon_days}d × "
          f"{len(instance.nurses)}n × {len(instance.shifts)}s)")
    print(f"Status: {result.status.value}")
    print(f"Wall time: {result.solve_time_seconds:.2f}s")
    if result.schedule is not None:
        print()
        print(render_ascii(instance, result.schedule))
        return 0
    if result.status.value == "infeasible":
        print("Model is infeasible. Try relaxing coverage or rest rules.")
        return 2
    return 1


if __name__ == "__main__":
    sys.exit(main())
