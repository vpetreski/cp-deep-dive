"""Smoke test for Chapter 07."""

from __future__ import annotations


def test_main_runs_without_error(capsys) -> None:  # type: ignore[no-untyped-def]
    """Either MiniZinc is installed and we print parity, or we print the banner."""
    from py_cp_sat_ch07.main import main

    main()
    out = capsys.readouterr().out
    assert out.strip(), "chapter main must print something"
