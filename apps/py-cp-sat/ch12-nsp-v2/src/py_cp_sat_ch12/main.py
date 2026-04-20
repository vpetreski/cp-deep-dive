"""Chapter 12 — NSP v2 — soft constraints SC-1..SC-5 + objectives.

Extends the hard-only v1 model with the five soft constraints: preferences,
fairness across nurses, workload balance, weekend distribution, and
consecutive days-off clustering. Offers two objective modes:

- ``--objective weighted``: single weighted-sum objective (default).
- ``--objective lexicographic``: minimise SC-1 (preferences) first, pin, then
  minimise the remaining weighted sum. Useful when you never want to sacrifice
  a preference point to gain a unit of fairness.

Weights default to the spec-locked values (10, 5, 2, 3, 1). Override any of
them via repeated ``--weight NAME=VALUE`` flags, e.g.
    ``--weight preference=20 --weight fairness=1``.
"""

from __future__ import annotations

import argparse
import pathlib
import sys

from nsp_core import ObjectiveWeights, SolveParams, load_instance, solve

from py_cp_sat_ch12.render import render_ascii_with_violations

DEFAULT_INSTANCE = (
    pathlib.Path(__file__).resolve().parents[5]
    / "data"
    / "nsp"
    / "toy-01.json"
)


def _parse_weight_override(raw: list[str]) -> ObjectiveWeights:
    key_map = {
        "preference": "preference",
        "fairness": "fairness",
        "workload": "workload_balance",
        "workloadBalance": "workload_balance",
        "weekend": "weekend_distribution",
        "weekendDistribution": "weekend_distribution",
        "consecutiveOff": "consecutive_days_off",
        "consecutiveDaysOff": "consecutive_days_off",
        "SC1": "preference",
        "SC2": "fairness",
        "SC3": "workload_balance",
        "SC4": "weekend_distribution",
        "SC5": "consecutive_days_off",
    }
    base = ObjectiveWeights()
    overrides: dict[str, int] = {}
    for spec in raw:
        if "=" not in spec:
            raise SystemExit(f"bad --weight syntax: {spec!r} (want NAME=VALUE)")
        name, val = spec.split("=", 1)
        name = name.strip()
        if name not in key_map:
            raise SystemExit(f"unknown weight name: {name!r}")
        overrides[key_map[name]] = int(val)
    merged = {
        "preference": base.preference,
        "fairness": base.fairness,
        "workload_balance": base.workload_balance,
        "weekend_distribution": base.weekend_distribution,
        "consecutive_days_off": base.consecutive_days_off,
    }
    merged.update(overrides)
    return ObjectiveWeights(**merged)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Solve an NSP instance with soft objectives (SC-1..SC-5) and "
            "print a roster annotated with per-code penalty contributions."
        ),
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
    parser.add_argument(
        "--objective",
        choices=["weighted", "lexicographic"],
        default="weighted",
    )
    parser.add_argument(
        "--weight",
        action="append",
        default=[],
        metavar="NAME=VALUE",
        help="Weight override, e.g. 'preference=20'. Repeat for multiple.",
    )
    parser.add_argument("--log", action="store_true")
    args = parser.parse_args(argv)

    instance = load_instance(args.instance)
    weights = _parse_weight_override(args.weight)
    params = SolveParams(
        time_limit_seconds=args.time_limit,
        num_workers=args.workers,
        random_seed=args.seed,
        log_search_progress=args.log,
        objective_weights=weights,
    )
    result = solve(
        instance, params, objective=args.objective, weights=weights
    )

    print(f"Instance: {instance.id}  ({instance.horizon_days}d × "
          f"{len(instance.nurses)}n × {len(instance.shifts)}s)")
    print(f"Mode: {args.objective}  weights: {weights}")
    print(f"Status: {result.status.value}")
    print(f"Objective: {result.objective}  best_bound: {result.best_bound}  "
          f"gap: {result.gap}")
    print(f"Wall time: {result.solve_time_seconds:.2f}s")
    if result.schedule is not None:
        print()
        print(render_ascii_with_violations(instance, result.schedule, result.violations))
        return 0
    if result.status.value == "infeasible":
        print("Model is infeasible under hard constraints alone.")
        return 2
    return 1


if __name__ == "__main__":
    sys.exit(main())
