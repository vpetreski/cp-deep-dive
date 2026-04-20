"""Extend the v1 hard-constraint model with soft constraints SC-1..SC-5.

The soft penalty term is a weighted sum::

    minimize  w_SC1 * sum_preference_penalties
            + w_SC2 * (max_total_shifts - min_total_shifts)
            + w_SC3 * (max_hours_fraction - min_hours_fraction)       [×10 scale]
            + w_SC4 * (max_weekend_count - min_weekend_count)
            + w_SC5 * sum_isolated_off_days

Weights default to spec values (10, 5, 2, 3, 1) but every caller can override
through ``SolveParams.objective_weights``.

The ``PenaltyTerms`` dataclass returned alongside the model carries the linear
expressions used in the objective. The solver wrapper reads these back after
solving to populate per-code ``Violation`` entries (see ``solver.py``).
"""

from __future__ import annotations

from dataclasses import dataclass

from ortools.sat.python import cp_model

from nsp_core.domain import Instance, ObjectiveWeights
from nsp_core.model_v1 import ModelVars, build_model as build_hard_model


@dataclass
class PenaltyTerms:
    """Integer expressions whose values equal each SC's raw penalty at solve time."""

    sc1_preference: cp_model.LinearExprT | int
    sc2_fairness_spread: cp_model.IntVar | int
    sc3_workload_spread_x10: cp_model.IntVar | int
    sc4_weekend_spread: cp_model.IntVar | int
    sc5_isolated_off: cp_model.LinearExprT | int


