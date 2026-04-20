package io.vanja.nspapi

import io.vanja.cpsat.nsp.Instance
import io.vanja.cpsat.nsp.InstanceIo
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement

/**
 * Parse an [Instance] from a [JsonElement] and run semantic validation that
 * goes beyond the structural checks in `Instance.init` — these are the sort
 * of mistakes a schema validator can't catch (e.g. "coverage references an
 * unknown shiftId").
 *
 * Throws:
 * - [IllegalArgumentException] for malformed JSON (→ 400 at the caller).
 * - [InstanceValidationException] for semantic failures (→ 422 at the caller).
 */
public fun parseAndValidateInstance(payload: JsonElement): Instance {
    val instance = try {
        InstanceIo.fromJson(payload.toString())
    } catch (e: SerializationException) {
        throw IllegalArgumentException("instance JSON is malformed: ${e.message}", e)
    } catch (e: IllegalArgumentException) {
        // nsp-core's Instance.init uses `require` — convert to semantic error
        // because the JSON parsed but the domain rejected it.
        throw InstanceValidationException(listOf(e.message ?: "invalid instance"))
    }
    val errors = validate(instance)
    if (errors.isNotEmpty()) throw InstanceValidationException(errors)
    return instance
}

/**
 * Return a list of human-readable validation errors for [instance]. Empty list
 * means everything looks good.
 *
 * We cover the constraints that are easy to state but that `Instance.init`
 * doesn't enforce (because they cross-reference lists that are all `val`s):
 *
 * - every coverage day is in `0 until horizonDays`.
 * - every coverage shiftId maps to a real shift.
 * - every preference shiftId (if non-null) maps to a real shift.
 * - every preference nurseId maps to a real nurse.
 * - every fixedOff nurseId maps to a real nurse.
 * - every unavailable day is in range.
 * - skill references on coverage map to something a nurse holds (otherwise HC-1
 *   would be guaranteed infeasible).
 */
public fun validate(instance: Instance): List<String> {
    val errors = mutableListOf<String>()
    val shiftIds = instance.shifts.map { it.id }.toSet()
    val nurseIds = instance.nurses.map { it.id }.toSet()
    val allSkills = instance.nurses.flatMap { it.skills }.toSet() +
        instance.shifts.mapNotNull { it.skill }.toSet()

    for (c in instance.coverage) {
        if (c.day !in 0 until instance.horizonDays) {
            errors += "coverage.day=${c.day} is out of range [0, ${instance.horizonDays})"
        }
        if (c.shiftId !in shiftIds) {
            errors += "coverage references unknown shiftId='${c.shiftId}'"
        }
        for (skill in c.requiredSkills) {
            if (skill !in allSkills) {
                errors += "coverage.requiredSkills contains unknown skill='$skill' on day=${c.day}"
            }
        }
    }

    for (p in instance.preferences) {
        if (p.nurseId !in nurseIds) {
            errors += "preference references unknown nurseId='${p.nurseId}'"
        }
        if (p.day !in 0 until instance.horizonDays) {
            errors += "preference.day=${p.day} is out of range for nurse='${p.nurseId}'"
        }
        if (p.shiftId != null && p.shiftId !in shiftIds) {
            errors += "preference references unknown shiftId='${p.shiftId}'"
        }
    }

    for (f in instance.fixedOff) {
        if (f.nurseId !in nurseIds) {
            errors += "fixedOff references unknown nurseId='${f.nurseId}'"
        }
        if (f.day !in 0 until instance.horizonDays) {
            errors += "fixedOff.day=${f.day} is out of range for nurse='${f.nurseId}'"
        }
    }

    for (n in instance.nurses) {
        for (d in n.unavailableDays) {
            if (d !in 0 until instance.horizonDays) {
                errors += "nurse='${n.id}' unavailableDays contains out-of-range day=$d"
            }
        }
    }

    for ((a, b) in instance.forbiddenTransitions) {
        if (a !in shiftIds) errors += "forbiddenTransitions references unknown shiftId='$a'"
        if (b !in shiftIds) errors += "forbiddenTransitions references unknown shiftId='$b'"
    }

    return errors
}
