"""Smoke test for the Chapter 06 entry point."""

from __future__ import annotations


def test_main_runs_without_error(capsys) -> None:  # type: ignore[no-untyped-def]
    from py_cp_sat_ch06.main import main

    main()
    out = capsys.readouterr().out
    for section in (
        "Circuit:",
        "Table:",
        "Element:",
        "Automaton:",
        "Inverse:",
        "LexLeq:",
        "Reservoir:",
    ):
        assert section in out
