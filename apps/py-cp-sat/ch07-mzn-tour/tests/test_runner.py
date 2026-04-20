"""Tests for the MiniZinc runner.

These tests are designed to work whether or not ``minizinc`` is installed:
    - Path-resolution tests run everywhere.
    - Live-invocation tests are guarded by ``pytest.importorskip`` /
      explicit ``minizinc_available()`` checks.
"""

from __future__ import annotations

import pytest
from py_cp_sat_ch07.mzn_runner import (
    MinizincNotInstalled,
    minizinc_available,
    mzn_dir,
    run_model,
)


def test_mzn_dir_exists_and_contains_models() -> None:
    mdir = mzn_dir()
    assert mdir.is_dir(), f"expected {mdir} to exist"
    assert (mdir / "nqueens.mzn").is_file()
    assert (mdir / "knapsack.mzn").is_file()
    assert (mdir / "sendmore.mzn").is_file()
    assert (mdir / "toy-nsp.mzn").is_file()


def test_run_model_raises_when_missing() -> None:
    if not minizinc_available():
        with pytest.raises(MinizincNotInstalled):
            run_model("nqueens")
    else:
        # When installed, a bogus model name should raise FileNotFoundError.
        with pytest.raises(FileNotFoundError):
            run_model("this-model-does-not-exist-xyzzy")


@pytest.mark.skipif(not minizinc_available(), reason="minizinc binary not installed")
def test_nqueens_live_run() -> None:
    run = run_model("nqueens")
    assert run.returncode == 0, run.stdout
    # The model's output clause prints "q = [...]".
    assert "q = " in run.stdout
