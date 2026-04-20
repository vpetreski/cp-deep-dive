"""Chapter 10 — Boolean shift grid + transition rules via an automaton.

Shape of the model
------------------

The grid is a 3D array of booleans indexed by ``(nurse, day, shift)``:

    ``work[n, d, s] == 1``  ⇔  nurse ``n`` works shift ``s`` on day ``d``

Shifts are: ``0 = OFF`` (resting — not an actual shift, but we carry it so the
automaton always sees a symbol per day), ``1 = DAY``, ``2 = NIGHT``.

Per-day rule: exactly one of ``OFF``, ``DAY``, ``NIGHT`` per (nurse, day). We
encode this with an IntVar ``assign[n, d] ∈ {0, 1, 2}`` tied to the booleans
via ``OnlyEnforceIf``.

Transition rule (the thing this chapter is really about):
    *A nurse who works a NIGHT shift cannot go straight to a DAY shift the
    next day* — they must rest (OFF) first. This is enforced with
    ``AddAutomaton`` on each nurse's weekly sequence. Any other transition is
    allowed.

Coverage (HC-1): for every (day, shift ∈ {DAY, NIGHT}), at least
``coverage_min`` nurses are assigned.
"""

from __future__ import annotations

from dataclasses import dataclass

from ortools.sat.python import cp_model

# Shift labels used across the chapter.
OFF = 0
DAY = 1
NIGHT = 2

SHIFT_SYMBOL = {OFF: ".", DAY: "D", NIGHT: "N"}


@dataclass(frozen=True)
class ShiftInstance:
    n_nurses: int = 5
    n_days: int = 7
    coverage_min: int = 1  # At least this many nurses on each (day, shift in {DAY, NIGHT}).
    # Max shifts (DAY + NIGHT) per nurse across the week.
    max_shifts_per_nurse: int = 5


@dataclass(frozen=True)
class ShiftSolution:
    status: str
    # shape: (n_nurses, n_days) of values in {OFF, DAY, NIGHT}
    schedule: tuple[tuple[int, ...], ...]
    # Per-nurse total of DAY + NIGHT shifts.
    totals: tuple[int, ...]

    def render(self) -> str:
        rows = []
        for n, row in enumerate(self.schedule):
            rows.append(f"N{n}: " + " ".join(SHIFT_SYMBOL[v] for v in row))
        return "\n".join(rows)


def _build_transition_tuples() -> list[tuple[int, int, int]]:
    """DFA tuples for the 'no DAY after NIGHT' rule.

    States encode "what did I do yesterday":
        state 0 = start / was OFF
        state 1 = worked DAY yesterday
        state 2 = worked NIGHT yesterday

    Symbols = {OFF, DAY, NIGHT}. Arc ``state -> symbol -> next_state``.
    Forbidden: state 2 (NIGHT yesterday) + symbol DAY.
    """
    transitions: list[tuple[int, int, int]] = []
    for prev_state, (off_ok, day_ok, night_ok) in enumerate(
        [
            # from OFF yesterday (state 0)
            (True, True, True),
            # from DAY yesterday (state 1)
            (True, True, True),
            # from NIGHT yesterday (state 2) — DAY is forbidden.
            (True, False, True),
        ]
    ):
        if off_ok:
            transitions.append((prev_state, OFF, 0))
        if day_ok:
            transitions.append((prev_state, DAY, 1))
        if night_ok:
            transitions.append((prev_state, NIGHT, 2))
    return transitions


DEFAULT_INSTANCE = ShiftInstance()


def solve_shifts(instance: ShiftInstance = DEFAULT_INSTANCE) -> ShiftSolution:
    """Solve the coverage + transition-rule model for one week."""
    model = cp_model.CpModel()

    n_nurses = instance.n_nurses
    n_days = instance.n_days

    # assign[n][d] = OFF | DAY | NIGHT
    assign: list[list[cp_model.IntVar]] = [
        [
            model.new_int_var(0, 2, f"assign_{n}_{d}")
            for d in range(n_days)
        ]
        for n in range(n_nurses)
    ]

    # Indicator booleans for each concrete shift (to aggregate for coverage).
    # work[n][d][s] iff assign[n][d] == s.
    work: list[list[dict[int, cp_model.IntVar]]] = []
    for n in range(n_nurses):
        per_day: list[dict[int, cp_model.IntVar]] = []
        for d in range(n_days):
            entries: dict[int, cp_model.IntVar] = {}
            for s in (OFF, DAY, NIGHT):
                b = model.new_bool_var(f"work_{n}_{d}_s{s}")
                model.add(assign[n][d] == s).only_enforce_if(b)
                model.add(assign[n][d] != s).only_enforce_if(~b)
                entries[s] = b
            # Exactly one of the three booleans is true.
            model.add_exactly_one(list(entries.values()))
            per_day.append(entries)
        work.append(per_day)

    # Coverage — at least `coverage_min` nurses on each working shift per day.
    for d in range(n_days):
        for s in (DAY, NIGHT):
            model.add(sum(work[n][d][s] for n in range(n_nurses)) >= instance.coverage_min)

    # Transition rule via automaton on each nurse's weekly sequence.
    transitions = _build_transition_tuples()
    for n in range(n_nurses):
        model.add_automaton(
            assign[n], starting_state=0, final_states=[0, 1, 2], transition_triples=transitions
        )

    # Workload cap (HC-3 analogue) — keeps the demo instance honest.
    for n in range(n_nurses):
        model.add(
            sum(work[n][d][DAY] + work[n][d][NIGHT] for d in range(n_days))
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
        totals = tuple(
            sum(1 for v in row if v != OFF) for row in schedule
        )
        return ShiftSolution(
            status=solver.status_name(status),
            schedule=schedule,
            totals=totals,
        )

    return ShiftSolution(
        status=solver.status_name(status), schedule=(), totals=()
    )


def verify_no_day_after_night(solution: ShiftSolution) -> bool:
    """Standalone property check: no DAY directly after a NIGHT."""
    for row in solution.schedule:
        for prev, curr in zip(row, row[1:], strict=False):
            if prev == NIGHT and curr == DAY:
                return False
    return True
