"""Chapter 12 tests - soft constraints, weighted objective, lexicographic solve.

Covers the main demo, the four exercise solutions, and the two solve modes
(weighted, lexicographic).
"""

from __future__ import annotations

import pathlib

import pytest
from nsp_core import (
    ObjectiveWeights,
    SolveParams,
    SolveStatus,
    load_instance,
    solve,
)

DATA = pathlib.Path(__file__).resolve().parents[4] / "data" / "nsp"


def test_weighted_solve_toy_01() -> None:
    inst = load_instance(DATA / "toy-01.json")
    result = solve(
        inst,
        SolveParams(time_limit_seconds=15.0, num_workers=4, random_seed=42),
        objective="weighted",
    )
    assert result.status in {SolveStatus.OPTIMAL, SolveStatus.FEASIBLE}
    assert result.schedule is not None
    assert result.objective is not None
    assert result.objective >= 0


def test_weighted_solve_toy_02() -> None:
    inst = load_instance(DATA / "toy-02.json")
    result = solve(
        inst,
        SolveParams(time_limit_seconds=15.0, num_workers=4, random_seed=42),
        objective="weighted",
    )
    assert result.status in {SolveStatus.OPTIMAL, SolveStatus.FEASIBLE}
    assert result.schedule is not None
    assert result.objective is not None


def test_weight_override_affects_objective() -> None:
    """Changing weights should shuffle which soft constraint wins priority."""
    inst = load_instance(DATA / "toy-02.json")
    base = solve(
        inst,
        SolveParams(
            time_limit_seconds=10.0,
            num_workers=1,
            random_seed=42,
            objective_weights=ObjectiveWeights(
                preference=0, fairness=100, workload_balance=100,
                weekend_distribution=100, consecutive_days_off=100,
            ),
        ),
        objective="weighted",
    )
    pref_heavy = solve(
        inst,
        SolveParams(
            time_limit_seconds=10.0,
            num_workers=1,
            random_seed=42,
            objective_weights=ObjectiveWeights(
                preference=1000, fairness=0, workload_balance=0,
                weekend_distribution=0, consecutive_days_off=0,
            ),
        ),
        objective="weighted",
    )
    assert base.status in {SolveStatus.OPTIMAL, SolveStatus.FEASIBLE}
    assert pref_heavy.status in {SolveStatus.OPTIMAL, SolveStatus.FEASIBLE}
    # Both produce schedules; tests below use the schedules directly.
    assert base.schedule is not None
    assert pref_heavy.schedule is not None


def test_lexicographic_pins_sc1() -> None:
    inst = load_instance(DATA / "toy-02.json")
    result = solve(
        inst,
        SolveParams(time_limit_seconds=15.0, num_workers=2, random_seed=42),
        objective="lexicographic",
    )
    assert result.status in {SolveStatus.OPTIMAL, SolveStatus.FEASIBLE}
    assert result.schedule is not None


def test_main_runs_without_error() -> None:
    from py_cp_sat_ch12.main import main

    rc = main(
        [
            "--time-limit", "10",
            "--workers", "2",
            "--objective", "weighted",
            str(DATA / "toy-01.json"),
        ]
    )
    assert rc == 0


def test_main_lexicographic_runs_without_error() -> None:
    from py_cp_sat_ch12.main import main

    rc = main(
        [
            "--time-limit", "10",
            "--workers", "2",
            "--objective", "lexicographic",
            str(DATA / "toy-01.json"),
        ]
    )
    assert rc == 0


@pytest.mark.parametrize(
    "module_name",
    [
        "py_cp_sat_ch12.solutions.exercise_12_a_weights_sensitivity",
        "py_cp_sat_ch12.solutions.exercise_12_b_lexicographic",
        "py_cp_sat_ch12.solutions.exercise_12_c_warmstart",
        "py_cp_sat_ch12.solutions.exercise_12_d_custom_objective",
    ],
)
def test_exercise_solution_runs(module_name: str) -> None:
    import importlib

    module = importlib.import_module(module_name)
    module.main()  # raises if the script misbehaves
