"""Manual lexicographic-less-or-equal constraint (no native helper in this
``ortools`` release).

The OR-Tools Python binding shipped at the time of this chapter lacks
``add_lexicographic_less_equal``, so we post an equivalent encoding by hand.

Given two equal-length arrays ``a`` and ``b``, ``a ≤_lex b`` iff there exists
a prefix ``a[0..k-1] == b[0..k-1]`` with either ``a[k] < b[k]`` or ``k == n``
(full equality). We post this via boolean "still-equal" indicators:

    eq[0] = True
    eq[i+1] = eq[i] AND (a[i] == b[i])
    when eq[i] is true: a[i] ≤ b[i]

The final step ``a[i] ≤ b[i]`` happens implicitly because if any position
forces ``a[i] < b[i]`` with ``eq[i]`` true, subsequent positions are free; if
``a == b`` all the way, the last ``a[n-1] ≤ b[n-1]`` is tight but satisfied.
"""

from __future__ import annotations

from collections.abc import Sequence

from ortools.sat.python import cp_model


def add_lex_less_equal(
    model: cp_model.CpModel,
    a: Sequence[cp_model.IntVar],
    b: Sequence[cp_model.IntVar],
) -> None:
    """Post ``a ≤_lex b`` with the boolean-prefix encoding described above."""
    if len(a) != len(b):
        raise ValueError("lex: arrays must have the same length")
    n = len(a)
    if n == 0:
        return

    # eq[i] <=> a[0..i-1] == b[0..i-1].
    eq = [model.new_bool_var(f"lex_eq_{i}") for i in range(n + 1)]
    model.add(eq[0] == 1)
    for i in range(n):
        # While equal, a[i] <= b[i].
        model.add(a[i] <= b[i]).only_enforce_if(eq[i])
        # eq[i+1] = eq[i] AND (a[i] == b[i]).
        same_here = model.new_bool_var(f"lex_same_{i}")
        model.add(a[i] == b[i]).only_enforce_if(same_here)
        model.add(a[i] != b[i]).only_enforce_if(~same_here)
        model.add_bool_and([eq[i], same_here]).only_enforce_if(eq[i + 1])
        model.add_bool_or([~eq[i], ~same_here]).only_enforce_if(~eq[i + 1])


def count_schedules(
    *, n_nurses: int = 5, days: int = 7, min_work: int = 3, use_lex: bool
) -> int:
    """Enumerate all feasible schedules; with or without lex symmetry breaking.

    Each nurse has a 7-day binary schedule (1 = working, 0 = off); constraint
    is that every nurse logs at least ``min_work`` working days. Without lex
    breaking every solution has ``n_nurses!`` permutation twins (interchangeable
    nurses). With lex breaking only the canonical ordering survives.
    """
    if n_nurses <= 0 or days <= 0:
        raise ValueError("n_nurses and days must be positive")

    model = cp_model.CpModel()
    schedules: list[list[cp_model.IntVar]] = []
    for n_idx in range(n_nurses):
        row = [model.new_int_var(0, 1, f"s_{n_idx}_{d}") for d in range(days)]
        schedules.append(row)
        model.add(sum(row) >= min_work)

    if use_lex:
        for n_idx in range(n_nurses - 1):
            add_lex_less_equal(model, schedules[n_idx], schedules[n_idx + 1])

    solver = cp_model.CpSolver()
    solver.parameters.random_seed = 42
    solver.parameters.enumerate_all_solutions = True
    solver.parameters.num_search_workers = 1

    count = 0

    class _Counter(cp_model.CpSolverSolutionCallback):
        def on_solution_callback(self) -> None:  # noqa: D401 — OR-Tools API
            nonlocal count
            count += 1

    solver.solve(model, _Counter())
    return count
