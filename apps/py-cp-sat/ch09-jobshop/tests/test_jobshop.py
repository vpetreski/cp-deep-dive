"""Tests for the core job-shop solver."""

from __future__ import annotations

from py_cp_sat_ch09.instances import DEMO_3X3, DEMO_5X4
from py_cp_sat_ch09.jobshop import solve_jobshop, verify_schedule


def test_3x3_reaches_textbook_optimum() -> None:
    sol = solve_jobshop(DEMO_3X3)
    assert sol.status == "OPTIMAL"
    assert sol.makespan == 11
    assert verify_schedule(DEMO_3X3, sol)


def test_5x4_finds_feasible_schedule() -> None:
    sol = solve_jobshop(DEMO_5X4)
    assert sol.status in {"OPTIMAL", "FEASIBLE"}
    assert sol.makespan is not None
    # Trivial lower bound: longest job's total processing time.
    per_job = [sum(op.duration for op in job) for job in DEMO_5X4.jobs]
    assert sol.makespan >= max(per_job)
    assert verify_schedule(DEMO_5X4, sol)


def test_verify_schedule_catches_bad_solution() -> None:
    from dataclasses import replace

    good = solve_jobshop(DEMO_3X3)
    assert good.schedule
    # Stomp the makespan — schedule no longer matches the claim.
    tampered = replace(good, makespan=(good.makespan or 0) - 1)
    assert not verify_schedule(DEMO_3X3, tampered)
