"""Table — allowed ``(nurse, ward, skill)`` tuples.

Uses :py:meth:`CpModel.add_allowed_assignments` to restrict a vector of
``IntVar``s to a finite set of allowed rows. Typical use case: policy /
compatibility tables that are too irregular to express with linear constraints
but small enough to enumerate.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from ortools.sat.python import cp_model

# (nurse_id, ward_id, minimum_skill)
DEMO_ALLOWED: tuple[tuple[int, int, int], ...] = (
    (0, 0, 1),
    (0, 1, 1),
    (0, 2, 2),
    (1, 1, 1),
    (1, 2, 1),
    (1, 3, 3),
    (2, 0, 2),
    (2, 2, 3),
    (2, 4, 2),
    (3, 0, 1),
    (3, 3, 2),
    (4, 1, 2),
    (4, 4, 3),
)


@dataclass(frozen=True)
class TableResult:
    """Result of a best-skill table lookup."""

    status: str
    nurse: int
    ward: int
    skill: int


def solve_best_skill(
    allowed: Sequence[tuple[int, int, int]] = DEMO_ALLOWED,
    *,
    time_limit: float = 5.0,
) -> TableResult:
    """Find the highest-skill ``(nurse, ward, skill)`` combination in ``allowed``."""
    if not allowed:
        raise ValueError("allowed must be non-empty")

    n_max = max(row[0] for row in allowed)
    w_max = max(row[1] for row in allowed)
    s_min = min(row[2] for row in allowed)
    s_max = max(row[2] for row in allowed)

    model = cp_model.CpModel()
    nurse = model.new_int_var(0, n_max, "nurse")
    ward = model.new_int_var(0, w_max, "ward")
    skill = model.new_int_var(s_min, s_max, "skill")
    model.add_allowed_assignments([nurse, ward, skill], [list(row) for row in allowed])
    model.maximize(skill)

    solver = cp_model.CpSolver()
    solver.parameters.random_seed = 42
    solver.parameters.max_time_in_seconds = time_limit
    status = solver.solve(model)
    status_name = solver.status_name(status)
    if status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        return TableResult(status=status_name, nurse=-1, ward=-1, skill=-1)
    return TableResult(
        status=status_name,
        nurse=int(solver.value(nurse)),
        ward=int(solver.value(ward)),
        skill=int(solver.value(skill)),
    )
