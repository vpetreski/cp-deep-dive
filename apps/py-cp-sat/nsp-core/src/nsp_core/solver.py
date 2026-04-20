"""High-level solve() entrypoint used by chapter code and the FastAPI backend.

Wraps the CP-SAT ``CpSolver``, applies ``SolveParams``, and translates the
result into a wire-compatible :class:`SolveResult` (status enum + Schedule +
metadata). Supports three modes:

- ``objective="hard"`` (default): enforce HC-1..HC-8 only, no soft weights.
- ``objective="weighted"``: add SC-1..SC-5 as a weighted-sum penalty.
- ``objective="lexicographic"``: solve-then-pin — minimise SC-1 first, pin it,
  then minimise the remaining weighted sum.

Callers that need progress callbacks pass a ``progress_callback`` — it is
invoked with each incumbent :class:`SolveResult` as CP-SAT discovers better
solutions. This is how the SSE streaming endpoint is fed.
"""

from __future__ import annotations

import datetime as dt
from collections.abc import Callable
from dataclasses import replace

from ortools.sat.python import cp_model

from nsp_core.domain import (
    Assignment,
    Instance,
    ObjectiveWeights,
    Schedule,
    SolveParams,
    SolveResult,
    SolveStatus,
    Violation,
)
from nsp_core.model_v1 import ModelVars, build_model as build_hard_model
from nsp_core.model_v2 import PenaltyTerms, build_model as build_soft_model

ProgressCallback = Callable[[SolveResult], None]


_CPSAT_STATUS_TO_WIRE: dict[int, SolveStatus] = {
    cp_model.OPTIMAL: SolveStatus.OPTIMAL,
    cp_model.FEASIBLE: SolveStatus.FEASIBLE,
    cp_model.INFEASIBLE: SolveStatus.INFEASIBLE,
    cp_model.UNKNOWN: SolveStatus.UNKNOWN,
    cp_model.MODEL_INVALID: SolveStatus.MODEL_INVALID,
}


def _apply_params(solver: cp_model.CpSolver, params: SolveParams) -> None:
    solver.parameters.max_time_in_seconds = params.time_limit_seconds
    solver.parameters.num_search_workers = params.num_workers
    solver.parameters.random_seed = params.random_seed
    solver.parameters.linearization_level = params.linearization_level
    solver.parameters.log_search_progress = params.log_search_progress
    if params.relative_gap_limit > 0:
        solver.parameters.relative_gap_limit = params.relative_gap_limit


def _schedule_from_solution(
    instance: Instance,
    solver: cp_model.CpSolver,
    vars_: ModelVars,
    *,
    job_id: str | None = None,
) -> Schedule:
    assignments: list[Assignment] = []
    for n in instance.nurses:
        for d in range(instance.horizon_days):
            for s in instance.shifts:
                v = vars_.x.get((n.id, d, s.id))
                if v is not None and solver.value(v) == 1:
                    assignments.append(Assignment(nurse_id=n.id, day=d, shift_id=s.id))
                    break  # HC-2 guarantees at most one shift/day
    return Schedule(
        instance_id=instance.id,
        assignments=tuple(assignments),
        job_id=job_id,
        generated_at=dt.datetime.now(dt.UTC).isoformat(timespec="seconds"),
    )


