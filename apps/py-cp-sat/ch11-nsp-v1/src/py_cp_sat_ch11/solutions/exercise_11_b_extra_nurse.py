"""Exercise 11-B — Add a sixth nurse to toy-01 and compare wall-time.

Answers the classical question: does more workforce flexibility mean faster
solving? Usually yes, because coverage slack lets presolve prune more branches.

Run:
    uv run python -m py_cp_sat_ch11.solutions.exercise_11_b_extra_nurse
"""

from __future__ import annotations

import dataclasses
import pathlib
import time

from nsp_core import Nurse, SolveParams, load_instance, solve

DATA = pathlib.Path(__file__).resolve().parents[6] / "data" / "nsp"


def main() -> None:
    inst = load_instance(DATA / "toy-01.json")
    params = SolveParams(time_limit_seconds=10.0, num_workers=1, random_seed=42)

    start = time.perf_counter()
    r1 = solve(inst, params, objective="hard")
    base_time = time.perf_counter() - start
    print(f"baseline 3 nurses: status={r1.status.value} wall={base_time:.3f}s")

    extra = Nurse(
        id="N4",
        name="Daniela",
        skills=frozenset({"general"}),
        contract_hours_per_week=24,
        unavailable=frozenset(),
    )
    bigger = dataclasses.replace(inst, nurses=(*inst.nurses, extra))

    start = time.perf_counter()
    r2 = solve(bigger, params, objective="hard")
    boosted_time = time.perf_counter() - start
    print(f"boosted 4 nurses:  status={r2.status.value} wall={boosted_time:.3f}s")

    delta = boosted_time - base_time
    direction = "faster" if delta < 0 else "slower"
    print(f"Δ wall time: {abs(delta):.3f}s {direction} with an extra nurse.")


if __name__ == "__main__":
    main()
