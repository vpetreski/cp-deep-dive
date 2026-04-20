"""Exercise 9.4 — Swap makespan for total completion time (flow time).

*Total completion time* (a.k.a. sum of job completion times, a.k.a. flow time)
is a different objective. Minimizing it tends to short-circuit jobs: finish a
cheap job early rather than letting it float in an otherwise-full schedule.

The model is identical to ``solve_jobshop`` except the objective:
    ``minimize sum_j end_of_last_op(j)``

Compare the two optima on the same instance — they usually disagree.
"""

from __future__ import annotations

from dataclasses import dataclass

from ortools.sat.python import cp_model

from py_cp_sat_ch09.instances import DEMO_3X3, JobShopInstance


@dataclass(frozen=True)
class FlowTimeResult:
    status: str
    total_flow_time: int | None
    job_completions: tuple[int, ...]


def solve_minimize_flow_time(
    instance: JobShopInstance = DEMO_3X3,
) -> FlowTimeResult:
    """Minimize the sum of per-job completion times."""
    model = cp_model.CpModel()
    horizon = instance.horizon()

    intervals_per_machine: list[list[cp_model.IntervalVar]] = [
        [] for _ in range(instance.n_machines)
    ]
    starts: list[list[cp_model.IntVar]] = []
    ends: list[list[cp_model.IntVar]] = []

    for j, job in enumerate(instance.jobs):
        starts_j: list[cp_model.IntVar] = []
        ends_j: list[cp_model.IntVar] = []
        for k, op in enumerate(job):
            start = model.new_int_var(0, horizon, f"s_{j}_{k}")
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

    job_ends = [ends[j][-1] for j in range(instance.n_jobs)]
    model.minimize(sum(job_ends))

    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = 5.0
    solver.parameters.random_seed = 42
    status = solver.solve(model)

    if status in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        completions = tuple(int(solver.value(e)) for e in job_ends)
        return FlowTimeResult(
            status=solver.status_name(status),
            total_flow_time=sum(completions),
            job_completions=completions,
        )
    return FlowTimeResult(
        status=solver.status_name(status), total_flow_time=None, job_completions=()
    )
