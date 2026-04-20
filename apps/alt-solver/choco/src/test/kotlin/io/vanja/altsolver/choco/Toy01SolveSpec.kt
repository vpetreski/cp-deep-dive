package io.vanja.altsolver.choco

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Paths

/**
 * End-to-end solve on toy-01 (3 nurses x 7 days x {D, N}).
 *
 * Choco is deterministic for a fixed model, so a short 8s budget is plenty;
 * the optimum on this toy is found sub-second.
 */
class Toy01SolveSpec : StringSpec({

    "toy-01 solves feasibly within the time limit" {
        val path = Paths.get("../../../data/nsp/toy-01.json").toAbsolutePath().normalize()
        val result = solveNsp(path, timeLimitSeconds = 8L)

        result.feasible shouldBe true
        result.assignments.size shouldBeGreaterThan 0

        // toy-01 has 7 days x (1D + 1N) minimum demand = 14 required slots,
        // plus possibly an extra D shift up to the max=2 cap on any given day.
        val minRequired = 14
        result.assignments.size shouldBeGreaterThan (minRequired - 1)

        // HC-2: one shift per nurse per day.
        val perNurseDay = result.assignments.groupBy { it.nurseId to it.day }
        perNurseDay.values.forEach { it.size shouldBeLessThanOrEqual 1 }

        // HC-3: forbidden transition N -> D must not occur.
        val byNurse = result.assignments.groupBy { it.nurseId }
        byNurse.values.forEach { rows ->
            val byDay = rows.associate { it.day to it.shiftId }
            for ((d, shift) in byDay) {
                if (shift == "N") {
                    (byDay[d + 1] == "D") shouldBe false
                }
            }
        }

        // N3 is unavailable on day 3 — must not be scheduled that day.
        val n3OnDay3 = result.assignments.any { it.nurseId == "N3" && it.day == 3 }
        n3OnDay3 shouldBe false
    }

    "toy-01 JSON output conforms to schedule schema" {
        val path = Paths.get("../../../data/nsp/toy-01.json").toAbsolutePath().normalize()
        val result = solveNsp(path, timeLimitSeconds = 5L)
        val rendered = renderScheduleJson(result)

        // Minimal structural validation: parse + shape check.
        val root = Json.parseToJsonElement(rendered).jsonObject
        root["instanceId"]!!.jsonPrimitive.content shouldBe "toy-01"
        root["generatedAt"]!!.jsonPrimitive.content.isNotEmpty() shouldBe true

        val assignments = root["assignments"]!!.jsonArray
        assignments.size shouldBeGreaterThan 0

        for (entry in assignments) {
            val obj: JsonObject = entry.jsonObject
            obj["nurseId"]!!.jsonPrimitive.content.isNotEmpty() shouldBe true
            // day is required and must be a non-negative integer.
            obj["day"]!!.jsonPrimitive.content.toInt() shouldBeGreaterThan -1
            // shiftId is required (we never emit day-off rows; absence == off).
            obj["shiftId"]!!.jsonPrimitive.content.isNotEmpty() shouldBe true
        }
    }
})
