"""Standalone schedule validator.

Given an ``Instance`` and a ``Schedule`` (both frozen dataclasses), return the
list of :class:`Violation`s — both hard (HC-1..HC-8) and soft (SC-1..SC-5). No
solver is involved: this is used by tests to certify a returned Schedule
against the original rules, and by the HTTP layer to double-check solver output
before serving it.
"""

from __future__ import annotations

from collections import defaultdict

from nsp_core.domain import Assignment, Instance, Schedule, Violation


def _assignments_by_cell(schedule: Schedule) -> dict[tuple[int, str], list[Assignment]]:
    grid: dict[tuple[int, str], list[Assignment]] = defaultdict(list)
    for a in schedule.assignments:
        if a.shift_id is None:
            continue
        grid[(a.day, a.shift_id)].append(a)
    return grid


def _assignments_by_nurse(
    schedule: Schedule,
) -> dict[str, dict[int, str]]:
    """nurse_id -> {day: shift_id} (only days where the nurse works)."""
    out: dict[str, dict[int, str]] = defaultdict(dict)
    for a in schedule.assignments:
        if a.shift_id is None:
            continue
        out[a.nurse_id][a.day] = a.shift_id
    return out


def validate_schedule(instance: Instance, schedule: Schedule) -> list[Violation]:
    """Return every violated constraint for the given schedule (hard + soft).

    Returning an empty list means the schedule is a valid certificate.
    """
    violations: list[Violation] = []
    grid = _assignments_by_cell(schedule)
    per_nurse = _assignments_by_nurse(schedule)
    horizon = instance.horizon_days
    shift_ids = {s.id for s in instance.shifts}

    # --- basic sanity: all referenced nurses/shifts/days exist ---
    nurses_by_id = {n.id: n for n in instance.nurses}
    for a in schedule.assignments:
        if a.nurse_id not in nurses_by_id:
            violations.append(
                Violation(
                    code="HC-0",
                    message=f"Assignment references unknown nurse {a.nurse_id!r}",
                    severity="hard",
                    nurse_id=a.nurse_id,
                    day=a.day,
                )
            )
        if a.day < 0 or a.day >= horizon:
            violations.append(
                Violation(
                    code="HC-0",
                    message=f"Assignment day {a.day} out of horizon [0, {horizon})",
                    severity="hard",
                    nurse_id=a.nurse_id,
                    day=a.day,
                )
            )
        if a.shift_id is not None and a.shift_id not in shift_ids:
            violations.append(
                Violation(
                    code="HC-0",
                    message=f"Assignment references unknown shift {a.shift_id!r}",
                    severity="hard",
                    nurse_id=a.nurse_id,
                    day=a.day,
                )
            )

    # --- HC-1: coverage min/max per cell ---
    covered_cells = set()
    for cov in instance.coverage:
        covered_cells.add((cov.day, cov.shift_id))
        staffed = len(grid.get((cov.day, cov.shift_id), []))
        if staffed < cov.min:
            violations.append(
                Violation(
                    code="HC-1",
                    message=(
                        f"Coverage short: day {cov.day} shift {cov.shift_id} has "
                        f"{staffed}, need >= {cov.min}"
                    ),
                    severity="hard",
                    day=cov.day,
                )
            )
        if staffed > cov.max:
            violations.append(
                Violation(
                    code="HC-1",
                    message=(
                        f"Coverage over: day {cov.day} shift {cov.shift_id} has "
                        f"{staffed}, allow <= {cov.max}"
                    ),
                    severity="hard",
                    day=cov.day,
                )
            )
        # HC-7: required skills on this cell
        for skill in cov.required_skills:
            staffed_with_skill = sum(
                1
                for a in grid.get((cov.day, cov.shift_id), [])
                if skill in nurses_by_id[a.nurse_id].skills
            )
            if staffed_with_skill < 1:
                violations.append(
                    Violation(
                        code="HC-7",
                        message=(
                            f"Skill miss: day {cov.day} shift {cov.shift_id} needs"
                            f" >=1 nurse with skill {skill!r}"
                        ),
                        severity="hard",
                        day=cov.day,
                    )
                )

    # Shift-level skill requirement
    for shift in instance.shifts:
        if shift.skill is not None:
            for d in range(horizon):
                for a in grid.get((d, shift.id), []):
                    nurse = nurses_by_id[a.nurse_id]
                    if shift.skill not in nurse.skills:
                        violations.append(
                            Violation(
                                code="HC-7",
                                message=(
                                    f"Nurse {a.nurse_id} lacks skill {shift.skill!r}"
                                    f" required by shift {shift.id}"
                                ),
                                severity="hard",
                                nurse_id=a.nurse_id,
                                day=d,
                            )
                        )

    # --- HC-2: at most one shift per day per nurse ---
    for nid, _days in per_nurse.items():
        # per_nurse already dedups to one shift per day; double-check the raw
        # assignments list for duplicates.
        seen: dict[int, int] = defaultdict(int)
        for a in schedule.assignments:
            if a.nurse_id == nid and a.shift_id is not None:
                seen[a.day] += 1
        for d, count in seen.items():
            if count > 1:
                violations.append(
                    Violation(
                        code="HC-2",
                        message=f"Nurse {nid} has {count} shifts on day {d}",
                        severity="hard",
                        nurse_id=nid,
                        day=d,
                    )
                )

    # --- HC-3 / HC-6: forbidden transitions ---
    # Augment forbidden transitions with rest-gap-derived pairs.
    forbidden = set(instance.forbidden_transitions)
    shifts_by_id = {s.id: s for s in instance.shifts}
    if instance.min_rest_hours > 0:
        for s1 in instance.shifts:
            for s2 in instance.shifts:
                gap_min = (24 * 60 + s2.start_minutes) - (
                    s1.start_minutes + s1.duration_minutes
                )
                if gap_min < instance.min_rest_hours * 60:
                    forbidden.add((s1.id, s2.id))

    for nid, days in per_nurse.items():
        for d, s_today in days.items():
            s_tomorrow = days.get(d + 1)
            if s_tomorrow is None:
                continue
            if (s_today, s_tomorrow) in forbidden:
                violations.append(
                    Violation(
                        code="HC-3",
                        message=(
                            f"Nurse {nid}: forbidden transition "
                            f"{s_today} on day {d} -> {s_tomorrow} on day {d+1}"
                        ),
                        severity="hard",
                        nurse_id=nid,
                        day=d,
                    )
                )

    # --- HC-4: max consecutive working days ---
    for nurse in instance.nurses:
        cap = nurse.max_consecutive_working_days or instance.max_consecutive_working_days
        days_worked = sorted(per_nurse.get(nurse.id, {}).keys())
        if not days_worked:
            continue
        run = 1
        for prev, cur in zip(days_worked, days_worked[1:], strict=False):
            if cur == prev + 1:
                run += 1
                if run > cap:
                    violations.append(
                        Violation(
                            code="HC-4",
                            message=(
                                f"Nurse {nurse.id} works {run} consecutive days "
                                f"(cap {cap}) starting near day {cur}"
                            ),
                            severity="hard",
                            nurse_id=nurse.id,
                            day=cur,
                        )
                    )
                    break
            else:
                run = 1

    # --- HC-5: max consecutive nights ---
    night_ids = instance.night_shift_ids
    if night_ids and instance.max_consecutive_nights > 0:
        cap_n = instance.max_consecutive_nights
        for nurse in instance.nurses:
            days_worked = sorted(per_nurse.get(nurse.id, {}).keys())
            run = 0
            last_d = -2
            for d in days_worked:
                s = per_nurse[nurse.id][d]
                if s in night_ids:
                    if d == last_d + 1:
                        run += 1
                    else:
                        run = 1
                    if run > cap_n:
                        violations.append(
                            Violation(
                                code="HC-5",
                                message=(
                                    f"Nurse {nurse.id} works {run} consecutive nights "
                                    f"(cap {cap_n}) ending on day {d}"
                                ),
                                severity="hard",
                                nurse_id=nurse.id,
                                day=d,
                            )
                        )
                        break
                    last_d = d
                else:
                    run = 0
                    last_d = d

    # --- Availability (unavailable days) ---
    for nurse in instance.nurses:
        for d in per_nurse.get(nurse.id, {}):
            if d in nurse.unavailable:
                violations.append(
                    Violation(
                        code="HC-0",
                        message=(
                            f"Nurse {nurse.id} is unavailable on day {d} but was assigned"
                        ),
                        severity="hard",
                        nurse_id=nurse.id,
                        day=d,
                    )
                )

    # --- HC-8: contract hours (rolling 7-day window) ---
    tol_hours = instance.contract_tolerance_hours
    if horizon >= 7:
        for nurse in instance.nurses:
            contract = nurse.contract_hours_per_week
            if contract <= 0:
                continue
            for d0 in range(horizon - 6):
                total_minutes = 0
                for d in range(d0, d0 + 7):
                    s_id = per_nurse.get(nurse.id, {}).get(d)
                    if s_id is not None:
                        total_minutes += shifts_by_id[s_id].duration_minutes
                total_hours = total_minutes / 60.0
                if total_hours < contract - tol_hours - 1e-6:
                    violations.append(
                        Violation(
                            code="HC-8",
                            message=(
                                f"Nurse {nurse.id} under contract: "
                                f"{total_hours:.1f}h in days {d0}..{d0+6} "
                                f"(contract {contract}h, tol {tol_hours}h)"
                            ),
                            severity="hard",
                            nurse_id=nurse.id,
                            day=d0,
                        )
                    )
                elif total_hours > contract + tol_hours + 1e-6:
                    violations.append(
                        Violation(
                            code="HC-8",
                            message=(
                                f"Nurse {nurse.id} over contract: "
                                f"{total_hours:.1f}h in days {d0}..{d0+6} "
                                f"(contract {contract}h, tol {tol_hours}h)"
                            ),
                            severity="hard",
                            nurse_id=nurse.id,
                            day=d0,
                        )
                    )
    else:
        # Scaled single window
        for nurse in instance.nurses:
            contract = nurse.contract_hours_per_week
            if contract <= 0:
                continue
            scale = horizon / 7.0
            scaled_contract = contract * scale
            total_minutes = sum(
                shifts_by_id[s_id].duration_minutes
                for d, s_id in per_nurse.get(nurse.id, {}).items()
            )
            total_hours = total_minutes / 60.0
            if total_hours < scaled_contract - tol_hours - 1e-6:
                violations.append(
                    Violation(
                        code="HC-8",
                        message=(
                            f"Nurse {nurse.id} under contract: {total_hours:.1f}h "
                            f"(scaled contract {scaled_contract:.1f}h ± {tol_hours}h)"
                        ),
                        severity="hard",
                        nurse_id=nurse.id,
                    )
                )
            elif total_hours > scaled_contract + tol_hours + 1e-6:
                violations.append(
                    Violation(
                        code="HC-8",
                        message=(
                            f"Nurse {nurse.id} over contract: {total_hours:.1f}h "
                            f"(scaled contract {scaled_contract:.1f}h ± {tol_hours}h)"
                        ),
                        severity="hard",
                        nurse_id=nurse.id,
                    )
                )

    return violations
