package io.vanja.cpsat.nsp

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * NSP domain model used by chapters 11-13 and the Ktor backend.
 *
 * These types are the canonical in-memory representation — they are not the
 * wire schema by themselves. Both the teaching `data/nsp/schema.json` and the
 * app's `apps/shared/schemas/nsp-instance.schema.json` round-trip through this
 * shape via [io.vanja.cpsat.nsp.io.InstanceIo].
 *
 * Design choices:
 *
 * - Everything is immutable (`data class` with `val`). Instances flow across
 *   coroutines during SSE streaming; immutability keeps that race-free.
 * - `shiftId = null` in an [Assignment] means "day off" — see the wire schema.
 * - We keep shift duration in minutes (precise, integer), not hours (too
 *   coarse for anything that isn't a whole multiple of an hour).
 */

/** One shift type (e.g. "Morning 07:00-15:00"). */
@Serializable
public data class Shift(
    /** Stable id used on the wire ("M", "D", "N"). */
    val id: String,
    /** Human-readable name ("Morning"). */
    val label: String,
    /** Local-time start minute `[0, 1439]`. */
    val startMinutes: Int,
    /** Shift duration in minutes (must be >= 1). */
    val durationMinutes: Int,
    /** Optional skill requirement — only nurses holding this skill may work this shift. */
    val skill: String? = null,
) {
    /** End minute in the same day frame; may wrap past midnight. */
    val endMinutes: Int get() = startMinutes + durationMinutes

    /** Duration in hours, rounded to nearest integer (for reports). */
    val durationHours: Int get() = (durationMinutes + 30) / 60

    /** True if this shift ends on the day *after* it starts (overnight). */
    val overnight: Boolean get() = endMinutes >= 24 * 60

    /** Scaled duration for objective math. CP-SAT is integer-only; minutes give enough precision. */
    val durationMinutesLong: Long get() = durationMinutes.toLong()
}

/** One schedulable nurse with skills and contract. */
@Serializable
public data class Nurse(
    val id: String,
    val name: String = id,
    /** Skill tags the nurse holds (e.g. {"general", "icu"}). */
    val skills: Set<String> = emptySet(),
    /** Weekly contract hours (HC-8). Defaults to 40 if unknown. */
    val contractHoursPerWeek: Int = 40,
    /** Optional per-nurse override of the instance's `maxConsecutiveWorkingDays`. */
    val maxConsecutiveWorkingDays: Int? = null,
    /** Optional absolute max shifts over the horizon. */
    val maxShiftsPerHorizon: Int? = null,
    /** Optional absolute min shifts over the horizon. */
    val minShiftsPerHorizon: Int? = null,
    /** Days on which the nurse is unavailable (hard day off). */
    val unavailableDays: Set<Int> = emptySet(),
)

/** Coverage requirement for one `(day, shiftId)` cell. */
@Serializable
public data class CoverageRequirement(
    val day: Int,
    val shiftId: String,
    /** Minimum nurses on the cell (HC-1 lower bound). */
    val min: Int,
    /** Maximum nurses on the cell (HC-1 upper bound). */
    val max: Int,
    /** Skills that must collectively be present (HC-7). */
    val requiredSkills: Set<String> = emptySet(),
) {
    init {
        require(day >= 0) { "day must be >= 0" }
        require(min >= 0) { "min must be >= 0" }
        require(max >= min) { "max ($max) must be >= min ($min) on day=$day shift=$shiftId" }
    }
}

/** Soft preference: positive weight = desired, negative = avoided. */
@Serializable
public data class Preference(
    val nurseId: String,
    val day: Int,
    /** null = preference about a day-off (no shift). */
    val shiftId: String? = null,
    /** Positive: nurse wants it. Negative: nurse wants to avoid it. */
    val weight: Int,
)

/** Hard pre-assignment: nurse *must* be off on this day. */
@Serializable
public data class FixedOff(
    val nurseId: String,
    val day: Int,
)

/** Soft objective weights for chapter 12. */
@Serializable
public data class ObjectiveWeights(
    /** SC-1 preference honoring. */
    @SerialName("SC1") val sc1: Int = 10,
    /** SC-2 fairness across nurses (shift-count spread). */
    @SerialName("SC2") val sc2: Int = 5,
    /** SC-3 workload balance (hours spread). */
    @SerialName("SC3") val sc3: Int = 2,
    /** SC-4 weekend distribution. */
    @SerialName("SC4") val sc4: Int = 3,
    /** SC-5 isolated days-off. */
    @SerialName("SC5") val sc5: Int = 1,
) {
    init {
        listOf("SC1" to sc1, "SC2" to sc2, "SC3" to sc3, "SC4" to sc4, "SC5" to sc5).forEach { (k, v) ->
            require(v in 0..1000) { "weight $k must be in [0, 1000], was $v" }
        }
    }

    public companion object {
        public val DEFAULT: ObjectiveWeights = ObjectiveWeights()
        public val ZERO: ObjectiveWeights = ObjectiveWeights(0, 0, 0, 0, 0)
    }
}

/** Full NSP instance. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
public data class Instance(
    val id: String,
    val name: String = id,
    @EncodeDefault val source: String = "custom",
    val horizonDays: Int,
    val shifts: List<Shift>,
    val nurses: List<Nurse>,
    val coverage: List<CoverageRequirement>,
    /** Pairs `[a, b]` meaning "shift a on day d, shift b on day d+1" is banned (HC-3). */
    val forbiddenTransitions: List<Pair<String, String>> = emptyList(),
    /** Hours of minimum rest between shift endings. Used to auto-derive more forbidden transitions. */
    val minRestHours: Int = 11,
    /** Global max consecutive working days (HC-4). */
    val maxConsecutiveWorkingDays: Int = 5,
    /** Max consecutive night shifts (HC-5). Default per spec: 3. */
    val maxConsecutiveNights: Int = 3,
    /** Soft preferences (SC-1). */
    val preferences: List<Preference> = emptyList(),
    /** Hard day-off locks. */
    val fixedOff: List<FixedOff> = emptyList(),
    /** Hours tolerance on HC-8 contract hours (± this value). */
    val contractTolerance: Int = 4,
    /** Weekday indices counted as weekends (default Saturday=5, Sunday=6). */
    val weekendDays: Set<Int> = setOf(5, 6),
) {
    init {
        require(horizonDays >= 1) { "horizonDays must be >= 1, was $horizonDays" }
        require(shifts.isNotEmpty()) { "instance must define at least one shift" }
        require(nurses.isNotEmpty()) { "instance must define at least one nurse" }
        require(shifts.map { it.id }.toSet().size == shifts.size) {
            "shift ids must be unique"
        }
        require(nurses.map { it.id }.toSet().size == nurses.size) {
            "nurse ids must be unique"
        }
    }

    /** Map `shiftId -> Shift`. */
    public val shiftById: Map<String, Shift> by lazy { shifts.associateBy { it.id } }

    /** Map `nurseId -> Nurse`. */
    public val nurseById: Map<String, Nurse> by lazy { nurses.associateBy { it.id } }

    /** Coverage requirements indexed by `(day, shiftId)` for fast lookup. */
    public val coverageBy: Map<Pair<Int, String>, CoverageRequirement> by lazy {
        coverage.associateBy { it.day to it.shiftId }
    }

    /** True if [day] is a weekend under this instance's calendar. */
    public fun isWeekend(day: Int): Boolean = (day % 7) in weekendDays

    /** True if [shiftId] is classified as a night shift (crosses midnight or has "night" in label). */
    public fun isNightShift(shiftId: String): Boolean {
        val s = shiftById[shiftId] ?: return false
        // Prefer label heuristic ("night"), then overnight check.
        return s.label.lowercase().contains("night") || s.overnight
    }

    /** All night shift ids for HC-5. */
    public val nightShiftIds: Set<String> by lazy {
        shifts.filter { isNightShift(it.id) }.map { it.id }.toSet()
    }
}

