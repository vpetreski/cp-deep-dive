"""CP-SAT model for the hard-constraints-only NSP (HC-1..HC-8).

The Boolean decision grid is::

    x[(nurse_id, day, shift_id)] = 1  iff  nurse works that shift on that day

We *skip* variables for unavailable cells — that is, when a nurse has ``day``
listed in ``unavailable`` or a coverage cell requires a skill the nurse does not
hold, the variable is simply not created. This is tighter than creating the
variable and pinning it to zero because there is nothing for the propagators to
examine. See ``docs/chapters/11-nsp-v1-hard-constraints.md`` §4.

All constraint classes use ``model.Add(sum(...) <relop> k)`` rather than
decomposed forms — CP-SAT's presolve is happy with those but the intent stays
readable.
"""

from __future__ import annotations

from dataclasses import dataclass

from ortools.sat.python import cp_model

from nsp_core.domain import Instance

Key = tuple[str, int, str]  # (nurse_id, day, shift_id)


@dataclass
class ModelVars:
    """Decision variables + convenience lookups returned from ``build_model``."""

    x: dict[Key, cp_model.IntVar]
    instance: Instance


def _cell_allowed(instance: Instance, nurse_id: str, day: int, shift_id: str) -> bool:
    """True iff we create a variable for (nurse, day, shift)."""
    nurse = instance.nurse(nurse_id)
    if day in nurse.unavailable:
        return False
    shift = instance.shift(shift_id)
    return not (shift.skill is not None and shift.skill not in nurse.skills)


