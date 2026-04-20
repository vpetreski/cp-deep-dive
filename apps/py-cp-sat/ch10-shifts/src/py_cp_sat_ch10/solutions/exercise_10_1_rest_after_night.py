"""Exercise 10.1 — Require at least two OFF days after a NIGHT shift.

The default model only forbids NIGHT → DAY. Stretch the automaton to also
forbid NIGHT → NIGHT *and* NIGHT → DAY on the very next day; effectively the
day after a NIGHT must be OFF, no exceptions.

New state machine:
    state 0 = was OFF (or start)
    state 1 = worked DAY yesterday
    state 2 = worked NIGHT yesterday — must go OFF
"""

from __future__ import annotations

from dataclasses import dataclass

from ortools.sat.python import cp_model

from py_cp_sat_ch10.shifts import DAY, DEFAULT_INSTANCE, NIGHT, OFF, ShiftInstance


@dataclass(frozen=True)
class RestResult:
    status: str
    schedule: tuple[tuple[int, ...], ...]


def _strict_transitions() -> list[tuple[int, int, int]]:
    return [
        # state 0 (OFF yesterday) — anything today.
        (0, OFF, 0),
        (0, DAY, 1),
        (0, NIGHT, 2),
        # state 1 (DAY yesterday) — anything today.
        (1, OFF, 0),
        (1, DAY, 1),
        (1, NIGHT, 2),
        # state 2 (NIGHT yesterday) — must be OFF today.
        (2, OFF, 0),
    ]


def solve_with_strict_rest(
    instance: ShiftInstance = DEFAULT_INSTANCE,
) -> RestResult:
    """Solve with NIGHT -> OFF mandatory the next day."""
    model = cp_model.CpModel()
    n_nurses, n_days = instance.n_nurses, instance.n_days

    assign = [
        [model.new_int_var(0, 2, f"a_{n}_{d}") for d in range(n_days)]
        for n in range(n_nurses)
    ]
    shift_bools: list[list[dict[int, cp_model.IntVar]]] = []
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
        shift_bools.append(per_day)

    for d in range(n_days):
        for s in (DAY, NIGHT):
            model.add(
                sum(shift_bools[n][d][s] for n in range(n_nurses)) >= instance.coverage_min
            )

    transitions = _strict_transitions()
    for n in range(n_nurses):
        model.add_automaton(
            assign[n], starting_state=0, final_states=[0, 1, 2], transition_triples=transitions
        )

    for n in range(n_nurses):
        model.add(
            sum(
                shift_bools[n][d][DAY] + shift_bools[n][d][NIGHT] for d in range(n_days)
            )
            <= instance.max_shifts_per_nurse
        )

    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = 5.0
    solver.parameters.random_seed = 42
    status = solver.solve(model)

    if status in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        schedule = tuple(
            tuple(int(solver.value(assign[n][d])) for d in range(n_days))
            for n in range(n_nurses)
        )
        return RestResult(status=solver.status_name(status), schedule=schedule)

    return RestResult(status=solver.status_name(status), schedule=())


def violates_strict_rest(schedule: tuple[tuple[int, ...], ...]) -> bool:
    """Return True iff a nurse works any non-OFF day right after a NIGHT."""
    for row in schedule:
        for prev, curr in zip(row, row[1:], strict=False):
            if prev == NIGHT and curr != OFF:
                return True
    return False
