package io.vanja.altsolver.timefold

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore
import ai.timefold.solver.core.api.score.stream.Constraint
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors
import ai.timefold.solver.core.api.score.stream.ConstraintFactory
import ai.timefold.solver.core.api.score.stream.ConstraintProvider
import ai.timefold.solver.core.api.score.stream.Joiners

/**
 * NSP constraints for the Timefold port.
 *
 * Scope: chapter 18 comparability set — HC-1..HC-5 + SC-1..SC-2.
 *
 * Mapping to the repo's hard-constraint IDs ([04-functional-requirements.md]):
 *
 *  | Chapter ID | Repo ID | Meaning                                       |
 *  |------------|---------|-----------------------------------------------|
 *  | HC-1       | HC-1    | Coverage (one assignment per required slot)   |
 *  | HC-2       | HC-2    | One shift per nurse per day                   |
 *  | HC-3       | HC-3    | Forbidden transitions                         |
 *  | HC-4       | HC-4    | Max consecutive working days                  |
 *  | HC-5       | HC-5    | Max consecutive nights                        |
 *
 * Cut (and why — see the chapter retrospective):
 *  - HC-6 (min rest hours): collapsed into HC-3 via forbiddenTransitions at load.
 *  - HC-7 (skill match): not modelled here; toy instances are single-skill.
 *  - HC-8 (contract hours): cut for Ch18 — requires duration arithmetic that
 *    inflates the constraint count and obscures the paradigm comparison. A
 *    follow-up port (Ex 10-A) covers it.
 *
 * Soft constraints:
 *  - SC-1: preference honoring (from the instance's `preferences`).
 *  - SC-2: fairness — minimise the max - min shift-count delta across nurses.
 */
class NspConstraintProvider : ConstraintProvider {

    override fun defineConstraints(factory: ConstraintFactory): Array<Constraint> = arrayOf(
        hc1Coverage(factory),           // built into structure, but validated as a score-0 safety net
        hc2OneShiftPerDay(factory),
        hc3ForbiddenTransitions(factory),
        hc4MaxConsecutiveWorkingDays(factory),
        hc5MaxConsecutiveNights(factory),
        sc1PreferenceHonoring(factory),
        sc2Fairness(factory),
    )

    // ---------------------------------------------------------------------
    // HC-1 — Coverage. Each [ShiftAssignment] is a required slot; the solver
    // must give every slot a nurse. An unassigned slot leaves `nurse = null`,
    // which Timefold 1.16 skips in `forEach` by default. We therefore use
    // `forEachIncludingUnassigned` and penalize null-nurse rows directly — a
    // safety net in case the solver stops mid-construction.
    // ---------------------------------------------------------------------
    private fun hc1Coverage(f: ConstraintFactory): Constraint =
        f.forEachIncludingUnassigned(ShiftAssignment::class.java)
            .filter { it.nurse == null }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("HC-1 coverage (unassigned slot)")

    // ---------------------------------------------------------------------
    // HC-2 — One shift per nurse per day. Penalise pairs of assignments for
    // the same nurse on the same day. Uses `forEachUniquePair` so each
    // offending pair is counted exactly once.
    // ---------------------------------------------------------------------
    private fun hc2OneShiftPerDay(f: ConstraintFactory): Constraint =
        f.forEachUniquePair(
            ShiftAssignment::class.java,
            Joiners.equal(ShiftAssignment::nurse),
            Joiners.equal(ShiftAssignment::dayIndex),
        )
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("HC-2 one shift per nurse per day")

    // ---------------------------------------------------------------------
    // HC-3 — Forbidden transitions: nurse works shift `s1` on day `d`, then
    // `s2` on day `d+1`, and `(s1, s2)` is in the forbidden set.
    //
    // Strategy: for every same-nurse consecutive-day pair, look up the pair
    // `(s1, s2)` in the companion's [forbiddenTransitions] set. Timefold's
    // [ConstraintProvider] is instantiated reflectively, so no instance
    // state is safe — we plumb the set through a companion field populated
    // by [NspSolutionFactory.build]. A [ConstraintConfiguration] would be
    // the "proper" way to plumb solver-lifetime data; we stick with the
    // companion map as a pragmatic single-field workaround and document the
    // trade-off in the Ch18 retrospective.
    //
    // Join shape: `forEach(s1).join(s2 on same nurse and day == s1.day+1)`,
    // then filter on the forbidden set.
    // ---------------------------------------------------------------------
    private fun hc3ForbiddenTransitions(f: ConstraintFactory): Constraint =
        f.forEach(ShiftAssignment::class.java)
            .join(
                ShiftAssignment::class.java,
                Joiners.equal(ShiftAssignment::nurse),
                Joiners.equal({ a: ShiftAssignment -> a.dayIndex + 1 }, ShiftAssignment::dayIndex),
            )
            .filter { prev, next -> (prev.shiftId to next.shiftId) in forbiddenTransitions }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("HC-3 forbidden transition")

