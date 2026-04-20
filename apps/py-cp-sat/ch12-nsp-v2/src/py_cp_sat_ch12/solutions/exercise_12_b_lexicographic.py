"""Exercise 12-B - Weighted-sum vs lexicographic.

The two objective modes give different trade-offs. This exercise solves the
same instance both ways and reports:

- Final objective value (in the weighted-sum sense, for apples-to-apples).
- Raw SC-1 preference violations (lower = better, lexicographic pins this first).
- Raw SC-2 fairness spread (absolute difference between the busiest and idlest
  nurse's shift count).

Run:
    uv run python -m py_cp_sat_ch12.solutions.exercise_12_b_lexicographic
"""

from __future__ import annotations

import pathlib

from nsp_core import ObjectiveWeights, SolveParams, SolveStatus, load_instance, solve

DATA = pathlib.Path(__file__).resolve().parents[6] / "data" / "nsp"


def main() -> None:
    inst = load_instance(DATA / "toy-02.json")
    weights = ObjectiveWeights(
        preference=10,
        fairness=5,
        workload_balance=2,
        weekend_distribution=3,
        consecutive_days_off=1,
    )
    params = SolveParams(
        time_limit_seconds=20.0,
        num_workers=4,
        random_seed=42,
        objective_weights=weights,
    )

    print(f"Solving {inst.id} ({len(inst.nurses)}n x {inst.horizon_days}d)")
    print(f"Weights: {weights}")
    print()

    for mode in ("weighted", "lexicographic"):
        result = solve(inst, params, objective=mode, weights=weights)
        assert result.status in {SolveStatus.OPTIMAL, SolveStatus.FEASIBLE}, (
            f"{mode} failed with status {result.status}"
        )

        sc1 = _raw(result.violations, "SC-1", weights.preference)
        sc2 = _raw(result.violations, "SC-2", weights.fairness)
        obj = result.objective if result.objective is not None else float("nan")
        print(
            f"  {mode:>14}  obj={obj:>7.1f}  "
            f"SC-1_raw={sc1:>5.1f}  SC-2_raw={sc2:>5.1f}  "
            f"wall={result.solve_time_seconds:>6.2f}s"
        )

    print()
    print(
        "Interpretation: lexicographic guarantees SC-1 (preference) is as low "
        "as possible first, then minimises the weighted rest.\n"
        "Weighted-sum can trade away SC-1 units for large gains elsewhere if "
        "the weight ratio tips that way."
    )


def _raw(violations: object, code: str, weight: int) -> float:
    if violations is None:
        return 0.0
    for v in violations:  # type: ignore[union-attr]
        if v.code == code and v.penalty is not None:
            return float(v.penalty) / float(weight) if weight else float(v.penalty)
    return 0.0


if __name__ == "__main__":
    main()
