"""Exercise 9.2 — Add release times per job.

Each job gets a ``release_time[j]``; its first operation cannot start before
then. Show that makespan strictly increases when releases bite.

In the 3×3 instance, releases ``(0, 3, 6)`` push the earliest possible start
of job 2 past the natural makespan of the un-released variant — good for a
test that's insensitive to the exact optimum.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from ortools.sat.python import cp_model

from py_cp_sat_ch09.instances import DEMO_3X3, JobShopInstance


@dataclass(frozen=True)
class ReleaseResult:
    status: str
    makespan: int | None


def solve_with_releases(
    instance: JobShopInstance = DEMO_3X3, releases: Sequence[int] = (0, 3, 6)
) -> ReleaseResult:
    """Solve with one release time per job. ``releases[j]`` bounds op[0] start."""
    if len(releases) != instance.n_jobs:
        raise ValueError(
            f"need one release per job (expected {instance.n_jobs}, got {len(releases)})"
        )

    model = cp_model.CpModel()
    horizon = instance.horizon() + max(releases)

    intervals_per_machine: list[list[cp_model.IntervalVar]] = [
        [] for _ in range(instance.n_machines)
    ]
    starts: list[list[cp_model.IntVar]] = []
    ends: list[list[cp_model.IntVar]] = []

    for j, job in enumerate(instance.jobs):
        starts_j: list[cp_model.IntVar] = []
        ends_j: list[cp_model.IntVar] = []
        for k, op in enumerate(job):
            lb = releases[j] if k == 0 else 0
            start = model.new_int_var(lb, horizon, f"s_{j}_{k}")
            end = model.new_int_var(0, horizon, f"e_{j}_{k}")
            interval = model.new_interval_var(start, op.duration, end, f"iv_{j}_{k}")
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
        return ReleaseResult(
            status=solver.status_name(status), makespan=int(solver.value(makespan))
        )
    return ReleaseResult(status=solver.status_name(status), makespan=None)
