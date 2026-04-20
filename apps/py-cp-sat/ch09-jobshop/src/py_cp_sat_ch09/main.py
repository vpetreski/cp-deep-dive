"""Chapter 09 — Job-Shop with IntervalVar + NoOverlap + Gantt rendering.

Running the module:
    * solves the 3×3 textbook instance (optimum makespan = 11)
    * solves the 5×4 demo instance
    * writes a Gantt PNG for the 5×4 demo to ``./build/ch09_5x4_gantt.png``

The PNG location is printed so users can open it.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from py_cp_sat_ch09.gantt import render_gantt
from py_cp_sat_ch09.instances import DEMO_3X3, DEMO_5X4
from py_cp_sat_ch09.jobshop import JobShopSolution, solve_jobshop, verify_schedule


@dataclass(frozen=True)
class ChapterDemo:
    """What the chapter's smoke test inspects."""

    small: JobShopSolution
    medium: JobShopSolution
    gantt_path: Path


def default_output_dir() -> Path:
    """Where we stash the Gantt PNG. Overridable via ``CH09_OUT_DIR`` env var."""
    override = os.environ.get("CH09_OUT_DIR")
    if override:
        return Path(override)
    return Path.cwd() / "build"


def solve(*, output_dir: Path | None = None) -> ChapterDemo:
    """Run both instances and render the Gantt for the medium one."""
    small = solve_jobshop(DEMO_3X3)
    medium = solve_jobshop(DEMO_5X4)

    assert verify_schedule(DEMO_3X3, small), "3x3 solution failed verification"
    assert verify_schedule(DEMO_5X4, medium), "5x4 solution failed verification"

    out_dir = output_dir or default_output_dir()
    gantt_path = render_gantt(
        medium,
        out_dir / "ch09_5x4_gantt.png",
        title="Chapter 09 — 5x4 job-shop",
    )
    return ChapterDemo(small=small, medium=medium, gantt_path=gantt_path)


def main() -> None:
    demo = solve()

    print("=== Job-shop 3x3 (textbook instance) ===")
    print(f"Status:   {demo.small.status}")
    print(f"Makespan: {demo.small.makespan}")
    print(f"Wall:     {demo.small.wall_time_s:.3f}s")
    print()

    print("=== Job-shop 5x4 ===")
    print(f"Status:   {demo.medium.status}")
    print(f"Makespan: {demo.medium.makespan}")
    print(f"Wall:     {demo.medium.wall_time_s:.3f}s")
    print()

    print(f"Gantt chart written to: {demo.gantt_path}")


if __name__ == "__main__":
    main()
