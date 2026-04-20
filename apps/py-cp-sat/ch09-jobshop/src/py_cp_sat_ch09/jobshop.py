"""Core job-shop solver — IntervalVar + AddNoOverlap + makespan minimization.

Shape of the model
------------------

Variables (per operation):
    ``start``, ``end``, ``interval = NewIntervalVar(start, duration, end)``

Constraints:
    * **Precedence** inside a job: ``end_of(op_i) <= start_of(op_{i+1})``.
    * **Machine exclusion**: ``AddNoOverlap(intervals_on_machine_m)`` for every
      machine — CP-SAT's specialized disjunctive propagator that replaces the
      quadratic pairwise ``start + dur <= start'`` / ``start' + dur <= start``
      disjunctions with a single global constraint.
    * **Makespan**: ``AddMaxEquality(makespan, all_ends)``, then minimize it.

We pin the random seed so that CI runs are reproducible.
"""

from __future__ import annotations

from dataclasses import dataclass

from ortools.sat.python import cp_model

from py_cp_sat_ch09.instances import DEMO_3X3, JobShopInstance


@dataclass(frozen=True)
class ScheduledOp:
    """One fully scheduled operation in the solution."""

    job: int
    op_index: int
    machine: int
    start: int
    duration: int

    @property
    def end(self) -> int:
        return self.start + self.duration


@dataclass(frozen=True)
class JobShopSolution:
    """What the solver returns for one job-shop instance."""

    status: str
    makespan: int | None
    schedule: tuple[ScheduledOp, ...]
    wall_time_s: float


def solve_jobshop(
    instance: JobShopInstance = DEMO_3X3,
    *,
    time_limit_s: float = 10.0,
    log_search: bool = False,
) -> JobShopSolution:
    """Solve a job-shop instance, minimizing makespan.

    Returns the optimal schedule when the solver proves optimality within
    ``time_limit_s`` — otherwise returns the best incumbent (FEASIBLE) or
    an empty schedule (INFEASIBLE / UNKNOWN).
    """
    model = cp_model.CpModel()

    horizon = instance.horizon()

    # Build one IntervalVar per operation.
    intervals_per_machine: list[list[cp_model.IntervalVar]] = [
        [] for _ in range(instance.n_machines)
    ]
    starts: list[list[cp_model.IntVar]] = []
    ends: list[list[cp_model.IntVar]] = []

    for j, job in enumerate(instance.jobs):
        starts_j: list[cp_model.IntVar] = []
        ends_j: list[cp_model.IntVar] = []
        for k, op in enumerate(job):
            suffix = f"j{j}_op{k}"
            start = model.new_int_var(0, horizon, f"start_{suffix}")
            end = model.new_int_var(0, horizon, f"end_{suffix}")
            interval = model.new_interval_var(
                start, op.duration, end, f"iv_{suffix}"
            )
            intervals_per_machine[op.machine].append(interval)
            starts_j.append(start)
            ends_j.append(end)
        starts.append(starts_j)
        ends.append(ends_j)

    # Precedence inside each job.
    for j, job in enumerate(instance.jobs):
        for k in range(len(job) - 1):
            model.add(starts[j][k + 1] >= ends[j][k])

    # One AddNoOverlap per machine — the heart of the model.
    for m in range(instance.n_machines):
        if intervals_per_machine[m]:
            model.add_no_overlap(intervals_per_machine[m])

    # Makespan = max over all end times.
    makespan = model.new_int_var(0, horizon, "makespan")
    model.add_max_equality(makespan, [ends[j][-1] for j in range(instance.n_jobs)])
    model.minimize(makespan)

    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = time_limit_s
    solver.parameters.random_seed = 42
    solver.parameters.log_search_progress = log_search

    status = solver.solve(model)

    status_name = solver.status_name(status)
    if status in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        scheduled: list[ScheduledOp] = []
        for j, job in enumerate(instance.jobs):
            for k, op in enumerate(job):
                scheduled.append(
                    ScheduledOp(
                        job=j,
                        op_index=k,
                        machine=op.machine,
                        start=int(solver.value(starts[j][k])),
                        duration=op.duration,
                    )
                )
            # end-aligned output looks nicer — sort machines later at render time
        return JobShopSolution(
            status=status_name,
            makespan=int(solver.value(makespan)),
            schedule=tuple(scheduled),
            wall_time_s=float(solver.wall_time),
        )

    return JobShopSolution(
        status=status_name,
        makespan=None,
        schedule=(),
        wall_time_s=float(solver.wall_time),
    )


def verify_schedule(instance: JobShopInstance, solution: JobShopSolution) -> bool:
    """Brute-force check that the schedule respects precedence + no-overlap."""
    if solution.makespan is None:
        return False

    by_job: dict[int, list[ScheduledOp]] = {}
    by_machine: dict[int, list[ScheduledOp]] = {}
    for op in solution.schedule:
        by_job.setdefault(op.job, []).append(op)
        by_machine.setdefault(op.machine, []).append(op)

    # Precedence within jobs.
    for j, ops in by_job.items():
        ops_sorted = sorted(ops, key=lambda o: o.op_index)
        for a, b in zip(ops_sorted, ops_sorted[1:], strict=False):
            if a.end > b.start:
                return False
        # Every operation belongs to the correct machine.
        for op, planned in zip(ops_sorted, instance.jobs[j], strict=True):
            if op.machine != planned.machine or op.duration != planned.duration:
                return False

    # No overlap per machine.
    for ops in by_machine.values():
        ops_sorted = sorted(ops, key=lambda o: o.start)
        for a, b in zip(ops_sorted, ops_sorted[1:], strict=False):
            if a.end > b.start:
                return False

    return solution.makespan == max(op.end for op in solution.schedule)
