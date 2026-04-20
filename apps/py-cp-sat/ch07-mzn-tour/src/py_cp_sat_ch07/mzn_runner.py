"""Run MiniZinc models via subprocess.

The goal of Chapter 07 is to show that the **same problem** can be described
declaratively in MiniZinc and imperatively via CP-SAT, and that both land on
the same solutions. This module is the tiny plumbing layer — it locates the
``apps/mzn`` folder, invokes the ``minizinc`` binary, and captures the stdout
output produced by each model's ``output [...]`` clause.

MiniZinc may not be installed on every developer machine. The helpers here
*skip gracefully* — they raise :class:`MinizincNotInstalled`, which callers
(and pytest) should catch or skip on.
"""

from __future__ import annotations

import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path


class MinizincNotInstalled(RuntimeError):
    """Raised when ``minizinc`` is not on ``$PATH`` — tests should skip."""


@dataclass(frozen=True)
class MznRun:
    """Outcome of one MiniZinc invocation."""

    model: str
    solver: str | None
    stdout: str
    returncode: int


def mzn_dir() -> Path:
    """Absolute path of the ``apps/mzn`` folder relative to this file."""
    here = Path(__file__).resolve()
    # apps/py-cp-sat/ch07-mzn-tour/src/py_cp_sat_ch07/mzn_runner.py → apps/mzn
    return here.parents[4] / "mzn"


def minizinc_available() -> bool:
    """Return True iff ``minizinc`` binary is resolvable on ``$PATH``."""
    return shutil.which("minizinc") is not None


def run_model(
    model_name: str, *, solver: str | None = None, timeout: float = 60.0
) -> MznRun:
    """Run a MiniZinc model by name (e.g. ``"nqueens"``), returning its output.

    Looks for ``apps/mzn/<model_name>.mzn`` (required) and
    ``apps/mzn/<model_name>.dzn`` (optional) and passes them to ``minizinc``.
    """
    if not minizinc_available():
        raise MinizincNotInstalled("minizinc binary not found on PATH")

    mzn = mzn_dir() / f"{model_name}.mzn"
    dzn = mzn_dir() / f"{model_name}.dzn"
    if not mzn.exists():
        raise FileNotFoundError(f"MiniZinc model not found: {mzn}")

    cmd: list[str] = ["minizinc"]
    if solver is not None:
        cmd += ["--solver", solver]
    cmd.append(str(mzn))
    if dzn.exists():
        cmd.append(str(dzn))

    completed = subprocess.run(
        cmd,
        check=False,
        capture_output=True,
        text=True,
        timeout=timeout,
    )
    return MznRun(
        model=model_name,
        solver=solver,
        stdout=completed.stdout,
        returncode=completed.returncode,
    )
