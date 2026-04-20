"""Reservoir — running-sum demand with lo/hi fence.

Models a tank whose level must stay in ``[min_level, max_level]`` after every
event. Each event has a time (variable), a delta (fixed sign), and an active
flag (here always true for simplicity).
"""

from __future__ import annotations

from dataclasses import dataclass

from ortools.sat.python import cp_model


@dataclass(frozen=True)
class ReservoirResult:
    status: str
    event_times: list[int]


def solve_reservoir_schedule(
    *,
    horizon: int = 20,
    deltas: tuple[int, ...] = (5, -3, 4, -6),
    min_level: int = 0,
    max_level: int = 10,
    time_limit: float = 5.0,
) -> ReservoirResult:
    """Place a sequence of delta-events within ``[0, horizon]`` respecting level bounds."""
    if horizon <= 0:
        raise ValueError("horizon must be positive")

    model = cp_model.CpModel()
    times = [model.new_int_var(0, horizon, f"t_{i}") for i in range(len(deltas))]
    model.add_reservoir_constraint(times, list(deltas), min_level, max_level)
    # Enforce event order t_0 < t_1 < ... so results are deterministic.
    for a, b in zip(times, times[1:], strict=False):
        model.add(a < b)

    solver = cp_model.CpSolver()
    solver.parameters.random_seed = 42
    solver.parameters.max_time_in_seconds = time_limit
    status = solver.solve(model)
    status_name = solver.status_name(status)
    if status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        return ReservoirResult(status=status_name, event_times=[])
    return ReservoirResult(
        status=status_name,
        event_times=[int(solver.value(t)) for t in times],
    )
