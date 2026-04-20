"""Toy NSP — Python port of ``apps/mzn/toy-nsp.mzn``.

Decision variables:
    ``work[n][d][s] = 1`` iff nurse ``n`` works shift ``s`` on day ``d``.

Hard constraints:
    HC-1  each ``(day, shift)`` cell is covered by exactly one nurse.
    HC-2  each nurse works at most one shift per day.
    HC-3  per-nurse workload is in ``[min_work, max_work]``.

Objective:
    Minimize ``max(totals) - min(totals)`` — tightest fair distribution.
"""

from __future__ import annotations

from dataclasses import dataclass

from ortools.sat.python import cp_model


@dataclass(frozen=True)
class ToyNspInstance:
    n_nurses: int
    n_days: int
    n_shifts: int
    max_work: int
    min_work: int


DEMO_INSTANCE: ToyNspInstance = ToyNspInstance(
    n_nurses=3, n_days=7, n_shifts=2, max_work=6, min_work=4
)


@dataclass(frozen=True)
class ToyNspResult:
    status: str
    spread: int | None
    totals: list[int]
    # schedule[n][d] = shift_id (0..n_shifts-1) or -1 for off
    schedule: list[list[int]]


def solve_toy_nsp(
    instance: ToyNspInstance = DEMO_INSTANCE, *, time_limit: float = 10.0
) -> ToyNspResult:
    """Solve the toy NSP and return per-nurse workloads + assignment grid."""
    if instance.n_nurses <= 0 or instance.n_days <= 0 or instance.n_shifts <= 0:
        raise ValueError("all dimensions must be positive")
    if instance.min_work > instance.max_work:
        raise ValueError("min_work must be ≤ max_work")

    model = cp_model.CpModel()
    work = [
        [
            [
                model.new_bool_var(f"work_n{n}_d{d}_s{s}")
                for s in range(instance.n_shifts)
            ]
            for d in range(instance.n_days)
        ]
        for n in range(instance.n_nurses)
    ]

    # HC-1 — one nurse per (day, shift).
    for d in range(instance.n_days):
        for s in range(instance.n_shifts):
            model.add(sum(work[n][d][s] for n in range(instance.n_nurses)) == 1)

    # HC-2 — at most one shift per nurse per day.
    for n in range(instance.n_nurses):
        for d in range(instance.n_days):
            model.add(sum(work[n][d][s] for s in range(instance.n_shifts)) <= 1)

    # HC-3 — workload bounds per nurse.
    totals = []
    upper = instance.n_days * instance.n_shifts
    for n in range(instance.n_nurses):
        total = model.new_int_var(0, upper, f"total_n{n}")
        model.add(
            total
            == sum(
                work[n][d][s]
                for d in range(instance.n_days)
                for s in range(instance.n_shifts)
            )
        )
        model.add(total >= instance.min_work)
        model.add(total <= instance.max_work)
        totals.append(total)

    max_total = model.new_int_var(0, upper, "max_total")
    min_total = model.new_int_var(0, upper, "min_total")
    model.add_max_equality(max_total, totals)
    model.add_min_equality(min_total, totals)
    spread = model.new_int_var(0, upper, "spread")
    model.add(spread == max_total - min_total)
    model.minimize(spread)

    solver = cp_model.CpSolver()
    solver.parameters.random_seed = 42
    solver.parameters.max_time_in_seconds = time_limit
    status = solver.solve(model)
    status_name = solver.status_name(status)
    if status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        return ToyNspResult(status=status_name, spread=None, totals=[], schedule=[])

    schedule: list[list[int]] = []
    for n in range(instance.n_nurses):
        row: list[int] = []
        for d in range(instance.n_days):
            assigned = -1
            for s in range(instance.n_shifts):
                if solver.boolean_value(work[n][d][s]):
                    assigned = s
                    break
            row.append(assigned)
        schedule.append(row)

    return ToyNspResult(
        status=status_name,
        spread=int(solver.value(spread)),
        totals=[int(solver.value(t)) for t in totals],
        schedule=schedule,
    )
