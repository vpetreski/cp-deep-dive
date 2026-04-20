"""Render a schedule plus a per-code soft-constraint penalty breakdown."""

from __future__ import annotations

from collections.abc import Iterable

from nsp_core import Instance, Schedule, Violation

# Reuse the ch11 ASCII base renderer for the roster.
from py_cp_sat_ch11.render import render_ascii


def render_ascii_with_violations(
    instance: Instance,
    schedule: Schedule,
    violations: Iterable[Violation],
) -> str:
    """Base roster + an "SC breakdown" footer."""
    base = render_ascii(instance, schedule)
    v_list = list(violations)
    if not v_list:
        return base + "\n\nSoft-constraint breakdown: all 0 (perfect schedule)."
    lines = [base, "", "Soft-constraint breakdown:"]
    total = 0.0
    for v in sorted(v_list, key=lambda x: x.code):
        penalty = v.penalty if v.penalty is not None else 0.0
        total += penalty
        lines.append(f"  {v.code}: {v.message}")
    lines.append(f"  TOTAL penalty: {total:.1f}")
    return "\n".join(lines)
