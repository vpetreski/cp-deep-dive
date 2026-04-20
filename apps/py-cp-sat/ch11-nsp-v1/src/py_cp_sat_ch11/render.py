"""ASCII roster renderer.

Drops the solver's per-cell boolean grid into a nurse-rows × day-columns table
that fits a terminal. Off days are marked ``--``.
"""

from __future__ import annotations

from collections import defaultdict

from nsp_core import Instance, Schedule


def render_ascii(instance: Instance, schedule: Schedule) -> str:
    """Return a multi-line string rendering of the schedule."""
    # Index assignments for quick lookup
    by_nd: dict[tuple[str, int], str] = defaultdict(lambda: "--")
    for a in schedule.assignments:
        if a.shift_id is not None:
            by_nd[(a.nurse_id, a.day)] = a.shift_id

    name_w = max((len(f"{n.id} {n.name}") for n in instance.nurses), default=10)
    day_w = max(2, max(len(s.id) for s in instance.shifts))
    header_cells = [f"d{d:02d}".rjust(day_w) for d in range(instance.horizon_days)]
    lines: list[str] = []
    lines.append("Nurse".ljust(name_w) + " | " + " ".join(header_cells))
    lines.append("-" * len(lines[0]))
    for n in instance.nurses:
        row = [f"{n.id} {n.name}".ljust(name_w), "|"]
        for d in range(instance.horizon_days):
            cell = by_nd[(n.id, d)]
            row.append(cell.rjust(day_w))
        lines.append(" ".join(row))

    # Footer: totals per nurse
    totals: dict[str, int] = defaultdict(int)
    hours: dict[str, float] = defaultdict(float)
    shifts_by_id = {s.id: s for s in instance.shifts}
    for a in schedule.assignments:
        if a.shift_id is not None:
            totals[a.nurse_id] += 1
            hours[a.nurse_id] += shifts_by_id[a.shift_id].hours
    lines.append("")
    lines.append("Per-nurse totals:")
    for n in instance.nurses:
        lines.append(
            f"  {n.id} {n.name}: {totals[n.id]} shifts  "
            f"({hours[n.id]:.1f}h / contract {n.contract_hours_per_week}h/wk)"
        )

    # Footer: coverage per cell
    cover: dict[tuple[int, str], int] = defaultdict(int)
    for a in schedule.assignments:
        if a.shift_id is not None:
            cover[(a.day, a.shift_id)] += 1
    lines.append("")
    lines.append("Coverage (cell → staffed):")
    for cov in instance.coverage:
        staffed = cover.get((cov.day, cov.shift_id), 0)
        marker = "ok" if cov.min <= staffed <= cov.max else "!!"
        lines.append(
            f"  [{marker}] d{cov.day:02d}/{cov.shift_id}: {staffed}  (min {cov.min}, max {cov.max})"
        )

    return "\n".join(lines)
