"""Parity comparison between ``apps/mzn/toy-nsp.mzn`` and the Python port.

The MiniZinc model's ``output [...]`` clause prints ``spread=N`` and
``totals=[a, b, c]``. The helpers here extract both, so the test can confirm
identical optima across runtimes.
"""

from __future__ import annotations

import re
from dataclasses import dataclass


@dataclass(frozen=True)
class MznToyNspOutcome:
    spread: int
    totals: list[int]


def parse_toy_nsp_output(stdout: str) -> MznToyNspOutcome:
    """Extract ``spread`` and ``totals`` from the MiniZinc output."""
    spread_match = re.search(r"spread\s*=\s*(-?\d+)", stdout)
    if spread_match is None:
        raise ValueError(
            f"could not find spread=... in MiniZinc toy-nsp output:\n{stdout}"
        )
    totals_match = re.search(r"totals\s*=\s*\[([^\]]*)\]", stdout)
    if totals_match is None:
        raise ValueError(
            f"could not find totals=[...] in MiniZinc toy-nsp output:\n{stdout}"
        )
    totals = [int(x.strip()) for x in totals_match.group(1).split(",") if x.strip()]
    return MznToyNspOutcome(spread=int(spread_match.group(1)), totals=totals)
