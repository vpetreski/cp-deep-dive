"""Exercise 9.1 — Replace AddNoOverlap with AddCumulative and see what changes.

Problem:
    Instead of treating each machine as a *unary* resource (capacity = 1,
    ``AddNoOverlap``), allow *capacity = 2* on machine 0 only. Show that the
    makespan drops because operations can now double up on the busiest machine.

The other machines stay strict (capacity = 1) via ``AddNoOverlap`` — CP-SAT
lets you mix both in the same model.
"""

from __future__ import annotations

from dataclasses import dataclass

from ortools.sat.python import cp_model

from py_cp_sat_ch09.instances import DEMO_3X3, JobShopInstance


@dataclass(frozen=True)
class CumulativeResult:
    status: str
    makespan: int | None


def solve_with_cumulative_on_machine_0(
    instance: JobShopInstance = DEMO_3X3, capacity_m0: int = 2
) -> CumulativeResult:
    """Solve a job-shop where machine 0 has ``capacity_m0`` parallel slots."""
    model = cp_model.CpModel()
    horizon = instance.horizon()

    intervals_per_machine: list[list[cp_model.IntervalVar]] = [
        [] for _ in range(instance.n_machines)
    ]
    demands_per_machine: list[list[int]] = [[] for _ in range(instance.n_machines)]
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
            demands_per_machine[op.machine].append(1)
            starts_j.append(start)
            ends_j.append(end)
        starts.append(starts_j)
        ends.append(ends_j)

    for j, job in enumerate(instance.jobs):
        for k in range(len(job) - 1):
            model.add(starts[j][k + 1] >= ends[j][k])

    for m in range(instance.n_machines):
        if not intervals_per_machine[m]:
            continue
        if m == 0:
            model.add_cumulative(
                intervals_per_machine[m], demands_per_machine[m], capacity_m0
            )
        else:
            model.add_no_overlap(intervals_per_machine[m])

    makespan = model.new_int_var(0, horizon, "makespan")
    model.add_max_equality(makespan, [ends[j][-1] for j in range(instance.n_jobs)])
    model.minimize(makespan)

    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = 5.0
    solver.parameters.random_seed = 42
    status = solver.solve(model)

    if status in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        return CumulativeResult(
            status=solver.status_name(status), makespan=int(solver.value(makespan))
        )
    return CumulativeResult(status=solver.status_name(status), makespan=None)
