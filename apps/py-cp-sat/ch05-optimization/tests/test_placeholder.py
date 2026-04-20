"""Smoke test for the Chapter 05 entry point."""

from __future__ import annotations


def test_main_runs_without_error(capsys) -> None:  # type: ignore[no-untyped-def]
    """``python -m py_cp_sat_ch05`` prints both demos and exits cleanly."""
    from py_cp_sat_ch05.main import main

    main()
    captured = capsys.readouterr()
    assert "0/1 Knapsack" in captured.out
    assert "Bin Packing" in captured.out
