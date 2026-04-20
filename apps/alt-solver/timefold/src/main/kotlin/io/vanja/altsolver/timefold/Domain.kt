package io.vanja.altsolver.timefold

import ai.timefold.solver.core.api.domain.entity.PlanningEntity
import ai.timefold.solver.core.api.domain.lookup.PlanningId
import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty
import ai.timefold.solver.core.api.domain.solution.PlanningScore
import ai.timefold.solver.core.api.domain.solution.PlanningSolution
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider
import ai.timefold.solver.core.api.domain.variable.PlanningVariable
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore

/**
 * Problem fact — nurses are fixed throughout a solve; the solver only picks
 * which [ShiftAssignment] each nurse gets.
 */
data class Nurse(
    @PlanningId val id: String,
    val name: String,
    val skills: Set<String>,
    val contractHoursPerWeek: Int,
    val fixedOffDays: Set<Int>,
) {
    val hasNightSkill: Boolean get() = true // every nurse can work nights in the v1 toy; override in skill-aware runs
}

/**
 * Problem fact — a (calendar) day. Exposed so constraint streams can look up
 * the weekday via `day.index % 7`.
 */
data class Day(@PlanningId val index: Int)

/**
 * Problem fact — a named shift with duration metadata precomputed for
 * incremental score calculations (e.g. hours-per-week totals when extended).
 */
data class Shift(
    @PlanningId val id: String,
    val label: String,
    val startMinutes: Int,
    val durationMinutes: Int,
    val isNight: Boolean,
) {
    companion object {
        /** Heuristic used by the v1 port: anything labelled "night" or
         *  running between 22:00 and 06:00 is classified as a night shift. */
        fun classifyAsNight(id: String, label: String, startMinutes: Int): Boolean {
            val nameHit = id.equals("N", ignoreCase = true) ||
                label.contains("night", ignoreCase = true)
            val timeHit = startMinutes >= 22 * 60 || startMinutes < 6 * 60
            return nameHit || timeHit
        }
    }
}

/**
 * Planning entity — one nurse assignment for a `(day, shift)` slot that must
 * be covered. For a slot with demand >= 2, multiple [ShiftAssignment] entities
 * share the same `day` and `shift`.
 *
 * The nurse field is the only planning variable. Timefold's default
 * construction heuristic (FIRST_FIT) picks a nurse for each slot, then local
 * search (late acceptance / tabu) tries swaps to drive the hard score to 0
 * and minimize the soft score.
 */
@PlanningEntity
class ShiftAssignment(
    @PlanningId var id: String,
    var day: Day,
    var shift: Shift,
) {
    @PlanningVariable(valueRangeProviderRefs = ["nurseRange"])
    var nurse: Nurse? = null

    /** Required no-arg constructor for Timefold's reflective instantiation. */
    @Suppress("unused")
    constructor() : this("", Day(0), Shift("", "", 0, 0, false))

    val dayIndex: Int get() = day.index
    val shiftId: String get() = shift.id

    override fun toString(): String = "ShiftAssignment($id: d=${day.index}, s=${shift.id}, n=${nurse?.id ?: "?"})"
}

/**
 * Planning solution — the whole problem, including facts and entities, plus
 * the slot to hold the current score. Timefold mutates the `nurse` fields on
 * the entities inside this solution as it solves.
 */
@PlanningSolution
class NspSolution {
    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "nurseRange")
    lateinit var nurses: List<Nurse>

    @ProblemFactCollectionProperty
    lateinit var days: List<Day>

    @ProblemFactCollectionProperty
    lateinit var shifts: List<Shift>

    @PlanningEntityCollectionProperty
    lateinit var assignments: List<ShiftAssignment>

    /** Configuration carried with the solution so constraint streams can reach it. */
    lateinit var config: NspConfig

    @PlanningScore
    var score: HardSoftScore? = null

    @Suppress("unused")
    constructor()

    constructor(
        nurses: List<Nurse>,
        days: List<Day>,
        shifts: List<Shift>,
        assignments: List<ShiftAssignment>,
        config: NspConfig,
    ) {
        this.nurses = nurses
        this.days = days
        this.shifts = shifts
        this.assignments = assignments
        this.config = config
    }
}

/** Parsed instance configuration carried inside [NspSolution.config]. */
data class NspConfig(
    val horizonDays: Int,
    val maxConsecutiveWorkingDays: Int,
    /** Ordered pairs `(s1, s2)`: working `s1` on day `d` forbids `s2` on `d+1`. */
    val forbiddenTransitions: Set<Pair<String, String>>,
    /** `nurseId -> day indices preferred off with weight > 0` (SC-1 day-off preference). */
    val preferDaysOff: Map<String, Map<Int, Int>>,
    /** `nurseId -> (day, shift) preferred with weight > 0` (SC-1 preferred shift). */
    val preferShift: Map<String, Map<Pair<Int, String>, Int>>,
    /** `nurseId -> (day, shift) avoided with weight < 0`. */
    val avoidShift: Map<String, Map<Pair<Int, String>, Int>>,
    /** Max consecutive nights allowed (HC-5). Fixed at 3 per spec. */
    val maxConsecutiveNights: Int = 3,
)
