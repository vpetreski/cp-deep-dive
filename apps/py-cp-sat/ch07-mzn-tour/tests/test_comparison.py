"""Tests for MiniZinc-stdout parsers."""

from __future__ import annotations

import pytest
from py_cp_sat_ch07.comparison import parse_knapsack_value, parse_sendmore


def test_parse_knapsack_value_extracts_integer() -> None:
    stdout = "value = 53\nweight = 20\npick = [1, 4, 6, 8, 13, 15]\n----------\n"
    assert parse_knapsack_value(stdout) == 53


def test_parse_sendmore_extracts_all_eight_letters() -> None:
    stdout = "S=9 E=5 N=6 D=7 M=1 O=0 R=8 Y=2\n"
    result = parse_sendmore(stdout)
    assert result.digits == {
        "S": 9, "E": 5, "N": 6, "D": 7, "M": 1, "O": 0, "R": 8, "Y": 2
    }
    assert result.send == 9567
    assert result.more == 1085
    assert result.money == 10652
    assert result.send + result.more == result.money


def test_parse_knapsack_raises_on_missing_value() -> None:
    with pytest.raises(ValueError):
        parse_knapsack_value("nothing here")


def test_parse_sendmore_raises_on_missing_letter() -> None:
    with pytest.raises(ValueError):
        parse_sendmore("S=9 E=5 N=6 D=7")  # missing several