def build_model(
    instance: Instance, *, enforce_contract_hours: bool = True
) -> tuple[cp_model.CpModel, ModelVars]:
    """Build the HC-1..HC-8 CP-SAT model.

    Parameters
    ----------
    instance:
        The normalised Instance.
    enforce_contract_hours:
        Set to False to disable HC-8 (useful for test instances where nurse
        contracts are deliberately loose). Defaults to True per spec.
    """
    model = cp_model.CpModel()
    nurses = instance.nurses
    days = range(instance.horizon_days)
    shifts = instance.shifts
    shift_ids = tuple(s.id for s in shifts)

    # ---------- variables ----------
    x: dict[Key, cp_model.IntVar] = {}
    for n in nurses:
        for d in days:
            for s in shift_ids:
                if _cell_allowed(instance, n.id, d, s):
                    x[(n.id, d, s)] = model.new_bool_var(f"x_{n.id}_d{d}_{s}")

    # ---------- HC-1: coverage ----------
    # For each coverage cell, min <= sum(x over n) <= max; if requiredSkills
    # set, at least one nurse with that skill must be assigned (HC-7 flavour).
    for cov in instance.coverage:
        assignable = [
            x[(n.id, cov.day, cov.shift_id)]
            for n in nurses
            if (n.id, cov.day, cov.shift_id) in x
        ]
        if not assignable:
            if cov.min > 0:
                # Impossible cell — force infeasibility cleanly
                model.add_bool_or([]).OnlyEnforceIf([])  # pragma: no cover
                model.add(cov.min <= 0)
            continue
        if cov.min > 0:
            model.add(sum(assignable) >= cov.min)
        if cov.max < len(nurses):
            model.add(sum(assignable) <= cov.max)

    # ---------- HC-7: skill coverage ----------
    for cov in instance.coverage:
        for skill in cov.required_skills:
            skilled = [
                x[(n.id, cov.day, cov.shift_id)]
                for n in nurses
                if (n.id, cov.day, cov.shift_id) in x and skill in n.skills
            ]
            if not skilled:
                # Must be infeasible for this cell — add an unsatisfiable clause.
                forced_zero = model.new_bool_var(f"hc7_impossible_d{cov.day}_{cov.shift_id}_{skill}")
                model.add(forced_zero == 0)
                model.add(forced_zero == 1)
                continue
            model.add(sum(skilled) >= 1)

    # ---------- HC-2: one shift per day per nurse ----------
    for n in nurses:
        for d in days:
            cells = [x[(n.id, d, s)] for s in shift_ids if (n.id, d, s) in x]
            if cells:
                model.add(sum(cells) <= 1)

    # ---------- HC-3 / HC-6: forbidden transitions ----------
    # HC-6 (min rest) is encoded via forbiddenTransitions per spec; augment the
    # user-supplied list with pairs whose gap is below minRestHours.
    forbidden = set(instance.forbidden_transitions)
    if instance.min_rest_hours > 0:
        for s1 in shifts:
            for s2 in shifts:
                # Minutes from end of s1 on day d to start of s2 on day d+1.
                s1_end = s1.start_minutes + s1.duration_minutes
                # next-day start of s2 happens at 24*60 + s2.start_minutes.
                gap_minutes = (24 * 60 + s2.start_minutes) - s1_end
                if gap_minutes < instance.min_rest_hours * 60:
                    forbidden.add((s1.id, s2.id))

    for n in nurses:
        for d in range(instance.horizon_days - 1):
            for s1_id, s2_id in forbidden:
                a = x.get((n.id, d, s1_id))
                b = x.get((n.id, d + 1, s2_id))
                if a is not None and b is not None:
                    model.add(a + b <= 1)

    # ---------- HC-4: max consecutive working days ----------
    for n in nurses:
        cap = n.max_consecutive_working_days or instance.max_consecutive_working_days
        window = cap + 1
        if window > instance.horizon_days:
            continue
        for d0 in range(instance.horizon_days - window + 1):
            slab: list[cp_model.IntVar] = []
            for d in range(d0, d0 + window):
                for s in shift_ids:
                    v = x.get((n.id, d, s))
                    if v is not None:
                        slab.append(v)
            if slab:
                model.add(sum(slab) <= cap)

    # ---------- HC-5: max consecutive nights ----------
    night_ids = instance.night_shift_ids
    cap_nights = instance.max_consecutive_nights
    if night_ids and cap_nights > 0:
        window = cap_nights + 1
        if window <= instance.horizon_days:
            for n in nurses:
                for d0 in range(instance.horizon_days - window + 1):
                    slab_n: list[cp_model.IntVar] = []
                    for d in range(d0, d0 + window):
                        for s in night_ids:
                            v = x.get((n.id, d, s))
                            if v is not None:
                                slab_n.append(v)
                    if slab_n:
                        model.add(sum(slab_n) <= cap_nights)

    # ---------- HC-8: contract hours over rolling 7-day windows ----------
    if enforce_contract_hours:
        tol_minutes = instance.contract_tolerance_hours * 60
        # shift durations in minutes; CP-SAT needs integer coefs
        for n in nurses:
            contract_minutes_week = n.contract_hours_per_week * 60
            if contract_minutes_week <= 0:
                continue
            if instance.horizon_days < 7:
                # Apply a single scaled window over the whole horizon.
                scale = instance.horizon_days / 7.0
                scaled_contract = int(round(contract_minutes_week * scale))
                slab_terms: list[cp_model.IntVar | int] = []
                slab_coeffs: list[int] = []
                for d in range(instance.horizon_days):
                    for s in shifts:
                        v = x.get((n.id, d, s.id))
                        if v is not None:
                            slab_terms.append(v)
                            slab_coeffs.append(s.duration_minutes)
                if slab_terms:
                    expr = sum(c * v for c, v in zip(slab_coeffs, slab_terms, strict=True))
                    model.add(expr >= max(0, scaled_contract - tol_minutes))
                    model.add(expr <= scaled_contract + tol_minutes)
                continue
            for d0 in range(instance.horizon_days - 6):
                slab_terms2: list[cp_model.IntVar] = []
                slab_coeffs2: list[int] = []
                for d in range(d0, d0 + 7):
                    for s in shifts:
                        v = x.get((n.id, d, s.id))
                        if v is not None:
                            slab_terms2.append(v)
                            slab_coeffs2.append(s.duration_minutes)
                if slab_terms2:
                    expr2 = sum(
                        c * v for c, v in zip(slab_coeffs2, slab_terms2, strict=True)
                    )
                    model.add(expr2 >= max(0, contract_minutes_week - tol_minutes))
                    model.add(expr2 <= contract_minutes_week + tol_minutes)

    # ---------- HC-6b: min/max shifts per week (per-nurse override) ----------
    for n in nurses:
        if (
            n.min_shifts_per_week is not None or n.max_shifts_per_week is not None
        ) and instance.horizon_days >= 7:
            for d0 in range(instance.horizon_days - 6):
                slab_ms: list[cp_model.IntVar] = []
                for d in range(d0, d0 + 7):
                    for s in shift_ids:
                        v = x.get((n.id, d, s))
                        if v is not None:
                            slab_ms.append(v)
                if slab_ms:
                    if n.min_shifts_per_week is not None:
                        model.add(sum(slab_ms) >= n.min_shifts_per_week)
                    if n.max_shifts_per_week is not None:
                        model.add(sum(slab_ms) <= n.max_shifts_per_week)

    return model, ModelVars(x=x, instance=instance)