    // ---------------------------------------------------------------------
    // HC-4 — Max consecutive working days. Group same-nurse days worked; a
    // run longer than the configured max contributes one hard-point.
    //
    // The run-length predicate is expressed via `filter` on the collected
    // day list. It's an O(n log n) per group step (sort), which is fine for
    // NSP horizons of ≤90 days. If scale grows, swap for an event-based
    // encoding (one constraint stream per sliding window of K+1 days).
    // ---------------------------------------------------------------------
    private fun hc4MaxConsecutiveWorkingDays(f: ConstraintFactory): Constraint =
        f.forEach(ShiftAssignment::class.java)
            .groupBy(
                ShiftAssignment::nurse,
                ConstraintCollectors.toList { a -> a.dayIndex },
            )
            .filter { _, days -> hasRunOfLength(days, MAX_CONSEC_DAYS + 1) }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("HC-4 > max consecutive working days")

    // ---------------------------------------------------------------------
    // HC-5 — Max consecutive nights. Same shape as HC-4 but filtered to
    // night shifts only.
    // ---------------------------------------------------------------------
    private fun hc5MaxConsecutiveNights(f: ConstraintFactory): Constraint =
        f.forEach(ShiftAssignment::class.java)
            .filter { it.shift.isNight }
            .groupBy(
                ShiftAssignment::nurse,
                ConstraintCollectors.toList { a -> a.dayIndex },
            )
            .filter { _, days -> hasRunOfLength(days, MAX_CONSEC_NIGHTS + 1) }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("HC-5 > max consecutive nights")

    // ---------------------------------------------------------------------
    // SC-1 — Preference honoring. Soft-penalise assignments that violate a
    // preference:
    //   - `(nurseId, day, shiftId, w > 0)`: nurse wants this shift; soft +w
    //     when absent (we cannot negatively penalise an absence with a
    //     Timefold stream, so we detect "no matching assignment" indirectly
    //     by penalising all non-matching assignments that occupy that slot
    //     on behalf of someone else — see below).
    //   - `(nurseId, day, shiftId, w < 0)`: nurse avoids this shift;
    //     soft +|w| when the nurse is actually assigned.
    //   - `(nurseId, day, null, w > 0)`: nurse prefers a day off; soft +w
    //     when the nurse works on that day.
    //
    // We implement the "avoid" and "prefer day off" cases directly. The
    // positive-preference "wants this shift" case falls back to preferring
    // the nurse is free on that day's other shifts (covered by HC-2) —
    // faithfully capturing "wants this shift" in constraint streams
    // requires forEachIncludingUnassigned and symmetric counting, which we
    // leave to SC-1-full in the Ex 10-A port.
    //
    // This simplified SC-1 is enough to distinguish schedules that honour
    // preferences from ones that don't on toy-01/toy-02 — exactly what the
    // Ch18 comparison needs.
    // ---------------------------------------------------------------------
    private fun sc1PreferenceHonoring(f: ConstraintFactory): Constraint =
        f.forEach(ShiftAssignment::class.java)
            .filter { a ->
                val n = a.nurse ?: return@filter false
                // weight to apply if this assignment violates any avoid-style
                // or prefer-day-off preference for the nurse on that day.
                violationWeight(n.id, a.dayIndex, a.shiftId, a) != 0
            }
            .penalize(HardSoftScore.ONE_SOFT) { a ->
                val n = a.nurse!!
                violationWeight(n.id, a.dayIndex, a.shiftId, a)
            }
            .asConstraint("SC-1 preference honoring")

