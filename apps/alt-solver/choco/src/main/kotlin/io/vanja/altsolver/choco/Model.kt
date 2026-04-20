package io.vanja.altsolver.choco

import org.chocosolver.solver.Model
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.variables.IntVar

/**
 * Compiled form of an NSP instance with Choco variables + constraints ready
 * to solve. Owned by [NspModelBuilder]; the solver entry point accesses
 * `model`, `x`, and `objective`.
 *
 * Decision variables:
 *  - `x[n][d]` — integer in `0..S`, where `0..S-1` code shift indices in
 *    [shiftIds] order, and `S` codes "off". Each cell is a one-shift-per-
 *    nurse-per-day choice (HC-2 is structural — a single variable can't
 *    take two values).
 *
 * Auxiliary booleans (precomputed once):
 *  - `working[n][d]` — true iff `x[n][d] != OFF`, for HC-4.
 *  - `isNight[n][d]` — true iff `x[n][d] = nightIdx`, for HC-5.
 *
 * Objective: integer variable `objective` minimised by the solver; equals
 * the weighted soft-violation total (SC-1 + SC-2). Hard constraints are
 * posted as absolute bans so any solution the search returns is feasible
 * by construction.
 */
internal class NspCompiled(
    val instance: InstanceJson,
    val model: Model,
    val nurseIds: List<String>,
    val shiftIds: List<String>,
    val x: Array<Array<IntVar>>,
    val working: Array<Array<BoolVar>>,
    val isNight: Array<Array<BoolVar>>,
    val objective: IntVar,
) {
    val nN: Int get() = nurseIds.size
    val nD: Int get() = instance.horizonDays
    val nS: Int get() = shiftIds.size
    val OFF: Int get() = nS
}

/**
 * Turns a parsed [InstanceJson] into a Choco model with HC-1..HC-5 posted as
 * hard constraints and SC-1 + SC-2 captured in the minimization objective.
 */
internal object NspModelBuilder {

    fun compile(instance: InstanceJson): NspCompiled {
        val model = Model("nsp-choco-${instance.id ?: "anon"}")

        val nurseIds = instance.nurses.map { it.id }
        val shiftIds = instance.shifts.map { it.id }
        val nN = nurseIds.size
        val nD = instance.horizonDays
        val nS = shiftIds.size
        val OFF = nS

        val shiftIdxById = shiftIds.withIndex().associate { (idx, id) -> id to idx }
        val shiftIsNight = BooleanArray(nS).also { arr ->
            instance.shifts.forEachIndexed { idx, s ->
                arr[idx] = s.isNight ?: classifyAsNight(s.id, s.label, s.startMinutes)
            }
        }

        // Decision variables
        val x: Array<Array<IntVar>> = Array(nN) { n ->
            Array(nD) { d -> model.intVar("x_${nurseIds[n]}_$d", 0, OFF) }
        }

        // Aux bools
        val working: Array<Array<BoolVar>> = Array(nN) { n ->
            Array(nD) { d ->
                // working = (x != OFF)
                val b = model.boolVar("working_${nurseIds[n]}_$d")
                // reify "x[n][d] != OFF" with b
                model.arithm(x[n][d], "!=", OFF).reifyWith(b)
                b
            }
        }
        val isNight: Array<Array<BoolVar>> = Array(nN) { n ->
            Array(nD) { d ->
                val b = model.boolVar("night_${nurseIds[n]}_$d")
                // choose one reification that matches every night-shift index.
                // Toy instances have exactly one night shift id, so a simple
                // equality reify is enough; for multi-night-shift instances we
                // OR the disjuncts via a dedicated constraint.
                val nightIndices = shiftIsNight
                    .withIndex()
                    .filter { (_, isN) -> isN }
                    .map { it.index }
                when (nightIndices.size) {
                    0 -> model.arithm(b, "=", 0).post()
                    1 -> model.arithm(x[n][d], "=", nightIndices.first()).reifyWith(b)
                    else -> {
                        // b <=> x[n][d] in {nightIndices}
                        val disjunctions = nightIndices.map { i ->
                            model.arithm(x[n][d], "=", i).reify()
                        }.toTypedArray()
                        model.max(b, disjunctions).post()
                    }
                }
                b
            }
        }

        // Fixed-off days: force x[n][d] = OFF for any nurse's unavailable days
        for ((n, nurse) in instance.nurses.withIndex()) {
            for (d in nurse.unavailable) {
                if (d in 0 until nD) {
                    model.arithm(x[n][d], "=", OFF).post()
                }
            }
        }

        // HC-1 coverage. For each (day, shift) in `coverage`, the count of
        // nurses assigned that shift on that day must lie in [min, max].
        // Coverage rows that don't appear for a given (d, s) leave no
        // constraint — that shift isn't required on that day.
        for (row in instance.coverage) {
            val sIdx = shiftIdxById[row.shiftId]
                ?: error("Coverage references unknown shift '${row.shiftId}'")
            val colExprs = Array(nN) { n -> x[n][row.day] }
            // `count(value, vars, resultVar)`; encode `min..max` range by
            // intersecting count with an intvar over the allowed bounds.
            val slotCount = model.intVar("cov_d${row.day}_s${row.shiftId}", row.min, row.max)
            model.count(sIdx, colExprs, slotCount).post()
        }

        // HC-3 forbidden transitions. For each forbidden (s1, s2): no nurse
        // may work s1 on day d and s2 on day d+1.
        val forbiddenSet = instance.forbiddenTransitions
            .filter { it.size == 2 }
            .mapNotNull { pair ->
                val a = shiftIdxById[pair[0]] ?: return@mapNotNull null
                val b = shiftIdxById[pair[1]] ?: return@mapNotNull null
                a to b
            }
        for (n in 0 until nN) {
            for (d in 0 until nD - 1) {
                for ((s1, s2) in forbiddenSet) {
                    val prev = model.arithm(x[n][d], "=", s1).reify()
                    val next = model.arithm(x[n][d + 1], "=", s2).reify()
                    // prev + next <= 1 rules out prev == next == 1.
                    model.sum(arrayOf(prev, next), "<=", 1).post()
                }
            }
        }

        // HC-4 max consecutive working days.
        // Sliding window of size K+1: sum(working[n][d..d+K]) <= K  (i.e. no
        // window of K+1 days is fully worked).
        val maxConsec = instance.maxConsecutiveWorkingDays
        if (maxConsec in 1 until nD) {
            val k = maxConsec
            for (n in 0 until nN) {
                for (start in 0..nD - (k + 1)) {
                    val window = (start..start + k).map { d -> working[n][d] }.toTypedArray()
                    model.sum(window, "<=", k).post()
                }
            }
        }

        // HC-5 max consecutive nights.
        val maxNights = instance.maxConsecutiveNights
        if (maxNights in 1 until nD) {
            val k = maxNights
            for (n in 0 until nN) {
                for (start in 0..nD - (k + 1)) {
                    val window = (start..start + k).map { d -> isNight[n][d] }.toTypedArray()
                    model.sum(window, "<=", k).post()
                }
            }
        }

        // Soft objective — SC-1 preferences + SC-2 fairness spread.
        val objective = buildObjective(
            model = model,
            instance = instance,
            nurseIds = nurseIds,
            shiftIdxById = shiftIdxById,
            x = x,
            working = working,
            off = OFF,
        )

        model.setObjective(Model.MINIMIZE, objective)

        return NspCompiled(
            instance = instance,
            model = model,
            nurseIds = nurseIds,
            shiftIds = shiftIds,
            x = x,
            working = working,
            isNight = isNight,
            objective = objective,
        )
    }

