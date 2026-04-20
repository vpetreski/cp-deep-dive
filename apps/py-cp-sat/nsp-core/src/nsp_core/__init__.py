"""Shared NSP primitives (domain, loader, solver, validator).

Keep the public surface small and stable — `apps/py-api` and the chapter
packages import from here.
"""

from __future__ import annotations

from nsp_core.domain import (
    Assignment,
    CoverageRequirement,
    Instance,
    Nurse,
    ObjectiveWeights,
    Preference,
    Schedule,
    Shift,
    SolveParams,
    SolveResult,
    SolveStatus,
    Violation,
)
from nsp_core.loader import load_instance, parse_instance
from nsp_core.solver import solve
from nsp_core.validator import validate_schedule

__all__ = [
    "Assignment",
    "CoverageRequirement",
    "Instance",
    "Nurse",
    "ObjectiveWeights",
    "Preference",
    "Schedule",
    "Shift",
    "SolveParams",
    "SolveResult",
    "SolveStatus",
    "Violation",
    "load_instance",
    "parse_instance",
    "solve",
    "validate_schedule",
]

__version__ = "0.1.0"