def _violations_from_terms(
    solver: cp_model.CpSolver,
    terms: PenaltyTerms,
    weights: ObjectiveWeights,
) -> tuple[Violation, ...]:
    """Translate penalty-term values into ``Violation`` rows (one per SC code)."""
    def v(expr: cp_model.LinearExprT | int) -> int:
        if isinstance(expr, int):
            return expr
        return int(solver.value(expr))

    raws = {
        "SC-1": (v(terms.sc1_preference), weights.preference, "preferences not honoured"),
        "SC-2": (
            v(terms.sc2_fairness_spread),
            weights.fairness,
            "unequal total shifts across nurses",
        ),
        "SC-3": (
            v(terms.sc3_workload_spread_x10),
            weights.workload_balance,
            "unequal hours/contract ratios across nurses (×10)",
        ),
        "SC-4": (
            v(terms.sc4_weekend_spread),
            weights.weekend_distribution,
            "unequal weekend assignments across nurses",
        ),
        "SC-5": (
            v(terms.sc5_isolated_off),
            weights.consecutive_days_off,
            "isolated single days off",
        ),
    }
    violations: list[Violation] = []
    for code, (raw, w, msg) in raws.items():
        if raw <= 0:
            continue
        violations.append(
            Violation(
                code=code,
                message=f"{msg} (raw={raw}, weight={w}, penalty={raw * w})",
                severity="soft",
                penalty=float(raw * w),
            )
        )
    return tuple(violations)


class _IncumbentRecorder(cp_model.CpSolverSolutionCallback):
    """CP-SAT callback that rebuilds a ``SolveResult`` from each incumbent and
    forwards it to a user-supplied ``ProgressCallback``."""

    def __init__(
        self,
        instance: Instance,
        vars_: ModelVars,
        callback: ProgressCallback,
        *,
        terms: PenaltyTerms | None,
        weights: ObjectiveWeights,
        job_id: str | None,
    ) -> None:
        super().__init__()
        self._instance = instance
        self._vars = vars_
        self._callback = callback
        self._terms = terms
        self._weights = weights
        self._job_id = job_id
        self.count = 0

    def on_solution_callback(self) -> None:  # pragma: no cover - CP-SAT thread
        self.count += 1
        assignments: list[Assignment] = []
        for n in self._instance.nurses:
            for d in range(self._instance.horizon_days):
                for s in self._instance.shifts:
                    v = self._vars.x.get((n.id, d, s.id))
                    if v is not None and self.value(v) == 1:
                        assignments.append(
                            Assignment(nurse_id=n.id, day=d, shift_id=s.id)
                        )
                        break
        schedule = Schedule(
            instance_id=self._instance.id,
            assignments=tuple(assignments),
            job_id=self._job_id,
            generated_at=dt.datetime.now(dt.UTC).isoformat(timespec="seconds"),
        )
        violations: tuple[Violation, ...] = ()
        if self._terms is not None:
            def v(expr: cp_model.LinearExprT | int) -> int:
                if isinstance(expr, int):
                    return expr
                return int(self.value(expr))

            raws = {
                "SC-1": (v(self._terms.sc1_preference), self._weights.preference),
                "SC-2": (v(self._terms.sc2_fairness_spread), self._weights.fairness),
                "SC-3": (
                    v(self._terms.sc3_workload_spread_x10),
                    self._weights.workload_balance,
                ),
                "SC-4": (v(self._terms.sc4_weekend_spread), self._weights.weekend_distribution),
                "SC-5": (v(self._terms.sc5_isolated_off), self._weights.consecutive_days_off),
            }
            violations = tuple(
                Violation(
                    code=code,
                    message=f"raw={raw}, weight={w}",
                    severity="soft",
                    penalty=float(raw * w),
                )
                for code, (raw, w) in raws.items()
                if raw > 0
            )
        # OR-Tools 9.15+ removed ``has_objective()`` from the solution callback API.
        # ``objective_value`` still works but only when the model actually has an
        # objective. In hard-only mode no soft penalties are added, so the
        # objective is undefined; we signal that with ``None``.
        if self._terms is None:
            obj: float | None = None
        else:
            try:
                obj = float(self.objective_value)
            except Exception:
                obj = None
        try:
            bound = self.best_objective_bound
        except Exception:  # pragma: no cover
            bound = None
        result = SolveResult(
            status=SolveStatus.RUNNING,
            schedule=schedule,
            objective=obj,
            best_bound=bound,
            solve_time_seconds=self.wall_time,
            violations=violations,
        )
        try:
            self._callback(result)
        except Exception:  # pragma: no cover - we don't let UI errors kill the solve
            pass


