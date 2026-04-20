package io.vanja.cpsat.nsp

import java.nio.file.Path
import kotlin.io.path.readText
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * JSON I/O for [Instance] and [Schedule].
 *
 * Two schemas are accepted on input:
 *
 * 1. **Toy schema** — `data/nsp/schema.json`. Used by teaching code and the
 *    bundled `toy-*.json` instances. Shifts are `start`/`end` as HH:MM,
 *    demand is `min`/`max`, nurses list `contractHoursPerWeek`, preferences
 *    live at the top level, forbiddenTransitions are `[[a,b], ...]`.
 *
 * 2. **Wire schema** — `apps/shared/schemas/nsp-instance.schema.json`. Used by
 *    the Ktor backend. Shifts are `startMinutes`/`durationMinutes`, demand is
 *    `required` (single value), preferences are nested inside each nurse,
 *    and the instance has an `id`.
 *
 * Output is always the canonical in-memory shape serialized via the wire
 * schema (primarily to avoid the toy schema's lossy start/end ambiguity).
 */
public object InstanceIo {
    /** Lenient Json for reading both schemas; strict output for the wire. */
    public val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** Parse an [Instance] from a JSON string (auto-detects toy vs. wire). */
    public fun fromJson(raw: String): Instance {
        val root = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (e: SerializationException) {
            throw IllegalArgumentException("Instance JSON is not a valid object: ${e.message}", e)
        }
        return if (root.isToySchema()) parseToy(root) else parseWire(root)
    }

    /** Load an [Instance] from a file path. */
    public fun load(path: Path): Instance = fromJson(path.readText())

    /** Serialize an [Instance] to canonical wire-schema JSON. */
    public fun toJson(instance: Instance): String = json.encodeToString(instance.toWireJson())

    /** Serialize a [Schedule] to canonical JSON. */
    public fun toJson(schedule: Schedule): String = json.encodeToString(schedule)

    /** Parse a [Schedule] from JSON. */
    public fun scheduleFromJson(raw: String): Schedule = json.decodeFromString(raw)

    // -------------------------------------------------------------------------
    // Schema detection
    // -------------------------------------------------------------------------

    private fun JsonObject.isToySchema(): Boolean {
        // The wire schema is the canonical in-memory form, so prefer it when
        // the tell-tale `coverage` key is present. Only fall back to the toy
        // parser when the JSON explicitly uses `demand` (the toy marker).
        //
        // The bundled `data/nsp/toy-*.json` files carry both `coverage` and a
        // toy-style `start`/`end` on shifts — we treat those as wire because
        // they also include `startMinutes`/`durationMinutes`.
        if (containsKey("coverage")) return false
        if (containsKey("demand")) return true
        val firstShift = (this["shifts"] as? kotlinx.serialization.json.JsonArray)?.firstOrNull() as? JsonObject
        if (firstShift != null && !firstShift.containsKey("startMinutes") && firstShift.containsKey("start")) {
            return true
        }
        return false
    }

    // -------------------------------------------------------------------------
    // Toy-schema parser — data/nsp/schema.json
    // -------------------------------------------------------------------------

    private fun parseToy(root: JsonObject): Instance {
        val horizonDays = root["horizonDays"]?.jsonPrimitive?.content?.toInt()
            ?: error("toy instance missing horizonDays")
        val shifts = (root["shifts"] as? kotlinx.serialization.json.JsonArray ?: error("toy instance missing shifts"))
            .map { (it as JsonObject).toToyShift() }
        val nurses = (root["nurses"] as? kotlinx.serialization.json.JsonArray ?: error("toy instance missing nurses"))
            .map { (it as JsonObject).toToyNurse() }
        val coverage = (root["demand"] as? kotlinx.serialization.json.JsonArray ?: error("toy instance missing demand"))
            .map { it.jsonObject.toToyCoverage() }
        val forbidden = root["forbiddenTransitions"]?.jsonArray?.map {
            val arr = it.jsonArray
            arr[0].jsonPrimitive.content to arr[1].jsonPrimitive.content
        } ?: emptyList()
        val preferences = root["preferences"]?.jsonArray?.map {
            val o = it.jsonObject
            val shiftPrim = o["shiftId"]
            val shiftId: String? = when (shiftPrim) {
                null, JsonNull -> null
                is JsonPrimitive -> if (shiftPrim.content == "null") null else shiftPrim.content
                else -> null
            }
            Preference(
                nurseId = o["nurseId"]!!.jsonPrimitive.content,
                day = o["day"]!!.jsonPrimitive.content.toInt(),
                shiftId = shiftId,
                weight = o["weight"]!!.jsonPrimitive.content.toInt(),
            )
        } ?: emptyList()
        val fixedOff = root["fixedOff"]?.jsonArray?.map {
            val o = it.jsonObject
            FixedOff(
                nurseId = o["nurseId"]!!.jsonPrimitive.content,
                day = o["day"]!!.jsonPrimitive.content.toInt(),
            )
        } ?: emptyList()
        val minRestHours = root["minRestHours"]?.jsonPrimitive?.content?.toInt() ?: 11
        val maxConsec = root["maxConsecutiveWorkingDays"]?.jsonPrimitive?.content?.toInt() ?: 5
        val idFromFile = root["id"]?.jsonPrimitive?.content
        return Instance(
            id = idFromFile ?: "toy-${hashCode().toUInt().toString(16)}",
            name = idFromFile ?: "Toy instance",
            source = "toy",
            horizonDays = horizonDays,
            shifts = shifts,
            nurses = nurses,
            coverage = coverage,
            forbiddenTransitions = forbidden,
            minRestHours = minRestHours,
            maxConsecutiveWorkingDays = maxConsec,
            preferences = preferences,
            fixedOff = fixedOff,
        )
    }

    private fun JsonObject.toToyShift(): Shift {
        val id = this["id"]!!.jsonPrimitive.content
        val label = this["name"]?.jsonPrimitive?.content ?: id
        val start = this["start"]?.jsonPrimitive?.content ?: "00:00"
        val end = this["end"]?.jsonPrimitive?.content ?: "00:00"
        val startMinutes = parseHhmm(start)
        val endMinutes = parseHhmm(end)
        val duration = if (endMinutes <= startMinutes) endMinutes + 24 * 60 - startMinutes else endMinutes - startMinutes
        return Shift(
            id = id,
            label = label,
            startMinutes = startMinutes,
            durationMinutes = duration,
        )
    }

    private fun parseHhmm(s: String): Int {
        val parts = s.split(":")
        require(parts.size == 2) { "invalid HH:MM: '$s'" }
        val h = parts[0].toInt()
        val m = parts[1].toInt()
        require(h in 0..23 && m in 0..59) { "HH:MM out of range: '$s'" }
        return h * 60 + m
    }

    private fun JsonObject.toToyNurse(): Nurse = Nurse(
        id = this["id"]!!.jsonPrimitive.content,
        name = this["name"]?.jsonPrimitive?.content ?: this["id"]!!.jsonPrimitive.content,
        skills = (this["skills"] as? kotlinx.serialization.json.JsonArray)?.map { it.jsonPrimitive.content }?.toSet()
            ?: emptySet(),
        contractHoursPerWeek = this["contractHoursPerWeek"]?.jsonPrimitive?.content?.toInt() ?: 40,
    )

    private fun JsonObject.toToyCoverage(): CoverageRequirement = CoverageRequirement(
        day = this["day"]!!.jsonPrimitive.content.toInt(),
        shiftId = this["shiftId"]!!.jsonPrimitive.content,
        min = this["min"]!!.jsonPrimitive.content.toInt(),
        max = this["max"]!!.jsonPrimitive.content.toInt(),
        requiredSkills = (this["requiredSkills"] as? kotlinx.serialization.json.JsonArray)
            ?.map { it.jsonPrimitive.content }?.toSet()
            ?: emptySet(),
    )

    // -------------------------------------------------------------------------
    // Wire-schema parser — apps/shared/schemas/nsp-instance.schema.json
    // -------------------------------------------------------------------------

    private fun parseWire(root: JsonObject): Instance {
        val horizonDays = root["horizonDays"]?.jsonPrimitive?.content?.toInt()
            ?: error("wire instance missing horizonDays")
        val shifts = (root["shifts"] as? kotlinx.serialization.json.JsonArray ?: error("wire instance missing shifts"))
            .map { (it as JsonObject).toWireShift() }
        val (nurses, perNursePrefs) = (
            root["nurses"] as? kotlinx.serialization.json.JsonArray
                ?: error("wire instance missing nurses")
            ).map { (it as JsonObject).toWireNurse() }.let { list ->
            list.map { it.first } to list.flatMap { it.second }
        }
        val coverage = (root["coverage"] as? kotlinx.serialization.json.JsonArray ?: error("wire instance missing coverage"))
            .map { it.jsonObject.toWireCoverage() }
        val forbidden = root["forbiddenTransitions"]?.jsonArray?.map {
            val arr = it.jsonArray
            arr[0].jsonPrimitive.content to arr[1].jsonPrimitive.content
        } ?: emptyList()
        val flatPrefs = (root["preferences"] as? kotlinx.serialization.json.JsonArray)?.map {
            val o = it.jsonObject
            Preference(
                nurseId = o["nurseId"]!!.jsonPrimitive.content,
                day = o["day"]!!.jsonPrimitive.content.toInt(),
                shiftId = o["shiftId"]?.jsonPrimitive?.contentOrNull(),
                weight = o["weight"]!!.jsonPrimitive.content.toInt(),
            )
        } ?: emptyList()
        val metadata = root["metadata"] as? JsonObject
        val minRestHours = metadata?.get("minRestHours")?.jsonPrimitive?.content?.toInt() ?: 11
        val maxConsec = metadata?.get("maxConsecutiveWorkingDays")?.jsonPrimitive?.content?.toInt() ?: 5
        val tolerance = metadata?.get("contractTolerance")?.jsonPrimitive?.content?.toInt() ?: 4
        val id = root["id"]?.jsonPrimitive?.content ?: "inst-${hashCode().toUInt().toString(16)}"
        return Instance(
            id = id,
            name = root["name"]?.jsonPrimitive?.content ?: id,
            source = root["source"]?.jsonPrimitive?.content ?: "custom",
            horizonDays = horizonDays,
            shifts = shifts,
            nurses = nurses,
            coverage = coverage,
            forbiddenTransitions = forbidden,
            minRestHours = minRestHours,
            maxConsecutiveWorkingDays = maxConsec,
            preferences = perNursePrefs + flatPrefs,
            contractTolerance = tolerance,
        )
    }

    private fun JsonObject.toWireShift(): Shift = Shift(
        id = this["id"]!!.jsonPrimitive.content,
        label = this["label"]?.jsonPrimitive?.content ?: this["id"]!!.jsonPrimitive.content,
        startMinutes = this["startMinutes"]?.jsonPrimitive?.content?.toInt() ?: 0,
        durationMinutes = this["durationMinutes"]?.jsonPrimitive?.content?.toInt() ?: 480,
        skill = this["skill"]?.jsonPrimitive?.contentOrNull(),
    )

    private fun JsonObject.toWireNurse(): Pair<Nurse, List<Preference>> {
        val id = this["id"]!!.jsonPrimitive.content
        val skills = (this["skills"] as? kotlinx.serialization.json.JsonArray)
            ?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()
        val unavailable = (this["unavailable"] as? kotlinx.serialization.json.JsonArray)
            ?.map { it.jsonPrimitive.content.toInt() }?.toSet() ?: emptySet()
        val perNursePrefs = (this["preferences"] as? kotlinx.serialization.json.JsonArray)?.map {
            val p = it.jsonObject
            Preference(
                nurseId = id,
                day = p["day"]!!.jsonPrimitive.content.toInt(),
                shiftId = p["shiftId"]?.jsonPrimitive?.contentOrNull(),
                weight = p["weight"]!!.jsonPrimitive.content.toInt(),
            )
        } ?: emptyList()
        val nurse = Nurse(
            id = id,
            name = this["name"]?.jsonPrimitive?.content ?: id,
            skills = skills,
            contractHoursPerWeek = this["contractHoursPerWeek"]?.jsonPrimitive?.content?.toInt() ?: 40,
            maxConsecutiveWorkingDays = this["maxConsecutiveWorkingDays"]?.jsonPrimitive?.content?.toInt(),
            maxShiftsPerHorizon = this["maxShiftsPerWeek"]?.jsonPrimitive?.content?.toInt(),
            minShiftsPerHorizon = this["minShiftsPerWeek"]?.jsonPrimitive?.content?.toInt(),
            unavailableDays = unavailable,
        )
        return nurse to perNursePrefs
    }

    private fun JsonObject.toWireCoverage(): CoverageRequirement {
        // Wire schema uses `required` (single int). Treat it as min=max=required for HC-1.
        val required = this["required"]?.jsonPrimitive?.content?.toInt()
        val min = this["min"]?.jsonPrimitive?.content?.toInt() ?: required ?: 0
        val max = this["max"]?.jsonPrimitive?.content?.toInt() ?: required ?: Int.MAX_VALUE / 2
        val skill = this["skill"]?.jsonPrimitive?.contentOrNull()
        val requiredSkills = (this["requiredSkills"] as? kotlinx.serialization.json.JsonArray)
            ?.map { it.jsonPrimitive.content }?.toSet()
            ?: skill?.let { setOf(it) }
            ?: emptySet()
        return CoverageRequirement(
            day = this["day"]!!.jsonPrimitive.content.toInt(),
            shiftId = this["shiftId"]!!.jsonPrimitive.content,
            min = min,
            max = max,
            requiredSkills = requiredSkills,
        )
    }

    // -------------------------------------------------------------------------
    // Canonical (wire) JSON emitter
    // -------------------------------------------------------------------------

    private fun Instance.toWireJson(): JsonElement = buildJsonObject {
        put("id", id)
        put("name", name)
        put("source", source)
        put("horizonDays", horizonDays)
        put("shifts", buildJsonArray {
            for (s in shifts) add(buildJsonObject {
                put("id", s.id)
                put("label", s.label)
                put("startMinutes", s.startMinutes)
                put("durationMinutes", s.durationMinutes)
                s.skill?.let { put("skill", it) }
            })
        })
        put("nurses", buildJsonArray {
            for (n in nurses) add(buildJsonObject {
                put("id", n.id)
                put("name", n.name)
                put("skills", buildJsonArray { for (sk in n.skills) add(JsonPrimitive(sk)) })
                put("contractHoursPerWeek", n.contractHoursPerWeek)
                n.maxConsecutiveWorkingDays?.let { put("maxConsecutiveWorkingDays", it) }
                n.maxShiftsPerHorizon?.let { put("maxShiftsPerWeek", it) }
                n.minShiftsPerHorizon?.let { put("minShiftsPerWeek", it) }
                put("unavailable", buildJsonArray { for (d in n.unavailableDays) add(JsonPrimitive(d)) })
            })
        })
        put("coverage", buildJsonArray {
            for (c in coverage) add(buildJsonObject {
                put("day", c.day)
                put("shiftId", c.shiftId)
                put("min", c.min)
                put("max", c.max)
                if (c.requiredSkills.isNotEmpty()) {
                    put("requiredSkills", buildJsonArray { for (sk in c.requiredSkills) add(JsonPrimitive(sk)) })
                }
            })
        })
        put("forbiddenTransitions", buildJsonArray {
            for ((a, b) in forbiddenTransitions) add(buildJsonArray { add(JsonPrimitive(a)); add(JsonPrimitive(b)) })
        })
        if (preferences.isNotEmpty()) {
            put("preferences", buildJsonArray {
                for (p in preferences) add(buildJsonObject {
                    put("nurseId", p.nurseId)
                    put("day", p.day)
                    if (p.shiftId != null) put("shiftId", p.shiftId) else put("shiftId", JsonNull)
                    put("weight", p.weight)
                })
            })
        }
        if (fixedOff.isNotEmpty()) {
            put("fixedOff", buildJsonArray {
                for (f in fixedOff) add(buildJsonObject {
                    put("nurseId", f.nurseId)
                    put("day", f.day)
                })
            })
        }
        put("metadata", buildJsonObject {
            put("minRestHours", minRestHours)
            put("maxConsecutiveWorkingDays", maxConsecutiveWorkingDays)
            put("contractTolerance", contractTolerance)
        })
    }
}

/** Safe accessor: string content or null for JsonNull. */
private fun JsonPrimitive.contentOrNull(): String? = if (this is JsonNull) null else content
