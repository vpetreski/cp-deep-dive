"""Chapter 04 — smoke test that ``main()`` runs end-to-end."""

from __future__ import annotations

from py_cp_sat_ch04.main import solve


def test_main_runs_without_error() -> None:
    demo = solve()
    assert demo.n_queens.status in {"OPTIMAL", "FEASIBLE"}
    assert demo.send_more.status in {"OPTIMAL", "FEASIBLE"}
    assert demo.sudoku.status in {"OPTIMAL", "FEASIBLE"}
