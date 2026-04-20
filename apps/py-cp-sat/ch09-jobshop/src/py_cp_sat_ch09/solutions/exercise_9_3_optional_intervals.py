"""Exercise 9.3 — Optional intervals for an alternative machine.

Suppose operation (job=1, op_index=2) — currently pinned to machine 1 for 4
time units — can *optionally* run on machine 2 instead, at 5 time units (slower
but on a less-loaded machine). Build the alternative as two
``NewOptionalIntervalVar`` candidates, gated by a pair of booleans whose sum
must equal 1, and let CP-SAT pick the cheaper branch.

This is the canonical "flexible job shop" pattern — now that you've built it
once, extending to a full FJSP is just repeating the same trick per operation.
"""

from __future__ import annotations

from dataclasses import dataclass

from ortools.sat.python import cp_model

from py_cp_sat_ch09.instances import DEMO_3X3


@dataclass(frozen=True)
class AlternativeResult:
    status: str
    makespan: int | None
    # True if the alternative (slower-but-less-loaded) machine was chosen.
    used_alternative: bool


def solve_with_alternative_machine() -> AlternativeResult:
    """Solve DEMO_3X3 with an alternative machine for job 1, operation 2."""
    model = cp_model.CpModel()
    instance = DEMO_3X3
    horizon = instance.horizon() + 5

    intervals_per_machine: list[list[cp_model.IntervalVar]] = [
        [] for _ in range(instance.n_machines)
    ]
    starts: list[list[cp_model.IntVar]] = []
    ends: list[list[cp_model.IntVar]] = []

    used_alt = model.new_bool_var("used_alt")

    for j, job in enumerate(instance.jobs):
        starts_j: list[cp_model.IntVar] = []
        ends_j: list[cp_model.IntVar] = []
        for k, op in enumerate(job):
            start = model.new_int_var(0, horizon, f"s_{j}_{k}")
            end = model.new_int_var(0, horizon, f"e_{j}_{k}")

            if j == 1 and k == 2:
                # Alternative: either (machine 1, duration 4) or (machine 2, duration 5).
                # Same start/end vars; two optional intervals gate on ``used_alt``.
                dur_primary = 4
                dur_alt = 5

                primary_end = model.new_int_var(0, horizon, f"e_alt_primary_{j}_{k}")
                alt_end = model.new_int_var(0, horizon, f"e_alt_alt_{j}_{k}")

                iv_primary = model.new_optional_interval_var(
                    start, dur_primary, primary_end, ~used_alt, f"iv_primary_{j}_{k}"
                )
                iv_alt = model.new_optional_interval_var(
                    start, dur_alt, alt_end, used_alt, f"iv_alt_{j}_{k}"
                )

                intervals_per_machine[1].append(iv_primary)
                intervals_per_machine[2].append(iv_alt)

                # Tie the master ``end`` to whichever branch is active.
                model.add(end == primary_end).only_enforce_if(~used_alt)
                model.add(end == alt_end).only_enforce_if(used_alt)
            else:
                interval = model.new_interval_var(
                    start, op.duration, end, f"iv_{j}_{k}"
                )
                intervals_per_machine[op.machine].append(interval)

            starts_j.append(start)
            ends_j.append(end)
        starts.append(starts_j)
        ends.append(ends_j)

    for j, job in enumerate(instance.jobs):
        for k in range(len(job) - 1):
            model.add(starts[j][k + 1] >= ends[j][k])

    for m in range(instance.n_machines):
        if intervals_per_machine[m]:
            model.add_no_overlap(intervals_per_machine[m])

    makespan = model.new_int_var(0, horizon, "makespan")
    model.add_max_equality(makespan, [ends[j][-1] for j in range(instance.n_jobs)])
    model.minimize(makespan)

    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = 5.0
    solver.parameters.random_seed = 42
    status = solver.solve(model)

    if status in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        return AlternativeResult(
            status=solver.status_name(status),
            makespan=int(solver.value(makespan)),
            used_alternative=bool(solver.value(used_alt)),
        )
    return AlternativeResult(
        status=solver.status_name(status), makespan=None, used_alternative=False
    )
