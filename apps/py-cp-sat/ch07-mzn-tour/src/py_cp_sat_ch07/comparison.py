"""Compare MiniZinc output against the Python CP-SAT chapter code.

Both runtimes should produce the *same* optimum on the same data. The
helpers here extract enough signal from the MiniZinc stdout to confirm parity
without brittle string matching.
"""

from __future__ import annotations

import re
from dataclasses import dataclass


@dataclass(frozen=True)
class KnapsackParity:
    """Side-by-side knapsack outcome."""

    minizinc_value: int
    python_value: int

    @property
    def agrees(self) -> bool:
        return self.minizinc_value == self.python_value


def parse_knapsack_value(stdout: str) -> int:
    """Extract the ``value = 53`` line printed by ``knapsack.mzn``."""
    match = re.search(r"value\s*=\s*(-?\d+)", stdout)
    if match is None:
        raise ValueError(f"could not find value = ... in MiniZinc output:\n{stdout}")
    return int(match.group(1))


@dataclass(frozen=True)
class SendMoreDigits:
    """Assignment extracted from ``sendmore.mzn`` stdout."""

    digits: dict[str, int]

    @property
    def send(self) -> int:
        d = self.digits
        return 1000 * d["S"] + 100 * d["E"] + 10 * d["N"] + d["D"]

    @property
    def more(self) -> int:
        d = self.digits
        return 1000 * d["M"] + 100 * d["O"] + 10 * d["R"] + d["E"]

    @property
    def money(self) -> int:
        d = self.digits
        return 10000 * d["M"] + 1000 * d["O"] + 100 * d["N"] + 10 * d["E"] + d["Y"]


def parse_sendmore(stdout: str) -> SendMoreDigits:
    """Extract the 8 letter assignments from the ``sendmore.mzn`` output."""
    digits: dict[str, int] = {}
    for letter in ("S", "E", "N", "D", "M", "O", "R", "Y"):
        match = re.search(rf"{letter}\s*=\s*(-?\d+)", stdout)
        if match is None:
            raise ValueError(
                f"could not find {letter} = ... in MiniZinc output:\n{stdout}"
            )
        digits[letter] = int(match.group(1))
    return SendMoreDigits(digits=digits)
