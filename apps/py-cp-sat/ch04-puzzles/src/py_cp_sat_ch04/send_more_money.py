"""SEND + MORE = MONEY via CP-SAT.

Each letter is a distinct digit 0-9. S and M can't be zero (no leading zeros).
The puzzle has exactly one solution: 9567 + 1085 = 10652.
"""

from __future__ import annotations

from dataclasses import dataclass, field

from ortools.sat.python import cp_model

_OK = (cp_model.OPTIMAL, cp_model.FEASIBLE)

_LETTERS = ("S", "E", "N", "D", "M", "O", "R", "Y")


@dataclass(frozen=True)
class SendMoreMoneyResult:
    """The unique letter-to-digit assignment."""

    status: str
    assignment: dict[str, int] = field(default_factory=dict)


def solve_send_more_money() -> SendMoreMoneyResult:
    """Solve the classic SEND + MORE = MONEY cryptarithm.

    Expected: S=9, E=5, N=6, D=7, M=1, O=0, R=8, Y=2.
    """
    model = cp_model.CpModel()
    variables = {letter: model.new_int_var(0, 9, letter) for letter in _LETTERS}

    model.add_all_different(list(variables.values()))
    model.add(variables["S"] >= 1)
    model.add(variables["M"] >= 1)

    send = (
        1000 * variables["S"]
        + 100 * variables["E"]
        + 10 * variables["N"]
        + variables["D"]
    )
    more = (
        1000 * variables["M"]
        + 100 * variables["O"]
        + 10 * variables["R"]
        + variables["E"]
    )
    money = (
        10000 * variables["M"]
        + 1000 * variables["O"]
        + 100 * variables["N"]
        + 10 * variables["E"]
        + variables["Y"]
    )
    model.add(send + more == money)

    solver = cp_model.CpSolver()
    solver.parameters.random_seed = 42
    status = solver.solve(model)
    if status not in _OK:
        return SendMoreMoneyResult(status=solver.status_name(status))
    return SendMoreMoneyResult(
        status=solver.status_name(status),
        assignment={k: int(solver.value(v)) for k, v in variables.items()},
    )
