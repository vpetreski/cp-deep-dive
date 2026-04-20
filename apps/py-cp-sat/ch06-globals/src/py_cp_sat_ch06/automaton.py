"""Automaton — "no more than 3 consecutive night shifts" over a 14-day run.

Labels: ``D`` (day) = 0, ``E`` (evening) = 1, ``N`` (night) = 2, ``O`` (off) = 3.

DFA states count consecutive N runs: 0, 1, 2, 3, and implicit BAD.
"""

from __future__ import annotations

from dataclasses import dataclass

from ortools.sat.python import cp_model

LABEL_DAY: int = 0
LABEL_EVENING: int = 1
LABEL_NIGHT: int = 2
LABEL_OFF: int = 3

DAYS: int = 14


@dataclass(frozen=True)
class AutomatonResult:
    status: str
    schedule: list[int]  # one label per day


def _build_max_consecutive_nights_dfa(
    max_consecutive: int,
) -> tuple[int, list[int], list[tuple[int, int, int]]]:
    """Return ``(start_state, final_states, transitions)`` for the DFA.

    States 0..max_consecutive are "number of nights in current run". Entering
    state ``max_consecutive + 1`` (BAD) has no outgoing transitions, so any
    sequence reaching it is rejected.
    """
    start = 0
    final_states = list(range(max_consecutive + 1))
    transitions: list[tuple[int, int, int]] = []
    # From each "good" state, D/E/O resets to state 0.
    for state in final_states:
        for label in (LABEL_DAY, LABEL_EVENING, LABEL_OFF):
            transitions.append((state, label, 0))
    # N advances the counter up to max_consecutive; beyond that would be BAD.
    for state in range(max_consecutive):
        transitions.append((state, LABEL_NIGHT, state + 1))
    return start, final_states, transitions


def solve_night_pattern(
    days: int = DAYS,
    max_consecutive: int = 3,
    *,
    min_total_nights: int = 3,
    time_limit: float = 5.0,
) -> AutomatonResult:
    """Find a ``days``-day schedule with ≤ ``max_consecutive`` nights in a row.

    The extra ``min_total_nights`` constraint forces at least some night shifts
    into the schedule — otherwise the trivial "all-off" schedule wins.
    """
    if days <= 0:
        raise ValueError("days must be positive")
    if max_consecutive < 1:
        raise ValueError("max_consecutive must be ≥ 1")

    model = cp_model.CpModel()
    schedule = [model.new_int_var(0, 3, f"day_{d}") for d in range(days)]

    start, finals, transitions = _build_max_consecutive_nights_dfa(max_consecutive)
    model.add_automaton(schedule, start, finals, transitions)

    # Count nights via booleans so we can apply the minimum-nights lower bound.
    night_bools = []
    for d, day_var in enumerate(schedule):
        b = model.new_bool_var(f"is_night_{d}")
        model.add(day_var == LABEL_NIGHT).only_enforce_if(b)
        model.add(day_var != LABEL_NIGHT).only_enforce_if(~b)
        night_bools.append(b)
    model.add(sum(night_bools) >= min_total_nights)

    solver = cp_model.CpSolver()
    solver.parameters.random_seed = 42
    solver.parameters.max_time_in_seconds = time_limit
    status = solver.solve(model)
    status_name = solver.status_name(status)
    if status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        return AutomatonResult(status=status_name, schedule=[])
    return AutomatonResult(
        status=status_name,
        schedule=[int(solver.value(v)) for v in schedule],
    )


def label_name(label: int) -> str:
    return {LABEL_DAY: "D", LABEL_EVENING: "E", LABEL_NIGHT: "N", LABEL_OFF: "O"}[label]