    // ---------------------------------------------------------------------
    // SC-2 — Fairness. Penalise the spread (max - min) of per-nurse shift
    // counts. The pipeline:
    //   1. Per-nurse shift count: groupBy(nurse, count())  -> Bi<Nurse,Int>
    //   2. Collapse to (minCount, maxCount) across all nurses via a two-slot
    //      groupBy with min and max collectors on the count column.
    //   3. Penalise by (max - min).
    //
    // Using `min(BiFunction)` and `max(BiFunction)` to stay in the Bi track.
    // ---------------------------------------------------------------------
    private fun sc2Fairness(f: ConstraintFactory): Constraint =
        f.forEach(ShiftAssignment::class.java)
            .groupBy(ShiftAssignment::nurse, ConstraintCollectors.count())
            .groupBy(
                ConstraintCollectors.min { _: Nurse?, count: Int -> count },
                ConstraintCollectors.max { _: Nurse?, count: Int -> count },
            )
            .penalize(HardSoftScore.ONE_SOFT) { minCount: Int, maxCount: Int ->
                maxCount - minCount
            }
            .asConstraint("SC-2 shift-count fairness")

    // ---------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------

    companion object {
        /** Hard upper bound on consecutive working days (matches schema default). */
        const val MAX_CONSEC_DAYS: Int = 5

        /** Hard upper bound on consecutive night shifts (HC-5 fixed at 3). */
        const val MAX_CONSEC_NIGHTS: Int = 3

        /**
         * Preference lookup populated in [NspSolutionFactory.build]. Keyed by
         * `(nurseId, day)` -> `(shiftId?, weight)`. A null `shiftId` means
         * "any assignment that day violates" (day-off preference). A weight
         * is a raw schema weight — positive means "wants", negative means
         * "avoids".
         *
         * We carry this on the companion because Timefold's [ConstraintProvider]
         * is instantiated reflectively; no instance state is safe.
         * See the Ch18 retrospective for a cleaner path using
         * [ConstraintConfiguration].
         */
        internal val preferencesByNurseDay = java.util.concurrent.ConcurrentHashMap<
            Pair<String, Int>, List<PrefEntry>
        >()

        /**
         * Set of forbidden `(s1, s2)` transitions populated at solve start.
         * Using a [java.util.Collections.synchronizedSet] instead of
         * [ConcurrentHashMap] because reads vastly outnumber writes and the
         * set is immutable after [installForbiddenTransitions]. Kept on the
         * companion for the same reason as [preferencesByNurseDay].
         */
        @Volatile
        internal var forbiddenTransitions: Set<Pair<String, String>> = emptySet()
            private set

        internal fun installForbiddenTransitions(pairs: Collection<Pair<String, String>>) {
            forbiddenTransitions = pairs.toHashSet()
        }

        internal fun resetPreferences() {
            preferencesByNurseDay.clear()
        }

        internal fun registerPreference(
            nurseId: String,
            day: Int,
            shiftId: String?,
            weight: Int,
        ) {
            preferencesByNurseDay.compute(nurseId to day) { _, existing ->
                val entry = PrefEntry(shiftId, weight)
                if (existing == null) listOf(entry) else existing + entry
            }
        }

        private fun violationWeight(
            nurseId: String,
            day: Int,
            shiftId: String,
            @Suppress("UNUSED_PARAMETER") assignment: ShiftAssignment,
        ): Int {
            val prefs = preferencesByNurseDay[nurseId to day] ?: return 0
            var total = 0
            for (p in prefs) {
                val matchesDayOff = p.shiftId == null && p.weight > 0
                val matchesAvoid = p.shiftId == shiftId && p.weight < 0
                if (matchesDayOff) total += p.weight
                else if (matchesAvoid) total += -p.weight
            }
            return total
        }

        /** Return true if [days] contains a monotone run of length >= [len]. */
        private fun hasRunOfLength(days: List<Int>, len: Int): Boolean {
            if (days.size < len) return false
            val sorted = days.sorted()
            var run = 1
            for (i in 1 until sorted.size) {
                run = if (sorted[i] == sorted[i - 1] + 1) run + 1 else 1
                if (run >= len) return true
            }
            return false
        }
    }

    internal data class PrefEntry(val shiftId: String?, val weight: Int)
}