    /**
     * Build the soft-constraint objective as `sum(SC-1 penalties) + (max - min)`
     * where max/min are over per-nurse total shift counts.
     */
    private fun buildObjective(
        model: Model,
        instance: InstanceJson,
        nurseIds: List<String>,
        shiftIdxById: Map<String, Int>,
        x: Array<Array<IntVar>>,
        working: Array<Array<BoolVar>>,
        off: Int,
    ): IntVar {
        val nN = nurseIds.size
        val nD = instance.horizonDays
        val penaltyTerms = mutableListOf<IntVar>()

        // SC-1. For each preference, materialise a penalty IntVar via
        // `times(violationBool, weight, penaltyVar)` -> penaltyVar == w when
        // the preference is violated, 0 otherwise.
        val nIdxById = nurseIds.withIndex().associate { (i, id) -> id to i }
        for (p in instance.preferences) {
            val n = nIdxById[p.nurseId] ?: continue
            if (p.day !in 0 until nD) continue
            when (p.kind) {
                "avoid" -> {
                    val sIdx = shiftIdxById[p.shiftId] ?: continue
                    val violated = model.arithm(x[n][p.day], "=", sIdx).reify()
                    val term = model.intVar(
                        "pen_avoid_${p.nurseId}_d${p.day}_s${p.shiftId}", 0, p.weight,
                    )
                    model.times(violated, p.weight, term).post()
                    penaltyTerms += term
                }
                "prefer" -> {
                    if (p.shiftId == null) {
                        // prefer day off: violation iff working[n][d] == 1
                        val term = model.intVar(
                            "pen_off_${p.nurseId}_d${p.day}", 0, p.weight,
                        )
                        model.times(working[n][p.day], p.weight, term).post()
                        penaltyTerms += term
                    } else {
                        // prefer this shift: violation iff x != shiftIdx
                        val sIdx = shiftIdxById[p.shiftId] ?: continue
                        val missed = model.arithm(x[n][p.day], "!=", sIdx).reify()
                        val term = model.intVar(
                            "pen_want_${p.nurseId}_d${p.day}_s${p.shiftId}", 0, p.weight,
                        )
                        model.times(missed, p.weight, term).post()
                        penaltyTerms += term
                    }
                }
            }
        }

        // SC-2. Per-nurse total shift count, then spread = max - min.
        val nurseTotals: Array<IntVar> = Array(nN) { n ->
            val total = model.intVar("total_${nurseIds[n]}", 0, nD)
            // Total = sum over days of working[n][d]
            model.sum((0 until nD).map { d -> working[n][d] }.toTypedArray(), "=", total).post()
            total
        }
        val maxTotal = model.intVar("fairMax", 0, nD)
        val minTotal = model.intVar("fairMin", 0, nD)
        model.max(maxTotal, nurseTotals).post()
        model.min(minTotal, nurseTotals).post()
        val spread = model.intVar("fairSpread", 0, nD)
        // spread = maxTotal - minTotal  <=>  maxTotal - minTotal - spread = 0
        model.scalar(arrayOf(maxTotal, minTotal, spread), intArrayOf(1, -1, -1), "=", 0).post()
        penaltyTerms += spread

        // Sum all penalty terms into the objective.
        val totalPenalty = model.intVar(
            "objective",
            0,
            // Safe upper bound: sum of all pref weights + max fairness spread
            instance.preferences.sumOf { it.weight } + nD,
        )
        if (penaltyTerms.isEmpty()) {
            model.arithm(totalPenalty, "=", 0).post()
        } else {
            model.sum(penaltyTerms.toTypedArray(), "=", totalPenalty).post()
        }
        return totalPenalty
    }
}
