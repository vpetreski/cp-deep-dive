"""Parity test for MiniZinc prototype vs Python port."""

from __future__ import annotations

import pytest
from py_cp_sat_ch07.mzn_runner import minizinc_available, run_model
from py_cp_sat_ch08.parity import parse_toy_nsp_output
from py_cp_sat_ch08.toy_nsp import DEMO_INSTANCE, solve_toy_nsp


def test_parse_toy_nsp_extracts_spread_and_totals() -> None:
    stdout = "spread=2\ntotals=[4, 5, 5]\nwork=[...]\n----------\n"
    outcome = parse_toy_nsp_output(stdout)
    assert outcome.spread == 2
    assert outcome.totals == [4, 5, 5]


def test_parse_toy_nsp_raises_on_missing_spread() -> None:
    with pytest.raises(ValueError):
        parse_toy_nsp_output("totals=[1, 2, 3]")


@pytest.mark.skipif(not minizinc_available(), reason="minizinc binary not installed")
def test_minizinc_and_python_agree_on_spread() -> None:
    run = run_model("toy-nsp")
    assert run.returncode == 0, run.stdout
    mzn = parse_toy_nsp_output(run.stdout)
    py = solve_toy_nsp(DEMO_INSTANCE)
    assert py.spread == mzn.spread
