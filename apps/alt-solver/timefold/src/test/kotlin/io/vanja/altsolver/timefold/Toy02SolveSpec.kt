package io.vanja.altsolver.timefold

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import java.nio.file.Paths

/**
 * End-to-end solve on toy-02 (5 nurses × 14 days × {M, D, N}).
 *
 * Larger instance, so we give it a longer budget. Still well under the 30s
 * CLI default. Assertion set is the hard-constraint subset Ch18 covers.
 */
class Toy02SolveSpec : StringSpec({

    "toy-02 solves feasibly within the time limit" {
        val path = Paths.get("../../../data/nsp/toy-02.json").toAbsolutePath().normalize()
        val result = solveNsp(path, secondsLimit = 15L)

        result.hardScore shouldBe 0
        result.feasible shouldBe true

        val flat = result.solution.toScheduleAssignments()
        // toy-02 has 14 days × (1M + 1D + 1N) = 42 required slots
        flat.size shouldBe 42

        // HC-2: no nurse twice on one day
        val perNurseDay = flat.groupBy { it.nurseId to it.day }
        perNurseDay.values.forEach { it.size shouldBeLessThanOrEqual 1 }

        // HC-3: forbidden transitions are N->M, N->D, D->M
        val byNurse = flat.groupBy { it.nurseId }
        val forbidden = setOf("N" to "M", "N" to "D", "D" to "M")
        byNurse.values.forEach { rows ->
            val byDay = rows.associate { it.day to it.shiftId }
            for ((d, s) in byDay) {
                val next = byDay[d + 1] ?: continue
                ((s to next) in forbidden) shouldBe false
            }
        }
    }

    "toy-02 respects HC-5 (max 3 consecutive nights)" {
        val path = Paths.get("../../../data/nsp/toy-02.json").toAbsolutePath().normalize()
        val result = solveNsp(path, secondsLimit = 15L)
        val flat = result.solution.toScheduleAssignments()

        val byNurse = flat.groupBy { it.nurseId }
        byNurse.values.forEach { rows ->
            val nightDays = rows.filter { it.shiftId == "N" }.map { it.day }.sorted()
            var run = 0
            var maxRun = 0
            var prev = Int.MIN_VALUE
            for (d in nightDays) {
                run = if (d == prev + 1) run + 1 else 1
                maxRun = maxOf(maxRun, run)
                prev = d
            }
            maxRun shouldBeLessThanOrEqual 3
        }

        flat.size shouldBeGreaterThan 0
    }
})
