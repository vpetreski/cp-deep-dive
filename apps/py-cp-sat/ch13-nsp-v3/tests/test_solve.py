"""Chapter 13 tests - benchmark harness + solver-tuning drills.

Covers the chapter's main entry point and the four exercise solutions.
Every test uses short time limits and small worker counts so the suite
stays well under CI budgets.
"""

from __future__ import annotations

import importlib
import pathlib

import pytest

DATA = pathlib.Path(__file__).resolve().parents[4] / "data" / "nsp"


def test_main_runs_on_toy_01() -> None:
    """Harness runs the five configs with a tight time limit, returns 0."""
    from py_cp_sat_ch13.main import main

    rc = main(
        [
            str(DATA / "toy-01.json"),
            "--time-limit",
            "3",
        ]
    )
    assert rc == 0


def test_main_runs_on_toy_02() -> None:
    from py_cp_sat_ch13.main import main

    rc = main(
        [
            str(DATA / "toy-02.json"),
            "--time-limit",
            "3",
        ]
    )
    assert rc == 0


@pytest.mark.parametrize(
    "module_name",
    [
        "py_cp_sat_ch13.solutions.exercise_13_a_num_workers",
        "py_cp_sat_ch13.solutions.exercise_13_b_linearization",
        "py_cp_sat_ch13.solutions.exercise_13_c_symmetry",
        "py_cp_sat_ch13.solutions.exercise_13_d_infeasibility_cores",
    ],
)
def test_exercise_solution_runs(module_name: str) -> None:
    """Each solution script runs cleanly on toy-02."""
    module = importlib.import_module(module_name)
    module.main()  # raises if the script misbehaves
