"""Exercise 6.5 — Measure that lex breaks 5! permutation symmetry.

Enumerate all feasible ``(nurses × days)`` schedules under a "at least
``min_work`` working days per nurse" rule, with and without lex ordering.
Without lex, every feasible solution has ``n_nurses!`` permutation twins;
with lex the ratio drops to ≈ ``n_nurses!``.
"""

from __future__ import annotations

import math
from dataclasses import dataclass

from py_cp_sat_ch06.lex_leq import count_schedules


@dataclass(frozen=True)
class LexRatio:
    without_lex: int
    with_lex: int
    expected_ratio: int

    @property
    def ratio(self) -> float:
        return self.without_lex / self.with_lex if self.with_lex else float("inf")


def measure_lex_ratio(
    *, n_nurses: int = 3, days: int = 4, min_work: int = 2
) -> LexRatio:
    without_lex = count_schedules(
        n_nurses=n_nurses, days=days, min_work=min_work, use_lex=False
    )
    with_lex = count_schedules(
        n_nurses=n_nurses, days=days, min_work=min_work, use_lex=True
    )
    return LexRatio(
        without_lex=without_lex,
        with_lex=with_lex,
        expected_ratio=math.factorial(n_nurses),
    )


def main() -> None:
    ratio = measure_lex_ratio()
    print(f"Solutions without lex: {ratio.without_lex}")
    print(f"Solutions with lex:    {ratio.with_lex}")
    print(f"Measured ratio:        {ratio.ratio:.3f}x (expected ≈ {ratio.expected_ratio})")


if __name__ == "__main__":
    main()
