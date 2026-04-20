"""Exercise 11-C — Three-tier skill granularity (junior/mid/senior).

Every non-junior nurse gets a virtual ``mid-or-better`` skill tag so coverage
rules can require "at least 1 senior AND at least 1 mid-or-better" on nights
without adding a new constraint type.

Run:
    uv run python -m py_cp_sat_ch11.solutions.exercise_11_c_three_tier_skills
"""

from __future__ import annotations

import dataclasses
import pathlib

from nsp_core import Nurse, SolveParams, load_instance, solve

DATA = pathlib.Path(__file__).resolve().parents[6] / "data" / "nsp"


def main() -> None:
    inst = load_instance(DATA / "toy-02.json")
    # Re-bucket nurses into three tiers; N5 (Elif) becomes senior, N2 (Bob) mid.
    tier_by_nurse = {
        "N1": {"junior"},
        "N2": {"mid", "mid-or-better"},
        "N3": {"junior"},
        "N4": {"mid", "mid-or-better"},
        "N5": {"senior", "mid-or-better"},
    }
    relabelled: list[Nurse] = []
    for n in inst.nurses:
        extra = tier_by_nurse.get(n.id, set())
        new_skills = frozenset(n.skills | extra)
        relabelled.append(dataclasses.replace(n, skills=new_skills))

    # Night (N) coverage requires >=1 mid-or-better (looser than a per-night
    # senior mandate, which would fight the max-consecutive-nights rule).
    new_coverage = []
    for cov in inst.coverage:
        if cov.shift_id == "N":
            new_coverage.append(
                dataclasses.replace(
                    cov,
                    required_skills=frozenset({"mid-or-better"}),
                )
            )
        else:
            new_coverage.append(cov)

    tiered = dataclasses.replace(
        inst, nurses=tuple(relabelled), coverage=tuple(new_coverage)
    )
    result = solve(
        tiered,
        SolveParams(time_limit_seconds=10.0, num_workers=4, random_seed=42),
        objective="hard",
    )
    print(f"3-tier skills: status={result.status.value}")
    if result.schedule is not None:
        nights = [a for a in result.schedule.assignments if a.shift_id == "N"]
        print(f"  {len(nights)} night assignments scheduled")


if __name__ == "__main__":
    main()