def solve(
    instance: Instance,
    params: SolveParams | None = None,
    *,
    objective: str = "hard",
    weights: ObjectiveWeights | None = None,
    job_id: str | None = None,
    progress_callback: ProgressCallback | None = None,
) -> SolveResult:
    """Solve ``instance`` and return a :class:`SolveResult`.

    ``objective`` is one of:

    - ``"hard"`` — find any feasible schedule (HC-1..HC-8 only).
    - ``"weighted"`` — minimise the weighted soft-penalty sum (default weights unless
      ``weights`` is supplied or ``params.objective_weights`` overrides).
    - ``"lexicographic"`` — minimise SC-1 first, pin, then minimise the rest.
    """
    params = params or SolveParams()
    if weights is None:
        weights = params.objective_weights

    if objective == "hard":
        return _solve_hard(instance, params, job_id=job_id, progress_callback=progress_callback)
    if objective == "weighted":
        return _solve_weighted(
            instance, params, weights, job_id=job_id, progress_callback=progress_callback
        )
    if objective == "lexicographic":
        return _solve_lexicographic(
            instance, params, weights, job_id=job_id, progress_callback=progress_callback
        )
    raise ValueError(f"Unknown objective mode: {objective!r}")


def _solve_hard(
    instance: Instance,
    params: SolveParams,
    *,
    job_id: str | None,
    progress_callback: ProgressCallback | None,
) -> SolveResult:
    model, vars_ = build_hard_model(instance)
    solver = cp_model.CpSolver()
    _apply_params(solver, params)
    if progress_callback is not None:
        cb = _IncumbentRecorder(
            instance, vars_, progress_callback,
            terms=None, weights=ObjectiveWeights(), job_id=job_id,
        )
        status = solver.solve(model, cb)
    else:
        status = solver.solve(model)
    wire_status = _CPSAT_STATUS_TO_WIRE.get(status, SolveStatus.UNKNOWN)
    schedule: Schedule | None = None
    if wire_status in (SolveStatus.OPTIMAL, SolveStatus.FEASIBLE):
        schedule = _schedule_from_solution(instance, solver, vars_, job_id=job_id)
    return SolveResult(
        status=wire_status,
        schedule=schedule,
        objective=None,
        best_bound=None,
        gap=None,
        solve_time_seconds=solver.wall_time,
    )


def _solve_weighted(
    instance: Instance,
    params: SolveParams,
    weights: ObjectiveWeights,
    *,
    job_id: str | None,
    progress_callback: ProgressCallback | None,
) -> SolveResult:
    model, vars_, terms = build_soft_model(instance, weights)
    solver = cp_model.CpSolver()
    _apply_params(solver, params)
    if progress_callback is not None:
        cb = _IncumbentRecorder(
            instance, vars_, progress_callback,
            terms=terms, weights=weights, job_id=job_id,
        )
        status = solver.solve(model, cb)
    else:
        status = solver.solve(model)
    wire_status = _CPSAT_STATUS_TO_WIRE.get(status, SolveStatus.UNKNOWN)
    schedule: Schedule | None = None
    violations: tuple[Violation, ...] = ()
    obj_val: float | None = None
    best_bound: float | None = None
    gap: float | None = None
    if wire_status in (SolveStatus.OPTIMAL, SolveStatus.FEASIBLE):
        schedule = _schedule_from_solution(instance, solver, vars_, job_id=job_id)
        violations = _violations_from_terms(solver, terms, weights)
        obj_val = float(solver.objective_value)
        best_bound = float(solver.best_objective_bound)
        if obj_val > 0:
            gap = max(0.0, (obj_val - best_bound) / max(abs(obj_val), 1e-9))
        else:
            gap = 0.0
        schedule = replace(schedule, violations=violations)
    return SolveResult(
        status=wire_status,
        schedule=schedule,
        objective=obj_val,
        best_bound=best_bound,
        gap=gap,
        solve_time_seconds=solver.wall_time,
        violations=violations,
    )


