"""Exercise 10.3 — Require each nurse to have at least one weekend day OFF.

Days 0..4 = Mon..Fri, days 5..6 = Sat, Sun. Add the constraint:

    ``work[n, 5, OFF] + work[n, 6, OFF] >= 1``  for every nurse n.

Verify it holds in the returned schedule.
"""

from __future__ import annotations

from dataclasses import dataclass

from ortools.sat.python import cp_model

from py_cp_sat_ch10.shifts import (
    DAY,
    DEFAULT_INSTANCE,
    NIGHT,
    OFF,
    ShiftInstance,
    _build_transition_tuples,
)


@dataclass(frozen=True)
class WeekendResult:
    status: str
    schedule: tuple[tuple[int, ...], ...]


def solve_with_weekend_off(
    instance: ShiftInstance = DEFAULT_INSTANCE,
) -> WeekendResult:
    model = cp_model.CpModel()
    n_nurses, n_days = instance.n_nurses, instance.n_days
    assert n_days >= 7, "need a full week to talk about weekends"

    assign = [
        [model.new_int_var(0, 2, f"a_{n}_{d}") for d in range(n_days)]
        for n in range(n_nurses)
    ]
    bools: list[list[dict[int, cp_model.IntVar]]] = []
    for n in range(n_nurses):
        per_day = []
        for d in range(n_days):
            entries = {}
            for s in (OFF, DAY, NIGHT):
                b = model.new_bool_var(f"b_{n}_{d}_{s}")
                model.add(assign[n][d] == s).only_enforce_if(b)
                model.add(assign[n][d] != s).only_enforce_if(~b)
                entries[s] = b
            model.add_exactly_one(list(entries.values()))
            per_day.append(entries)
        bools.append(per_day)

    for d in range(n_days):
        for s in (DAY, NIGHT):
            model.add(sum(bools[n][d][s] for n in range(n_nurses)) >= instance.coverage_min)

    for n in range(n_nurses):
        model.add_automaton(
            assign[n],
            starting_state=0,
            final_states=[0, 1, 2],
            transition_triples=_build_transition_tuples(),
        )

    for n in range(n_nurses):
        model.add(
            sum(bools[n][d][DAY] + bools[n][d][NIGHT] for d in range(n_days))
            <= instance.max_shifts_per_nurse
        )

    # Weekend-off rule.
    for n in range(n_nurses):
        model.add(bools[n][5][OFF] + bools[n][6][OFF] >= 1)

    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = 5.0
    solver.parameters.random_seed = 42
    status = solver.solve(model)

    if status in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        schedule = tuple(
            tuple(int(solver.value(assign[n][d])) for d in range(n_days))
            for n in range(n_nurses)
        )
        return WeekendResult(status=solver.status_name(status), schedule=schedule)

    return WeekendResult(status=solver.status_name(status), schedule=())


def everyone_has_weekend_off(schedule: tuple[tuple[int, ...], ...]) -> bool:
    return all(row[5] == OFF or row[6] == OFF for row in schedule)