def build_model(
    instance: Instance,
    weights: ObjectiveWeights | None = None,
    *,
    enforce_contract_hours: bool = True,
) -> tuple[cp_model.CpModel, ModelVars, PenaltyTerms]:
    """Build the v2 model (HC-1..HC-8 + SC-1..SC-5) and return the objective
    pieces so tests/solver can attribute penalty back to each SC code."""
    if weights is None:
        weights = ObjectiveWeights()

    model, vars_ = build_hard_model(instance, enforce_contract_hours=enforce_contract_hours)
    x = vars_.x
    nurses = instance.nurses
    shifts = instance.shifts
    horizon = instance.horizon_days

    # ----- SC-1: preference honouring -----
    sc1_terms: list[cp_model.LinearExprT] = []
    for pref in instance.preferences:
        applies_days: list[int] = (
            [pref.day] if pref.day is not None else list(range(horizon))
        )
        if pref.shift_id is None:
            # Day-off preference: we work-indicator = sum over shifts
            for d in applies_days:
                works = [
                    x[(pref.nurse_id, d, s.id)]
                    for s in shifts
                    if (pref.nurse_id, d, s.id) in x
                ]
                if not works:
                    continue
                works_sum = sum(works)
                if pref.kind == "prefer":
                    # want day off → penalty when works
                    sc1_terms.append(pref.weight * works_sum)
                else:  # avoid day off → penalty when does not work
                    # works_sum is bool-like (0..1); penalty = weight * (1 - works_sum)
                    sc1_terms.append(pref.weight * (1 - works_sum))
        else:
            for d in applies_days:
                v = x.get((pref.nurse_id, d, pref.shift_id))
                if v is None:
                    # Structural miss: e.g. nurse unavailable on that day.
                    # Treat as already penalised for prefer, already satisfied for avoid.
                    if pref.kind == "prefer":
                        sc1_terms.append(pref.weight)  # always penalised
                    continue
                if pref.kind == "prefer":
                    sc1_terms.append(pref.weight * (1 - v))
                else:
                    sc1_terms.append(pref.weight * v)

    sc1_expr: cp_model.LinearExprT | int = sum(sc1_terms) if sc1_terms else 0

    # ----- SC-2: fairness (max - min total shifts per nurse) -----
    total_shifts_per_nurse: dict[str, cp_model.IntVar] = {}
    max_per_nurse = horizon  # loose upper bound
    for n in nurses:
        var = model.new_int_var(0, max_per_nurse, f"total_{n.id}")
        terms = [
            x[(n.id, d, s.id)]
            for d in range(horizon)
            for s in shifts
            if (n.id, d, s.id) in x
        ]
        if terms:
            model.add(var == sum(terms))
        else:
            model.add(var == 0)
        total_shifts_per_nurse[n.id] = var

    if len(nurses) >= 2:
        max_total = model.new_int_var(0, max_per_nurse, "fairness_max")
        min_total = model.new_int_var(0, max_per_nurse, "fairness_min")
        model.add_max_equality(max_total, list(total_shifts_per_nurse.values()))
        model.add_min_equality(min_total, list(total_shifts_per_nurse.values()))
        sc2_spread = model.new_int_var(0, max_per_nurse, "fairness_spread")
        model.add(sc2_spread == max_total - min_total)
    else:
        sc2_spread = 0  # no spread with one nurse

    # ----- SC-3: workload balance (max - min hours/contract, scaled x10) -----
    # hoursFraction[n] = hours_scheduled[n] / contract * 10  (int arithmetic)
    # Guard against zero contract.
    hour_frac_vars: list[cp_model.IntVar] = []
    max_frac_bound = int(horizon * 24 * 10)
    for n in nurses:
        contract = max(n.contract_hours_per_week, 1)
        # hours_scheduled[n] in minutes scaled by 10 / (contract*60)  => needs real
        # To stay integer-only: scale * hours_scheduled_minutes / (contract * 60)
        # We use: fraction_x10 = (10 * scheduled_minutes) // (contract * 60)
        #       = (scheduled_minutes) / (6 * contract)
        scheduled_minutes_var = model.new_int_var(
            0, horizon * 24 * 60, f"hours_mins_{n.id}"
        )
        coeffs = [
            s.duration_minutes
            for d in range(horizon)
            for s in shifts
            if (n.id, d, s.id) in x
        ]
        terms = [
            x[(n.id, d, s.id)]
            for d in range(horizon)
            for s in shifts
            if (n.id, d, s.id) in x
        ]
        if terms:
            model.add(scheduled_minutes_var == sum(c * v for c, v in zip(coeffs, terms, strict=True)))
        else:
            model.add(scheduled_minutes_var == 0)

        frac_x10 = model.new_int_var(0, max_frac_bound, f"hours_frac10_{n.id}")
        # 10 * scheduled_minutes = frac_x10 * contract * 60 + remainder (0..contract*60-1)
        model.add_division_equality(frac_x10, 10 * scheduled_minutes_var, contract * 60)
        hour_frac_vars.append(frac_x10)

    if len(hour_frac_vars) >= 2:
        max_frac = model.new_int_var(0, max_frac_bound, "workload_max")
        min_frac = model.new_int_var(0, max_frac_bound, "workload_min")
        model.add_max_equality(max_frac, hour_frac_vars)
        model.add_min_equality(min_frac, hour_frac_vars)
        sc3_spread = model.new_int_var(0, max_frac_bound, "workload_spread_x10")
        model.add(sc3_spread == max_frac - min_frac)
    else:
        sc3_spread = 0

    # ----- SC-4: weekend distribution (max - min weekend counts) -----
    weekend_days = [d for d in range(horizon) if d % 7 in (5, 6)]
    weekend_counts: dict[str, cp_model.IntVar] = {}
    bound_wk = max(len(weekend_days), 1)
    for n in nurses:
        var_wk = model.new_int_var(0, bound_wk, f"weekend_{n.id}")
        terms = [
            x[(n.id, d, s.id)]
            for d in weekend_days
            for s in shifts
            if (n.id, d, s.id) in x
        ]
        if terms:
            model.add(var_wk == sum(terms))
        else:
            model.add(var_wk == 0)
        weekend_counts[n.id] = var_wk

    if weekend_days and len(nurses) >= 2:
        max_wk = model.new_int_var(0, bound_wk, "weekend_max")
        min_wk = model.new_int_var(0, bound_wk, "weekend_min")
        model.add_max_equality(max_wk, list(weekend_counts.values()))
        model.add_min_equality(min_wk, list(weekend_counts.values()))
        sc4_spread = model.new_int_var(0, bound_wk, "weekend_spread")
        model.add(sc4_spread == max_wk - min_wk)
    else:
        sc4_spread = 0

    # ----- SC-5: isolated days off -----
    # works[n, d] = OR over s of x[n, d, s]; isolated = works[d-1] * (1 - works[d]) * works[d+1]
    works_by_nurse: dict[str, dict[int, cp_model.IntVar]] = {}
    for n in nurses:
        per_day: dict[int, cp_model.IntVar] = {}
        for d in range(horizon):
            cells = [x[(n.id, d, s.id)] for s in shifts if (n.id, d, s.id) in x]
            w = model.new_bool_var(f"works_{n.id}_d{d}")
            if cells:
                # works = OR(cells). For boolean cells summed, sum >= 1 => w=1 else w=0.
                # At-most-one is already enforced by HC-2, so sum is 0 or 1.
                model.add(w == sum(cells))
            else:
                model.add(w == 0)
            per_day[d] = w
        works_by_nurse[n.id] = per_day

    sc5_terms: list[cp_model.IntVar] = []
    for n in nurses:
        works = works_by_nurse[n.id]
        for d in range(1, horizon - 1):
            iso = model.new_bool_var(f"iso_{n.id}_d{d}")
            # iso == works[d-1] AND NOT works[d] AND works[d+1]
            # Linearise with the three implications:
            #   iso <= works[d-1]
            #   iso <= 1 - works[d]
            #   iso <= works[d+1]
            #   iso >= works[d-1] + (1 - works[d]) + works[d+1] - 2
            model.add(iso <= works[d - 1])
            model.add(iso <= 1 - works[d])
            model.add(iso <= works[d + 1])
            model.add(iso >= works[d - 1] + (1 - works[d]) + works[d + 1] - 2)
            sc5_terms.append(iso)

    sc5_expr: cp_model.LinearExprT | int = sum(sc5_terms) if sc5_terms else 0

    # ----- Objective: weighted sum -----
    objective = (
        weights.preference * sc1_expr
        + weights.fairness * sc2_spread
        + weights.workload_balance * sc3_spread
        + weights.weekend_distribution * sc4_spread
        + weights.consecutive_days_off * sc5_expr
    )
    model.minimize(objective)

    penalty_terms = PenaltyTerms(
        sc1_preference=sc1_expr,
        sc2_fairness_spread=sc2_spread,
        sc3_workload_spread_x10=sc3_spread,
        sc4_weekend_spread=sc4_spread,
        sc5_isolated_off=sc5_expr,
    )
    return model, vars_, penalty_terms
