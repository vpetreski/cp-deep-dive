"""Tests for SEND + MORE = MONEY.

The puzzle has exactly one solution: 9567 + 1085 = 10652.
"""

from __future__ import annotations

from py_cp_sat_ch04.send_more_money import solve_send_more_money


def test_status_is_optimal() -> None:
    result = solve_send_more_money()
    assert result.status in {"OPTIMAL", "FEASIBLE"}


def test_unique_expected_digits() -> None:
    result = solve_send_more_money()
    expected = {"S": 9, "E": 5, "N": 6, "D": 7, "M": 1, "O": 0, "R": 8, "Y": 2}
    assert result.assignment == expected


def test_all_digits_distinct() -> None:
    result = solve_send_more_money()
    digits = list(result.assignment.values())
    assert len(set(digits)) == len(digits)


def test_equation_balances() -> None:
    result = solve_send_more_money()
    a = result.assignment
    send = 1000 * a["S"] + 100 * a["E"] + 10 * a["N"] + a["D"]
    more = 1000 * a["M"] + 100 * a["O"] + 10 * a["R"] + a["E"]
    money = 10000 * a["M"] + 1000 * a["O"] + 100 * a["N"] + 10 * a["E"] + a["Y"]
    assert send + more == money
    assert send == 9567
    assert more == 1085
    assert money == 10652
