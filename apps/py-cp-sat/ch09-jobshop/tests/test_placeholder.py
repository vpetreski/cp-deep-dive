"""Smoke test for Chapter 09."""

from __future__ import annotations

from pathlib import Path


def test_main_runs_without_error(tmp_path: Path, capsys, monkeypatch) -> None:  # type: ignore[no-untyped-def]
    monkeypatch.setenv("CH09_OUT_DIR", str(tmp_path))
    from py_cp_sat_ch09.main import main

    main()
    out = capsys.readouterr().out
    assert "Job-shop 3x3" in out
    assert "Gantt chart written to" in out
    png = tmp_path / "ch09_5x4_gantt.png"
    assert png.exists() and png.stat().st_size > 0
