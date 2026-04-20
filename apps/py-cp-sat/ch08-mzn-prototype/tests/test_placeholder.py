"""Smoke test for Chapter 08."""

from __future__ import annotations


def test_main_runs_without_error(capsys) -> None:  # type: ignore[no-untyped-def]
    from py_cp_sat_ch08.main import main

    main()
    out = capsys.readouterr().out
    assert "Toy NSP (Python CP-SAT)" in out
