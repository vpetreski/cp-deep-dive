"""Inverse — tasks ↔ nurses bijection.

Forward ``nurse_of_task[t]`` and backward ``task_of_nurse[n]`` are both
permutations. Posting ``add_inverse`` channels them together so propagation
benefits from both viewpoints.
"""

from __future__ import annotations

from dataclasses import dataclass

from ortools.sat.python import cp_model


@dataclass(frozen=True)
class InverseResult:
    status: str
    nurse_of_task: list[int]
    task_of_nurse: list[int]


def solve_simple_bijection(
    n: int = 5, *, pinned: dict[int, int] | None = None, time_limit: float = 5.0
) -> InverseResult:
    """Construct a bijection ``tasks → nurses`` with optional pin constraints.

    ``pinned`` maps ``task_id -> nurse_id``. The solver completes the bijection.
    """
    if n <= 0:
        raise ValueError("n must be positive")
    if pinned is None:
        pinned = {}
    for task, nurse in pinned.items():
        if not 0 <= task < n or not 0 <= nurse < n:
            raise ValueError(f"pinned entry ({task}, {nurse}) out of [0, {n})")

    model = cp_model.CpModel()
    nurse_of_task = [model.new_int_var(0, n - 1, f"nurse_of_task_{t}") for t in range(n)]
    task_of_nurse = [model.new_int_var(0, n - 1, f"task_of_nurse_{nn}") for nn in range(n)]
    model.add_inverse(nurse_of_task, task_of_nurse)

    for task, nurse in pinned.items():
        model.add(nurse_of_task[task] == nurse)

    solver = cp_model.CpSolver()
    solver.parameters.random_seed = 42
    solver.parameters.max_time_in_seconds = time_limit
    status = solver.solve(model)
    status_name = solver.status_name(status)
    if status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        return InverseResult(status=status_name, nurse_of_task=[], task_of_nurse=[])
    return InverseResult(
        status=status_name,
        nurse_of_task=[int(solver.value(v)) for v in nurse_of_task],
        task_of_nurse=[int(solver.value(v)) for v in task_of_nurse],
    )
