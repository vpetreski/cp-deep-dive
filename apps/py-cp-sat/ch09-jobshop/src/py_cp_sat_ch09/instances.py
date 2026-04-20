"""Demo job-shop instances used across the chapter.

A *job-shop* instance is a list of jobs. Each job is an ordered list of
*operations*. Each operation pins (machine, duration) — operations inside a
job run in sequence, and two operations on the same machine cannot overlap.

The small 3×3 below is the textbook example from Taillard-style tutorials:
every job visits every machine, in a different order. Makespan-optimal is 11.

The 5×4 below is the classic ``ft06``-family test case (5 jobs × 4 machines),
reshaped to stay readable in a chapter. It's just big enough for the Gantt
chart to look interesting without making the chapter slow.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Operation:
    """One (machine, duration) step of a job."""

    machine: int
    duration: int


@dataclass(frozen=True)
class JobShopInstance:
    """A job-shop problem. Jobs are ordered lists of operations."""

    n_machines: int
    jobs: tuple[tuple[Operation, ...], ...]

    @property
    def n_jobs(self) -> int:
        return len(self.jobs)

    def horizon(self) -> int:
        """Upper bound on makespan — the sum of all durations."""
        return sum(op.duration for job in self.jobs for op in job)


# Textbook 3×3 — optimum makespan = 11.
DEMO_3X3 = JobShopInstance(
    n_machines=3,
    jobs=(
        (Operation(0, 3), Operation(1, 2), Operation(2, 2)),
        (Operation(0, 2), Operation(2, 1), Operation(1, 4)),
        (Operation(1, 4), Operation(2, 3)),
    ),
)

# Small 5×4 — more machines, room for a pretty Gantt chart.
DEMO_5X4 = JobShopInstance(
    n_machines=4,
    jobs=(
        (Operation(0, 3), Operation(1, 2), Operation(2, 2), Operation(3, 4)),
        (Operation(1, 3), Operation(0, 4), Operation(3, 1), Operation(2, 2)),
        (Operation(2, 2), Operation(3, 3), Operation(0, 2), Operation(1, 3)),
        (Operation(3, 2), Operation(2, 3), Operation(1, 2), Operation(0, 1)),
        (Operation(0, 2), Operation(2, 1), Operation(3, 3), Operation(1, 2)),
    ),
)
