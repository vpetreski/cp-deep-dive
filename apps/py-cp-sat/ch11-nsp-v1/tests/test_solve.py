"""Chapter 11 tests — solve toy-01 and toy-02 with hard constraints only.

Each test builds + solves the instance end-to-end, asserts the status is
FEASIBLE or OPTIMAL, then runs the standalone validator to certify the
Schedule against HC-1..HC-8 (so we know the solver didn't silently let a
constraint slide).
"""

from __future__ import annotations

import pathlib

from nsp_core import SolveParams, SolveStatus, load_instance, solve, validate_schedule

DATA = pathlib.Path(__file__).resolve().parents[4] / "data" / "nsp"


def _run(instance_path: pathlib.Path) -> None:
    instance = load_instance(instance_path)
    result = solve(
        instance,
        SolveParams(time_limit_seconds=15.0, num_workers=4, random_seed=42),
        objective="hard",
    )
    assert result.status in {SolveStatus.OPTIMAL, SolveStatus.FEASIBLE}, (
        f"{instance_path.name}: got status {result.status}"
    )
    assert result.schedule is not None
    violations = validate_schedule(instance, result.schedule)
    hard = [v for v in violations if v.severity == "hard"]
    assert not hard, f"{instance_path.name}: hard violations: {hard}"


def test_toy_01_feasible() -> None:
    _run(DATA / "toy-01.json")


def test_toy_02_feasible() -> None:
    _run(DATA / "toy-02.json")


def test_solve_is_deterministic_with_fixed_seed() -> None:
    """Same seed + same params → same objective value. (Hard model has none, so
    just confirm both runs return a feasible schedule in the same status.)"""
    instance = load_instance(DATA / "toy-01.json")
    params = SolveParams(time_limit_seconds=10.0, num_workers=1, random_seed=42)
    r1 = solve(instance, params, objective="hard")
    r2 = solve(instance, params, objective="hard")
    assert r1.status == r2.status
    assert r1.schedule is not None and r2.schedule is not None


def test_main_runs_without_error() -> None:
    """`python -m py_cp_sat_ch11` should exit 0 on the default toy instance."""
    from py_cp_sat_ch11.main import main

    rc = main(["--time-limit", "10", "--workers", "2", str(DATA / "toy-01.json")])
    assert rc == 0
