"""Tests for the Gantt-chart renderer."""

from __future__ import annotations

from pathlib import Path

import pytest
from py_cp_sat_ch09.gantt import render_gantt
from py_cp_sat_ch09.instances import DEMO_3X3
from py_cp_sat_ch09.jobshop import JobShopSolution, solve_jobshop


def test_render_gantt_writes_png(tmp_path: Path) -> None:
    sol = solve_jobshop(DEMO_3X3)
    out = render_gantt(sol, tmp_path / "gantt.png")
    assert out.exists()
    assert out.stat().st_size > 1000  # non-empty PNG


def test_render_gantt_refuses_empty_schedule(tmp_path: Path) -> None:
    empty = JobShopSolution(
        status="INFEASIBLE", makespan=None, schedule=(), wall_time_s=0.0
    )
    with pytest.raises(ValueError):
        render_gantt(empty, tmp_path / "nope.png")
