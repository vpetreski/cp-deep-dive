"""Frozen dataclasses mirroring `apps/shared/schemas/nsp-instance.schema.json`.

These objects are immutable so solver code can freely hash / cache them. All
collections are tuples (never lists) for the same reason. JSON parsing lives in
`loader.py`; this module is intentionally independent of any serialisation
library so it can be imported from anywhere (ch11, ch12, ch13, py-api, tests).
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum


# ---------------------------------------------------------------------------
# Instance
# ---------------------------------------------------------------------------


@dataclass(frozen=True, slots=True)
class Shift:
    """A shift type. Accepts either HH:MM strings or minute offsets — the loader
    normalises both to ``start_minutes`` + ``duration_minutes``.
    """

    id: str
    label: str
    start_minutes: int
    duration_minutes: int
    is_night: bool = False
    skill: str | None = None

    @property
    def hours(self) -> float:
        """Duration in hours (used by HC-8 and SC-3)."""
        return self.duration_minutes / 60.0

    @property
    def end_minutes(self) -> int:
        """End minute-of-week (may exceed 1440 for wrap-around night shifts)."""
        return self.start_minutes + self.duration_minutes


@dataclass(frozen=True, slots=True)
class Nurse:
    id: str
    name: str = ""
    skills: frozenset[str] = field(default_factory=frozenset)
    contract_hours_per_week: int = 40
    max_shifts_per_week: int | None = None
    min_shifts_per_week: int | None = None
    max_consecutive_working_days: int | None = None
    unavailable: frozenset[int] = field(default_factory=frozenset)


@dataclass(frozen=True, slots=True)
class CoverageRequirement:
    day: int
    shift_id: str
    min: int
    max: int
    required_skills: frozenset[str] = field(default_factory=frozenset)


@dataclass(frozen=True, slots=True)
class Preference:
    """A soft preference contributing to SC-1.

    ``kind = "prefer"`` means the nurse wants the cell (penalty when not assigned).
    ``kind = "avoid"``  means the nurse dislikes the cell (penalty when assigned).
    ``shift_id = None`` means the preference is about an off-day.
    """

    nurse_id: str
    kind: str  # "prefer" | "avoid"
    weight: int  # 1..10
    day: int | None = None
    shift_id: str | None = None


@dataclass(frozen=True, slots=True)
class Instance:
    id: str
    horizon_days: int
    shifts: tuple[Shift, ...]
    nurses: tuple[Nurse, ...]
    coverage: tuple[CoverageRequirement, ...]
    preferences: tuple[Preference, ...] = ()
    forbidden_transitions: tuple[tuple[str, str], ...] = ()
    min_rest_hours: int = 11
    max_consecutive_working_days: int = 6
    max_consecutive_nights: int = 3
    contract_tolerance_hours: int = 4
    name: str = ""
    source: str = "custom"
    metadata: dict[str, object] = field(default_factory=dict)

    # -- convenience lookups --------------------------------------------------

    def shift(self, shift_id: str) -> Shift:
        for s in self.shifts:
            if s.id == shift_id:
                return s
        raise KeyError(f"Unknown shift id: {shift_id}")

    def nurse(self, nurse_id: str) -> Nurse:
        for n in self.nurses:
            if n.id == nurse_id:
                return n
        raise KeyError(f"Unknown nurse id: {nurse_id}")

    @property
    def night_shift_ids(self) -> frozenset[str]:
        """Set of shift ids classified as 'night' for HC-5."""
        return frozenset(s.id for s in self.shifts if s.is_night)


# ---------------------------------------------------------------------------
# Schedule
# ---------------------------------------------------------------------------


@dataclass(frozen=True, slots=True)
class Assignment:
    """One (nurse, day) row in a Schedule. ``shift_id = None`` means day off."""

    nurse_id: str
    day: int
    shift_id: str | None = None


@dataclass(frozen=True, slots=True)
class Violation:
    """A hard or soft constraint violation on a Schedule."""

    code: str  # "HC-1".."HC-8" or "SC-1".."SC-5"
    message: str
    severity: str = "soft"  # "hard" | "soft"
    nurse_id: str | None = None
    day: int | None = None
    penalty: float | None = None


@dataclass(frozen=True, slots=True)
class Schedule:
    instance_id: str
    assignments: tuple[Assignment, ...]
    violations: tuple[Violation, ...] = ()
    job_id: str | None = None
    generated_at: str | None = None  # ISO-8601 timestamp


# ---------------------------------------------------------------------------
# Solve params / status
# ---------------------------------------------------------------------------


class SolveStatus(str, Enum):
    """Wire-compatible status enum (mirrors SolveResponse.status)."""

    PENDING = "pending"
    QUEUED = "queued"
    RUNNING = "running"
    FEASIBLE = "feasible"
    OPTIMAL = "optimal"
    INFEASIBLE = "infeasible"
    UNKNOWN = "unknown"
    TIMEOUT = "timeout"
    CANCELLED = "cancelled"
    MODEL_INVALID = "modelInvalid"
    ERROR = "error"

    @property
    def is_terminal(self) -> bool:
        return self in _TERMINAL_STATUSES


_TERMINAL_STATUSES = frozenset(
    {
        SolveStatus.FEASIBLE,
        SolveStatus.OPTIMAL,
        SolveStatus.INFEASIBLE,
        SolveStatus.UNKNOWN,
        SolveStatus.TIMEOUT,
        SolveStatus.CANCELLED,
        SolveStatus.MODEL_INVALID,
        SolveStatus.ERROR,
    }
)


@dataclass(frozen=True, slots=True)
class ObjectiveWeights:
    """Per-soft-constraint weights (each 0..1000)."""

    preference: int = 10       # SC-1
    fairness: int = 5          # SC-2
    workload_balance: int = 2  # SC-3
    weekend_distribution: int = 3  # SC-4
    consecutive_days_off: int = 1  # SC-5

    @classmethod
    def from_mapping(cls, raw: dict[str, int] | None) -> ObjectiveWeights:
        """Accept both semantic keys ('preference', 'fairness', ...) and
        short-form SC codes ('SC1', 'SC2', ...). Unknown keys are ignored.
        """
        if not raw:
            return cls()
        key_map = {
            "SC1": "preference",
            "SC2": "fairness",
            "SC3": "workload_balance",
            "SC4": "weekend_distribution",
            "SC5": "consecutive_days_off",
            "preference": "preference",
            "fairness": "fairness",
            "workload_balance": "workload_balance",
            "workloadBalance": "workload_balance",
            "weekend_distribution": "weekend_distribution",
            "weekendDistribution": "weekend_distribution",
            "consecutive_days_off": "consecutive_days_off",
            "consecutiveDaysOff": "consecutive_days_off",
        }
        field_map = {
            "workloadBalance": "workload_balance",
            "weekendDistribution": "weekend_distribution",
            "consecutiveDaysOff": "consecutive_days_off",
        }
        kwargs: dict[str, int] = {}
        for key, val in raw.items():
            if key not in key_map:
                continue
            attr = key_map[key]
            attr = field_map.get(attr, attr)
            kwargs[attr] = int(val)
        defaults = cls()
        return cls(
            preference=kwargs.get("preference", defaults.preference),
            fairness=kwargs.get("fairness", defaults.fairness),
            workload_balance=kwargs.get("workload_balance", defaults.workload_balance),
            weekend_distribution=kwargs.get("weekend_distribution", defaults.weekend_distribution),
            consecutive_days_off=kwargs.get("consecutive_days_off", defaults.consecutive_days_off),
        )


@dataclass(frozen=True, slots=True)
class SolveParams:
    """Solver parameters (wire-aligned but with Python-snake-case names)."""

    time_limit_seconds: float = 30.0
    num_workers: int = 8
    random_seed: int = 42
    linearization_level: int = 1
    relative_gap_limit: float = 0.0
    log_search_progress: bool = False
    enable_hints: bool = True
    objective_weights: ObjectiveWeights = field(default_factory=ObjectiveWeights)

    @classmethod
    def from_mapping(cls, raw: dict[str, object] | None) -> SolveParams:
        """Build from a wire-style dict (accepts both camelCase aliases)."""
        if not raw:
            return cls()
        d: dict[str, object] = dict(raw)
        # alias handling per the schema
        if "maxTimeSeconds" in d and "timeLimitSeconds" not in d:
            d["timeLimitSeconds"] = d.pop("maxTimeSeconds")
        if "numSearchWorkers" in d and "numWorkers" not in d:
            d["numWorkers"] = d.pop("numSearchWorkers")

        weights = ObjectiveWeights.from_mapping(
            d.get("objectiveWeights") if isinstance(d.get("objectiveWeights"), dict) else None  # type: ignore[arg-type]
        )
        return cls(
            time_limit_seconds=float(d.get("timeLimitSeconds", 30.0)),  # type: ignore[arg-type]
            num_workers=int(d.get("numWorkers", 8)),  # type: ignore[arg-type]
            random_seed=int(d.get("randomSeed", 42)),  # type: ignore[arg-type]
            linearization_level=int(d.get("linearizationLevel", 1)),  # type: ignore[arg-type]
            relative_gap_limit=float(d.get("relativeGapLimit", 0.0)),  # type: ignore[arg-type]
            log_search_progress=bool(d.get("logSearchProgress", False)),
            enable_hints=bool(d.get("enableHints", True)),
            objective_weights=weights,
        )


@dataclass(frozen=True, slots=True)
class SolveResult:
    """The full result of a solve — status + best schedule (if any) + metadata."""

    status: SolveStatus
    schedule: Schedule | None
    objective: float | None = None
    best_bound: float | None = None
    gap: float | None = None
    solve_time_seconds: float = 0.0
    violations: tuple[Violation, ...] = ()
    error: str | None = None
