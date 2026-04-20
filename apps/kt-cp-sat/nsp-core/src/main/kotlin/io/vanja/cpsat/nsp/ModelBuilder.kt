package io.vanja.cpsat.nsp

import io.vanja.cpsat.BoolVar
import io.vanja.cpsat.CpModel
import io.vanja.cpsat.IntVar
import io.vanja.cpsat.LinearExpr
import io.vanja.cpsat.atLeastOne
import io.vanja.cpsat.boolVar
import io.vanja.cpsat.constraint
import io.vanja.cpsat.cpModel
import io.vanja.cpsat.eq
import io.vanja.cpsat.ge
import io.vanja.cpsat.intVar
import io.vanja.cpsat.le
import io.vanja.cpsat.minus
import io.vanja.cpsat.plus
import io.vanja.cpsat.sum
import io.vanja.cpsat.times

/**
 * Shared NSP model builder used by chapters 11 (hard only), 12 (soft adds
 * objectives on top), and 13 (benchmark harness).
 *
 * The ladder:
 *
 * 1. Decision variables: `x[nurseId, day, shiftId] ∈ {0, 1}`.
 * 2. Hard constraints HC-1..HC-8 per spec `04-functional-requirements.md`.
 * 3. Optional soft-constraint helpers the caller can mount via [addSoftObjective].
 *
 * Each step is separated so chapter 11 can stop after step 2, chapter 12 can
 * plug in a weighted-sum or lexicographic objective on top, and chapter 13
 * can tune solver params without touching the model structure.
 */
public object ModelBuilder {

    /**
     * Build a fresh model for [instance] with hard constraints HC-1..HC-8
     * applied. Returns the model handle together with the [Decisions] map —
     * the caller can then add objectives and solve.
     */
    public fun buildHardModel(instance: Instance): Pair<CpModel, Decisions> {
        val decisions = Decisions.empty(instance)
        val model = cpModel {
            // Allocate x[n, d, s] — skip cells we can prove infeasible at modeling time.
            buildDecisionVars(instance, decisions)
            addHardConstraints(instance, decisions)
        }
        return model to decisions
    }

    /**
     * Adds SC-1..SC-5 soft-constraint penalty terms to the model and returns a
     * [SoftTerms] bundle. The caller chooses whether to minimize the weighted
     * sum (chapter 12 simple) or pass the stages to `solveLexicographic`.
     */
    public fun addSoftObjective(
        model: CpModel,
        instance: Instance,
        decisions: Decisions,
        weights: ObjectiveWeights,
    ): SoftTerms {
        return with(model) { buildSoftTerms(instance, decisions, weights) }
    }

    // -------------------------------------------------------------------------
    // Decision variables
    // -------------------------------------------------------------------------