def _solve_lexicographic(
    instance: Instance,
    params: SolveParams,
    weights: ObjectiveWeights,
    *,
    job_id: str | None,
    progress_callback: ProgressCallback | None,
) -> SolveResult:
    """Solve SC-1 first, pin the value, then minimise the weighted remainder."""
    # Stage 1: minimise raw SC-1 only (with weights for everything else = 0).
    stage1_weights = ObjectiveWeights(
        preference=1, fairness=0, workload_balance=0,
        weekend_distribution=0, consecutive_days_off=0,
    )
    model1, vars_, terms1 = build_soft_model(instance, stage1_weights)
    solver1 = cp_model.CpSolver()
    _apply_params(solver1, params)
    # Reserve half the budget for stage 1.
    stage1_budget = params.time_limit_seconds / 2.0
    solver1.parameters.max_time_in_seconds = stage1_budget
    status1 = solver1.solve(model1)
    wire1 = _CPSAT_STATUS_TO_WIRE.get(status1, SolveStatus.UNKNOWN)
    if wire1 not in (SolveStatus.OPTIMAL, SolveStatus.FEASIBLE):
        return SolveResult(
            status=wire1,
            schedule=None,
            solve_time_seconds=solver1.wall_time,
        )
    sc1_pinned = int(solver1.objective_value)

    # Stage 2: rebuild the full model, pin SC-1 to sc1_pinned, minimise the rest.
    model2, vars2, terms2 = build_soft_model(
        instance,
        ObjectiveWeights(
            preference=0,
            fairness=weights.fairness,
            workload_balance=weights.workload_balance,
            weekend_distribution=weights.weekend_distribution,
            consecutive_days_off=weights.consecutive_days_off,
        ),
    )
    # Pin SC-1 raw penalty to its optimum (allow +0 slack).
    model2.add(terms2.sc1_preference == sc1_pinned)
    solver2 = cp_model.CpSolver()
    _apply_params(solver2, params)
    solver2.parameters.max_time_in_seconds = params.time_limit_seconds - solver1.wall_time
    if progress_callback is not None:
        cb = _IncumbentRecorder(
            instance, vars2, progress_callback,
            terms=terms2, weights=weights, job_id=job_id,
        )
        status2 = solver2.solve(model2, cb)
    else:
        status2 = solver2.solve(model2)
    wire2 = _CPSAT_STATUS_TO_WIRE.get(status2, SolveStatus.UNKNOWN)
    schedule: Schedule | None = None
    violations: tuple[Violation, ...] = ()
    obj_val: float | None = None
    best_bound: float | None = None
    gap: float | None = None
    if wire2 in (SolveStatus.OPTIMAL, SolveStatus.FEASIBLE):
        schedule = _schedule_from_solution(instance, solver2, vars2, job_id=job_id)
        violations = _violations_from_terms(solver2, terms2, weights)
        # Include the pinned SC-1 as a violation row too so callers see it.
        if sc1_pinned > 0:
            violations = (
                Violation(
                    code="SC-1",
                    message=f"preferences: pinned raw={sc1_pinned}",
                    severity="soft",
                    penalty=float(sc1_pinned * weights.preference),
                ),
            ) + tuple(v for v in violations if v.code != "SC-1")
        obj_val = float(solver2.objective_value)
        best_bound = float(solver2.best_objective_bound)
        gap = 0.0 if obj_val == 0 else max(0.0, (obj_val - best_bound) / max(abs(obj_val), 1e-9))
        schedule = replace(schedule, violations=violations)
    return SolveResult(
        status=wire2,
        schedule=schedule,
        objective=obj_val,
        best_bound=best_bound,
        gap=gap,
        solve_time_seconds=solver1.wall_time + solver2.wall_time,
        violations=violations,
    )
