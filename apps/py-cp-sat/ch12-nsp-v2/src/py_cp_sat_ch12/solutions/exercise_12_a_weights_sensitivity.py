"""Exercise 12-A - Weights sensitivity sweep.

Vary the SC-1 preference weight from 0 to 50 and track three things for each
step:

- The weighted objective (dominant term depends on which weight is cranked).
- The *raw* SC-1 violation count (unweighted, so comparable across runs).
- The *raw* SC-2 fairness spread (same reason).

Run:
    uv run python -m py_cp_sat_ch12.solutions.exercise_12_a_weights_sensitivity
"""

from __future__ import annotations

import pathlib

from nsp_core import ObjectiveWeights, SolveParams, SolveStatus, load_instance, solve

DATA = pathlib.Path(__file__).resolve().parents[6] / "data" / "nsp"


def main() -> None:
    inst = load_instance(DATA / "toy-02.json")
    header = f"{'pref_w':>7} {'status':>10} {'obj':>10} {'SC-1 raw':>10} {'SC-2 raw':>10}"
    print(header)
    print("-" * len(header))

    for pref_w in (0, 1, 5, 10, 25, 50):
        weights = ObjectiveWeights(
            preference=pref_w,
            fairness=5,
            workload_balance=2,
            weekend_distribution=3,
            consecutive_days_off=1,
        )
        params = SolveParams(
            time_limit_seconds=10.0,
            num_workers=4,
            random_seed=42,
            objective_weights=weights,
        )
        result = solve(inst, params, objective="weighted", weights=weights)

        # Recover raw (unweighted) SC-1 and SC-2 magnitudes from the violation
        # rows: raw = penalty / weight (when the weight is zero we fall back
        # to penalty as-is — that branch is just informational).
        sc1_raw = _extract_raw(result.violations, "SC-1", pref_w)
        sc2_raw = _extract_raw(result.violations, "SC-2", 5)

        obj_str = (
            f"{result.objective:>10.1f}"
            if result.objective is not None
            else f"{'-':>10}"
        )
        print(
            f"{pref_w:>7} {result.status.value:>10} {obj_str}"
            f" {sc1_raw:>10.1f} {sc2_raw:>10.1f}"
        )

        # Sanity: toy-02 has a feasible solution so every run should terminate.
        assert result.status in {SolveStatus.OPTIMAL, SolveStatus.FEASIBLE}


def _extract_raw(
    violations: object,
    code: str,
    weight: int,
) -> float:
    """Back out the raw violation count from the weighted penalty we emit."""
    if violations is None:
        return 0.0
    for v in violations:  # type: ignore[union-attr]
        if v.code == code and v.penalty is not None:
            if weight == 0:
                return float(v.penalty)
            return float(v.penalty) / float(weight)
    return 0.0


if __name__ == "__main__":
    main()