    private fun CpModel.buildDecisionVars(instance: Instance, dec: Decisions) {
        val unavailablePerNurse = instance.nurses.associate { it.id to it.unavailableDays }
        val fixedOffPerNurse: Map<String, Set<Int>> =
            instance.fixedOff.groupBy { it.nurseId }.mapValues { (_, list) -> list.map { it.day }.toSet() }
        for (n in instance.nurses) {
            for (d in 0 until instance.horizonDays) {
                val off = unavailablePerNurse[n.id].orEmpty()
                val lockedOff = fixedOffPerNurse[n.id].orEmpty()
                if (d in off || d in lockedOff) continue
                for (s in instance.shifts) {
                    // Skip shift if skill mismatch (HC-7 pre-prune).
                    if (s.skill != null && s.skill !in n.skills) continue
                    val variable = boolVar("x_${n.id}_d${d}_${s.id}")
                    dec.x[Triple(n.id, d, s.id)] = variable
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Hard constraints HC-1..HC-8
    // -------------------------------------------------------------------------

    private fun CpModel.addHardConstraints(instance: Instance, dec: Decisions) {
        hc1Coverage(instance, dec)
        hc2OneShiftPerDay(instance, dec)
        hc3ForbiddenTransitions(instance, dec)
        hc4MaxConsecutiveWorking(instance, dec)
        hc5MaxConsecutiveNights(instance, dec)
        // HC-6 is folded into HC-3 via derived forbidden transitions in hc3.
        hc7SkillMatch(instance, dec)
        hc8ContractHours(instance, dec)
    }

    /**
     * HC-1. Coverage: for every `(day, shiftId)` cell, sum of assigned nurses
     * is in `[min, max]`.
     */
    private fun CpModel.hc1Coverage(instance: Instance, dec: Decisions) {
        for (req in instance.coverage) {
            val cells = instance.nurses.mapNotNull { n ->
                dec.x[Triple(n.id, req.day, req.shiftId)]
            }
            if (cells.isEmpty()) {
                // No nurse can possibly cover this cell — force infeasibility
                // when the requirement is active.
                if (req.min > 0) {
                    val sink = boolVar("__hc1_fail_${req.day}_${req.shiftId}")
                    constraint { +(sink eq 1L) }
                    constraint { +(sink eq 0L) }
                }
                continue
            }
            val cellsExpr: LinearExpr = sum(cells)
            constraint { +(cellsExpr ge req.min.toLong()) }
            constraint { +(cellsExpr le req.max.toLong()) }
        }
    }

    /** HC-2. At most one shift per day per nurse. */
    private fun CpModel.hc2OneShiftPerDay(instance: Instance, dec: Decisions) {
        for (n in instance.nurses) {
            for (d in 0 until instance.horizonDays) {
                val cells = instance.shifts.mapNotNull { s -> dec.x[Triple(n.id, d, s.id)] }
                if (cells.size > 1) {
                    val cellsExpr: LinearExpr = sum(cells)
                    constraint { +(cellsExpr le 1L) }
                }
            }
        }
    }

    /**
     * HC-3 + HC-6. Forbidden transitions from the instance, plus derived
     * transitions where the rest gap between `shift a end` on day d and
     * `shift b start` on day d+1 is less than `minRestHours`.
     */
    private fun CpModel.hc3ForbiddenTransitions(instance: Instance, dec: Decisions) {
        val pairs = buildSet {
            addAll(instance.forbiddenTransitions)
            // Derive rest-gap-violating pairs (HC-6 → HC-3 reduction per spec §HC-6).
            for (a in instance.shifts) {
                for (b in instance.shifts) {
                    if (restGapHours(a, b) < instance.minRestHours) {
                        add(a.id to b.id)
                    }
                }
            }
        }
        for (n in instance.nurses) {
            for (d in 0 until instance.horizonDays - 1) {
                for ((a, b) in pairs) {
                    val xa = dec.x[Triple(n.id, d, a)]
                    val xb = dec.x[Triple(n.id, d + 1, b)]
                    if (xa != null && xb != null) {
                        val sumExpr: LinearExpr = xa + xb
                        constraint { +(sumExpr le 1L) }
                    }
                }
            }
        }
    }

    /**
     * Rest gap in hours between shift [a] ending on day d and shift [b]
     * starting on day d+1. End of `a` is `startMinutes + durationMinutes`;
     * start of `b` is `startMinutes + 24*60`.
     */
    private fun restGapHours(a: Shift, b: Shift): Int {
        val aEndMin = a.startMinutes + a.durationMinutes
        val bStartMin = b.startMinutes + 24 * 60
        val gapMin = bStartMin - aEndMin
        return gapMin / 60
    }

    /** HC-4. Max consecutive working days: sliding window of `K+1` days ≤ K. */
    private fun CpModel.hc4MaxConsecutiveWorking(instance: Instance, dec: Decisions) {
        for (n in instance.nurses) {
            val k = n.maxConsecutiveWorkingDays ?: instance.maxConsecutiveWorkingDays
            if (k < 1 || k >= instance.horizonDays) continue
            for (startDay in 0..(instance.horizonDays - k - 1)) {
                val window = mutableListOf<BoolVar>()
                for (d in startDay..(startDay + k)) {
                    for (s in instance.shifts) {
                        dec.x[Triple(n.id, d, s.id)]?.let { window += it }
                    }
                }
                if (window.isNotEmpty()) {
                    val winExpr: LinearExpr = sum(window)
                    constraint { +(winExpr le k.toLong()) }
                }
            }
        }
    }

    /** HC-5. Max consecutive night shifts (default 3). */
    private fun CpModel.hc5MaxConsecutiveNights(instance: Instance, dec: Decisions) {
        val nights = instance.nightShiftIds
        if (nights.isEmpty()) return
        val k = instance.maxConsecutiveNights
        if (k < 1 || k >= instance.horizonDays) return
        for (n in instance.nurses) {
            for (startDay in 0..(instance.horizonDays - k - 1)) {
                val window = mutableListOf<BoolVar>()
                for (d in startDay..(startDay + k)) {
                    for (s in nights) {
                        dec.x[Triple(n.id, d, s)]?.let { window += it }
                    }
                }
                if (window.size > k) {
                    val winExpr: LinearExpr = sum(window)
                    constraint { +(winExpr le k.toLong()) }
                }
            }
        }
    }

    /**
     * HC-7. Skill match on coverage: every `requiredSkill` on a coverage cell
     * must have at least one holder assigned.
     *
     * The shift-level skill (HC-7 second clause) is enforced at variable
     * allocation time — cells where `shift.skill !∈ nurse.skills` are never
     * created (see [buildDecisionVars]).
     */
    private fun CpModel.hc7SkillMatch(instance: Instance, dec: Decisions) {
        for (req in instance.coverage) {
            if (req.requiredSkills.isEmpty()) continue
            for (skill in req.requiredSkills) {
                val holders = instance.nurses
                    .filter { skill in it.skills }
                    .mapNotNull { n -> dec.x[Triple(n.id, req.day, req.shiftId)] }
                if (holders.isEmpty()) {
                    // No holder can cover — mark infeasible if the requirement is active.
                    val sink = boolVar("__hc7_fail_${req.day}_${req.shiftId}_$skill")
                    constraint { +(sink eq 1L) }
                    constraint { +(sink eq 0L) }
                    continue
                }
                atLeastOne(holders)
            }
        }
    }

    /**
     * HC-8. Contract hours: every rolling 7-day window's total hours lies in
     * `[contract - tol, contract + tol]`.
     *
     * If the horizon is shorter than 7 days, we scale proportionally per spec.
     */
    private fun CpModel.hc8ContractHours(instance: Instance, dec: Decisions) {
        val tolMin = instance.contractTolerance.toLong() * 60L
        if (instance.horizonDays >= 7) {
            for (n in instance.nurses) {
                val contractMin = n.contractHoursPerWeek.toLong() * 60L
                val low = (contractMin - tolMin).coerceAtLeast(0L)
                val high = contractMin + tolMin
                for (start in 0..(instance.horizonDays - 7)) {
                    val hoursExpr = buildHoursExpr(instance, dec, n.id, start until (start + 7))
                    if (hoursExpr != null) {
                        constraint { +(hoursExpr ge low) }
                        constraint { +(hoursExpr le high) }
                    }
                }
            }
        } else {
            // Shorter-than-a-week horizon: scale the weekly contract proportionally.
            val scale = instance.horizonDays.toDouble() / 7.0
            for (n in instance.nurses) {
                val contractMin = (n.contractHoursPerWeek.toLong() * 60L * scale).toLong()
                val low = (contractMin - tolMin).coerceAtLeast(0L)
                val high = contractMin + tolMin
                val hoursExpr = buildHoursExpr(instance, dec, n.id, 0 until instance.horizonDays)
                if (hoursExpr != null) {
                    constraint { +(hoursExpr ge low) }
                    constraint { +(hoursExpr le high) }
                }
            }
        }
    }

    /** Sum of shift-duration-minutes for [nurseId] over [daysRange]. */
    private fun CpModel.buildHoursExpr(
        instance: Instance,
        dec: Decisions,
        nurseId: String,
        daysRange: IntRange,
    ): LinearExpr? {
        val contribs = mutableListOf<LinearExpr>()
        for (d in daysRange) {
            for (s in instance.shifts) {
                val v = dec.x[Triple(nurseId, d, s.id)] ?: continue
                contribs += v * s.durationMinutesLong
            }
        }
        return if (contribs.isEmpty()) null else sum(contribs)
    }

    // -------------------------------------------------------------------------
    // Soft-constraint penalty construction (SC-1..SC-5)
    // -------------------------------------------------------------------------

    private fun CpModel.buildSoftTerms(
        instance: Instance,
        dec: Decisions,
        weights: ObjectiveWeights,
    ): SoftTerms {
        val sc1 = buildSc1Preferences(instance, dec)
        val sc2 = buildSc2FairnessSpread(instance, dec)
        val sc3 = buildSc3WorkloadSpread(instance, dec)
        val sc4 = buildSc4WeekendSpread(instance, dec)
        val sc5 = buildSc5IsolatedOff(instance, dec)
        return SoftTerms(sc1 = sc1, sc2 = sc2, sc3 = sc3, sc4 = sc4, sc5 = sc5, weights = weights)
    }

    /**
     * SC-1. Preference penalties.
     * - Positive weight (nurse *wants* it) → penalty = w when the cell is NOT honored.
     * - Negative weight (nurse wants to *avoid*) → penalty = |w| when the cell IS assigned.
     *
     * For shift-off preferences (shiftId == null) the "cell" is the union of
     * all shifts on that day, treated as a 0/1 "works" indicator via sum.
     */
    private fun CpModel.buildSc1Preferences(instance: Instance, dec: Decisions): LinearExpr? {
        if (instance.preferences.isEmpty()) return null
        val terms = mutableListOf<LinearExpr>()
        for (p in instance.preferences) {
            val absW = kotlin.math.abs(p.weight).toLong()
            if (absW == 0L) continue
            if (p.shiftId == null) {
                // Day-off preference: works[n, d] = sum over shifts of x[n, d, s].
                val works = instance.shifts.mapNotNull { s -> dec.x[Triple(p.nurseId, p.day, s.id)] }
                if (works.isEmpty()) continue
                val worksExpr: LinearExpr = sum(works)
                if (p.weight > 0) {
                    // Wants day off. Penalty = w * works (non-zero only if they worked).
                    terms += worksExpr * absW
                } else {
                    // Wants to work that day. Penalty = w * (1 - works).
                    terms += (absW - worksExpr * absW)
                }
            } else {
                val x = dec.x[Triple(p.nurseId, p.day, p.shiftId)] ?: continue
                if (p.weight > 0) {
                    // Wants shift. Penalty = w * (1 - x).
                    terms += (absW - x * absW)
                } else {
                    // Wants to avoid shift. Penalty = w * x.
                    terms += x * absW
                }
            }
        }
        if (terms.isEmpty()) return null
        return sum(terms)
    }

    /** SC-2. Fairness: max_n total_n - min_n total_n. */
    private fun CpModel.buildSc2FairnessSpread(instance: Instance, dec: Decisions): LinearExpr? {
        val totals = instance.nurses.map { n ->
            val cells = mutableListOf<BoolVar>()
            for (d in 0 until instance.horizonDays) for (s in instance.shifts) {
                dec.x[Triple(n.id, d, s.id)]?.let { cells += it }
            }
            val t = intVar("sc2_total_${n.id}", 0..(instance.horizonDays * instance.shifts.size))
            if (cells.isEmpty()) {
                constraint { +(t eq 0L) }
            } else {
                val cellsExpr: LinearExpr = sum(cells)
                constraint { +(t eq cellsExpr) }
            }
            t
        }
        return spreadExpr(totals, "sc2", 0L, (instance.horizonDays * instance.shifts.size).toLong())
    }

    /**
     * SC-3. Workload balance on absolute minutes worked. Penalize the spread
     * in total minutes across nurses.
     */
    private fun CpModel.buildSc3WorkloadSpread(instance: Instance, dec: Decisions): LinearExpr? {
        val horizonMinutes = (instance.horizonDays * 24 * 60).toLong()
        val totalsMin = instance.nurses.map { n ->
            val terms = mutableListOf<LinearExpr>()
            for (d in 0 until instance.horizonDays) for (s in instance.shifts) {
                val v = dec.x[Triple(n.id, d, s.id)] ?: continue
                terms += v * s.durationMinutesLong
            }
            val t = intVar("sc3_min_${n.id}", 0L..horizonMinutes)
            if (terms.isEmpty()) {
                constraint { +(t eq 0L) }
            } else {
                val termsExpr: LinearExpr = sum(terms)
                constraint { +(t eq termsExpr) }
            }
            t
        }
        return spreadExpr(totalsMin, "sc3", 0L, horizonMinutes)
    }

    /** SC-4. Weekend distribution spread across nurses. */
    private fun CpModel.buildSc4WeekendSpread(instance: Instance, dec: Decisions): LinearExpr? {
        val weekendDays = (0 until instance.horizonDays).filter { instance.isWeekend(it) }
        if (weekendDays.isEmpty()) return null
        val perNurse = instance.nurses.map { n ->
            val cells = mutableListOf<BoolVar>()
            for (d in weekendDays) for (s in instance.shifts) {
                dec.x[Triple(n.id, d, s.id)]?.let { cells += it }
            }
            val t = intVar("sc4_wkend_${n.id}", 0..weekendDays.size * instance.shifts.size)
            if (cells.isEmpty()) {
                constraint { +(t eq 0L) }
            } else {
                val cellsExpr: LinearExpr = sum(cells)
                constraint { +(t eq cellsExpr) }
            }
            t
        }
        return spreadExpr(perNurse, "sc4", 0L, (weekendDays.size * instance.shifts.size).toLong())
    }

    /**
     * SC-5. Isolated day-off indicator: `work(d-1) AND off(d) AND work(d+1)`.
     * Count such days across all nurses.
     */
    private fun CpModel.buildSc5IsolatedOff(instance: Instance, dec: Decisions): LinearExpr? {
        if (instance.horizonDays < 3) return null
        val indicators = mutableListOf<LinearExpr>()
        for (n in instance.nurses) {
            for (d in 1..(instance.horizonDays - 2)) {
                val cellsPrev = instance.shifts.mapNotNull { dec.x[Triple(n.id, d - 1, it.id)] }
                val cellsHere = instance.shifts.mapNotNull { dec.x[Triple(n.id, d, it.id)] }
                val cellsNext = instance.shifts.mapNotNull { dec.x[Triple(n.id, d + 1, it.id)] }
                if (cellsPrev.isEmpty() || cellsHere.isEmpty() || cellsNext.isEmpty()) continue

                val worksPrev = boolVar("sc5_wprev_${n.id}_d$d")
                val offHere = boolVar("sc5_off_${n.id}_d$d")
                val worksNext = boolVar("sc5_wnext_${n.id}_d$d")
                val isolated = boolVar("sc5_iso_${n.id}_d$d")

                // worksPrev == sum(cellsPrev) (0 or 1 given HC-2)
                val prevExpr: LinearExpr = sum(cellsPrev)
                val hereExpr: LinearExpr = sum(cellsHere)
                val nextExpr: LinearExpr = sum(cellsNext)
                constraint { +(worksPrev eq prevExpr) }
                // offHere == 1 - sum(cellsHere)  ⇔  offHere + sum(cellsHere) == 1
                val offSumExpr: LinearExpr = offHere + hereExpr
                constraint { +(offSumExpr eq 1L) }
                constraint { +(worksNext eq nextExpr) }
                // isolated = worksPrev AND offHere AND worksNext
                constraint { +(isolated le worksPrev) }
                constraint { +(isolated le offHere) }
                constraint { +(isolated le worksNext) }
                val andExpr: LinearExpr = worksPrev + offHere + worksNext - 2L
                constraint { +(isolated ge andExpr) }

                indicators += isolated
            }
        }
        if (indicators.isEmpty()) return null
        return sum(indicators)
    }

    /** Build `max(xs) - min(xs)` as a LinearExpr. Returns null if [xs] is empty. */
    private fun CpModel.spreadExpr(
        xs: List<IntVar>,
        tag: String,
        lo: Long,
        hi: Long,
    ): LinearExpr? {
        if (xs.isEmpty()) return null
        val maxVar = intVar("${tag}_max", lo..hi)
        val minVar = intVar("${tag}_min", lo..hi)
        for (x in xs) {
            constraint { +(maxVar ge x) }
            constraint { +(minVar le x) }
        }
        // Objective pressure makes these tight. No equality to a specific xi
        // needed — the minimization keeps max as low and min as high as possible.
        return maxVar - minVar
    }
}

/**
 * Soft-constraint penalty expressions, one per SC family. Null means the
 * family did not contribute anything for this instance.
 */
public data class SoftTerms(
    val sc1: LinearExpr?,
    val sc2: LinearExpr?,
    val sc3: LinearExpr?,
    val sc4: LinearExpr?,
    val sc5: LinearExpr?,
    val weights: ObjectiveWeights,
) {
    /** Weighted-sum objective expression. Returns null if all SCs are inactive. */
    public fun weightedSum(): LinearExpr? {
        val parts = buildList {
            sc1?.let { add(it * weights.sc1.toLong()) }
            sc2?.let { add(it * weights.sc2.toLong()) }
            sc3?.let { add(it * weights.sc3.toLong()) }
            sc4?.let { add(it * weights.sc4.toLong()) }
            sc5?.let { add(it * weights.sc5.toLong()) }
        }
        return if (parts.isEmpty()) null else sum(parts)
    }

    /** The list of non-null SC terms in SC-1..SC-5 order. */
    public val nonNullTerms: List<Pair<String, LinearExpr>>
        get() = buildList {
            sc1?.let { add("SC-1" to it) }
            sc2?.let { add("SC-2" to it) }
            sc3?.let { add("SC-3" to it) }
            sc4?.let { add("SC-4" to it) }
            sc5?.let { add("SC-5" to it) }
        }
}

/**
 * Decision variable table. Key is `Triple(nurseId, day, shiftId)`.
 * Absent key = the cell is structurally disallowed (unavailable / skill
 * mismatch / pre-locked off).
 */
public class Decisions private constructor(
    public val instance: Instance,
    public val x: MutableMap<Triple<String, Int, String>, BoolVar>,
) {
    public fun get(nurseId: String, day: Int, shiftId: String): BoolVar? =
        x[Triple(nurseId, day, shiftId)]

    public companion object {
        public fun empty(instance: Instance): Decisions =
            Decisions(instance, HashMap())
    }
}
