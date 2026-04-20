package io.vanja.altsolver.choco

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Duplicate of the Timefold module's instance-reader. Kept local to avoid
 * cross-module build coupling (see CLAUDE.md), at the cost of keeping the
 * two copies in sync by hand.
 */
@Serializable
internal data class InstanceJson(
    val id: String? = null,
    val name: String? = null,
    val horizonDays: Int,
    val shifts: List<ShiftJson>,
    val nurses: List<NurseJson>,
    val coverage: List<CoverageEntry>,
    val preferences: List<PreferenceJson> = emptyList(),
    val forbiddenTransitions: List<List<String>> = emptyList(),
    val minRestHours: Int = 11,
    val maxConsecutiveWorkingDays: Int = 6,
    val maxConsecutiveNights: Int = 3,
)

@Serializable
internal data class ShiftJson(
    val id: String,
    val label: String,
    val start: String? = null,
    val end: String? = null,
    val startMinutes: Int? = null,
    val durationMinutes: Int? = null,
    val isNight: Boolean? = null,
)

@Serializable
internal data class NurseJson(
    val id: String,
    val name: String? = null,
    val skills: List<String> = emptyList(),
    val contractHoursPerWeek: Int = 36,
    val unavailable: List<Int> = emptyList(),
)

@Serializable
internal data class CoverageEntry(
    val day: Int,
    val shiftId: String,
    val min: Int = 1,
    val max: Int = 1,
    val requiredSkills: List<String> = emptyList(),
)

@Serializable
internal data class PreferenceJson(
    val nurseId: String,
    val day: Int,
    val shiftId: String? = null,
    /** `"prefer"` or `"avoid"`. */
    val kind: String,
    val weight: Int,
)

internal val NSP_JSON = Json {
    ignoreUnknownKeys = true
    isLenient = false
}

internal fun loadInstance(path: Path): InstanceJson =
    NSP_JSON.decodeFromString(InstanceJson.serializer(), path.readText())

/** Canonicalise a shift's night-ness when the JSON doesn't say so explicitly. */
internal fun classifyAsNight(id: String, label: String, startMinutes: Int?): Boolean {
    val nameHit = id.equals("N", ignoreCase = true) ||
        label.contains("night", ignoreCase = true)
    val timeHit = startMinutes != null && (startMinutes >= 22 * 60 || startMinutes < 6 * 60)
    return nameHit || timeHit
}
