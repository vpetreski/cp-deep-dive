"""Chapter 08 — MiniZinc prototype then Python port.

Solve the same tiny NSP twice:
    1. Python port via CP-SAT (always works).
    2. MiniZinc invocation of ``apps/mzn/toy-nsp.mzn`` if ``minizinc`` exists.

Print the per-nurse totals and the spread objective, then confirm the two
runtimes agree on the optimum.
"""

from __future__ import annotations

from dataclasses import dataclass

from py_cp_sat_ch07.mzn_runner import minizinc_available, run_model

from py_cp_sat_ch08.parity import MznToyNspOutcome, parse_toy_nsp_output
from py_cp_sat_ch08.toy_nsp import DEMO_INSTANCE, ToyNspResult, solve_toy_nsp


@dataclass(frozen=True)
class ChapterDemo:
    python_result: ToyNspResult
    minizinc_outcome: MznToyNspOutcome | None
    parity: bool | None


def solve() -> ChapterDemo:
    python_result = solve_toy_nsp(DEMO_INSTANCE)
    if not minizinc_available():
        return ChapterDemo(python_result=python_result, minizinc_outcome=None, parity=None)

    run = run_model("toy-nsp")
    mzn_outcome = parse_toy_nsp_output(run.stdout)
    parity = mzn_outcome.spread == python_result.spread
    return ChapterDemo(
        python_result=python_result, minizinc_outcome=mzn_outcome, parity=parity
    )


def _render_schedule(schedule: list[list[int]], n_shifts: int) -> str:
    """Render the assignment as a compact ASCII grid."""
    labels = {i: chr(ord("A") + i) for i in range(n_shifts)}
    labels[-1] = "."
    rows = [
        "  | " + " ".join(f"d{d}" for d in range(len(schedule[0]))),
        "--+-" + "---" * len(schedule[0]),
    ]
    for n, row in enumerate(schedule):
        rows.append(f"n{n}| " + " ".join(f" {labels[s]}" for s in row))
    return "\n".join(rows)


def main() -> None:
    demo = solve()
    python = demo.python_result

    print("=== Toy NSP (Python CP-SAT) ===")
    print(f"Status:  {python.status}")
    print(f"Spread:  {python.spread}")
    print(f"Totals:  {python.totals}")
    print(_render_schedule(python.schedule, DEMO_INSTANCE.n_shifts))
    print()

    if demo.minizinc_outcome is None:
        print("=== MiniZinc not installed — skipping prototype run ===")
        return

    print("=== Toy NSP (MiniZinc) ===")
    print(f"Spread:  {demo.minizinc_outcome.spread}")
    print(f"Totals:  {demo.minizinc_outcome.totals}")
    print()
    print(f"Python / MiniZinc parity: {demo.parity}")


if __name__ == "__main__":
    main()