/**
 * A single assignment cell. `shiftId = null` = day off.
 */
@Serializable
public data class Assignment(
    val nurseId: String,
    val day: Int,
    val shiftId: String? = null,
)

/** One constraint violation — hard (infeasibility witness) or soft (penalty). */
@Serializable
public data class Violation(
    /** Stable code, `HC-N` or `SC-N`. */
    val code: String,
    /** Plain-English description. */
    val message: String,
    val severity: Severity,
    val nurseId: String? = null,
    val day: Int? = null,
    val penalty: Double? = null,
)

@Serializable
public enum class Severity {
    @SerialName("hard") HARD,

    @SerialName("soft") SOFT,
}

/** Produced schedule. */
@Serializable
public data class Schedule(
    val instanceId: String,
    val assignments: List<Assignment>,
    val violations: List<Violation> = emptyList(),
    val generatedAt: String? = null,
)

/** Parameters fed to the solver. */
public data class SolveParams(
    val maxTimeSeconds: Double = 30.0,
    val numSearchWorkers: Int = 8,
    val randomSeed: Int? = 1,
    val logSearchProgress: Boolean = false,
    val linearizationLevel: Int? = null,
    val objectiveWeights: ObjectiveWeights = ObjectiveWeights.DEFAULT,
    /** When true, use lexicographic objective (chapter 12 variant). */
    val lexicographic: Boolean = false,
)

/**
 * Outcome of a solve. Mirrors `io.vanja.cpsat.SolveResult` but richer —
 * includes the produced [Schedule] and timing.
 */
public sealed interface SolveResult {
    public val solveTimeSeconds: Double
    public val status: Status

    public data class Optimal(
        val schedule: Schedule,
        val objective: Long,
        override val solveTimeSeconds: Double,
    ) : SolveResult {
        override val status: Status get() = Status.OPTIMAL
    }

    public data class Feasible(
        val schedule: Schedule,
        val objective: Long,
        val bestBound: Long,
        val gap: Double,
        override val solveTimeSeconds: Double,
    ) : SolveResult {
        override val status: Status get() = Status.FEASIBLE
    }

    public data class Infeasible(
        val violations: List<Violation>,
        override val solveTimeSeconds: Double,
    ) : SolveResult {
        override val status: Status get() = Status.INFEASIBLE
    }

    public data class Unknown(override val solveTimeSeconds: Double) : SolveResult {
        override val status: Status get() = Status.UNKNOWN
    }

    public data class ModelInvalid(val message: String, override val solveTimeSeconds: Double) : SolveResult {
        override val status: Status get() = Status.MODEL_INVALID
    }

    public enum class Status {
        OPTIMAL,
        FEASIBLE,
        INFEASIBLE,
        UNKNOWN,
        MODEL_INVALID,
    }
}

/** Extract the best [Schedule] from any [SolveResult], or null. */
public fun SolveResult.scheduleOrNull(): Schedule? = when (this) {
    is SolveResult.Optimal -> schedule
    is SolveResult.Feasible -> schedule
    else -> null
}
