"""Render a job-shop schedule as a Gantt chart (PNG).

We pick matplotlib's ``Agg`` backend explicitly so this works on headless CI
machines. The chart has one row per machine (y-axis) and operations drawn as
horizontal bars coloured by job.
"""

from __future__ import annotations

from pathlib import Path

import matplotlib

matplotlib.use("Agg")

import matplotlib.pyplot as plt  # noqa: E402  — must come after set backend
from matplotlib.patches import Patch  # noqa: E402

from py_cp_sat_ch09.jobshop import JobShopSolution  # noqa: E402


def render_gantt(
    solution: JobShopSolution,
    out_path: Path,
    *,
    title: str = "Job-Shop schedule",
    bar_height: float = 0.7,
) -> Path:
    """Write a Gantt PNG for ``solution`` at ``out_path``. Returns the path.

    Raises ``ValueError`` if the schedule is empty (e.g. infeasible instance).
    """
    if not solution.schedule:
        raise ValueError("Cannot render Gantt chart for an empty schedule.")

    n_jobs = max(op.job for op in solution.schedule) + 1
    n_machines = max(op.machine for op in solution.schedule) + 1

    cmap = plt.get_cmap("tab10")
    colors = [cmap(j % 10) for j in range(n_jobs)]

    fig, ax = plt.subplots(figsize=(10, 1.0 + 0.6 * n_machines))

    for op in solution.schedule:
        ax.broken_barh(
            [(op.start, op.duration)],
            (op.machine - bar_height / 2, bar_height),
            facecolors=colors[op.job],
            edgecolors="black",
            linewidth=0.5,
        )
        ax.text(
            op.start + op.duration / 2,
            op.machine,
            f"J{op.job}",
            ha="center",
            va="center",
            fontsize=8,
            color="white",
            fontweight="bold",
        )

    ax.set_yticks(range(n_machines))
    ax.set_yticklabels([f"M{m}" for m in range(n_machines)])
    ax.set_xlabel("time")
    ax.set_ylabel("machine")
    ax.set_title(f"{title} — makespan={solution.makespan}")
    ax.set_xlim(0, solution.makespan)
    ax.invert_yaxis()
    ax.grid(True, axis="x", linestyle=":", alpha=0.5)

    legend_handles = [
        Patch(facecolor=colors[j], edgecolor="black", label=f"Job {j}")
        for j in range(n_jobs)
    ]
    ax.legend(handles=legend_handles, loc="lower right")

    out_path.parent.mkdir(parents=True, exist_ok=True)
    fig.tight_layout()
    fig.savefig(out_path, dpi=120)
    plt.close(fig)

    return out_path
