"""Smoke test for Chapter 10."""

from __future__ import annotations


def test_main_runs_without_error(capsys) -> None:  # type: ignore[no-untyped-def]
    from py_cp_sat_ch10.main import main

    main()
    out = capsys.readouterr().out
    assert "Shifts" in out
    assert "Totals" in out
