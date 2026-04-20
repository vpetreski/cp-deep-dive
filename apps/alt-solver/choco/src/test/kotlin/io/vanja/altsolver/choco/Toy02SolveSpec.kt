package io.vanja.altsolver.choco

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import java.nio.file.Paths

/**
 * End-to-end solve on toy-02 (5 nurses x 14 days x {M, D, N}).
 *
 * Bigger instance; 15s is plenty for Choco to find and prove a feasible
 * schedule respecting HC-1..HC-5 even if it doesn't finish proving optimality
 * for the soft objective.
 */
class Toy02SolveSpec : StringSpec({

    "toy-02 solves feasibly within the time limit" {
        val path = Paths.get("../../../data/nsp/toy-02.json").toAbsolutePath().normalize()
        val result = solveNsp(path, timeLimitSeconds = 15L)

        result.feasible shouldBe true
        result.assignments.size shouldBeGreaterThan 0

        // toy-02 min demand: 14 days x (1M + 1D + 1N) = 42.
        result.assignments.size shouldBeGreaterThan 41

        // HC-2: one shift per nurse per day.
        val perNurseDay = result.assignments.groupBy { it.nurseId to it.day }
        perNurseDay.values.forEach { it.size shouldBeLessThanOrEqual 1 }

        // HC-3: three forbidden transitions — N->M, N->D, D->M.
        val byNurse = result.assignments.groupBy { it.nurseId }
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
        val result = solveNsp(path, timeLimitSeconds = 15L)

        val byNurse = result.assignments.groupBy { it.nurseId }
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
    }

    "toy-02 respects HC-4 (max 5 consecutive working days)" {
        val path = Paths.get("../../../data/nsp/toy-02.json").toAbsolutePath().normalize()
        val result = solveNsp(path, timeLimitSeconds = 15L)

        val byNurse = result.assignments.groupBy { it.nurseId }
        byNurse.values.forEach { rows ->
            val workDays = rows.map { it.day }.sorted()
            var run = 0
            var maxRun = 0
            var prev = Int.MIN_VALUE
            for (d in workDays) {
                run = if (d == prev + 1) run + 1 else 1
                maxRun = maxOf(maxRun, run)
                prev = d
            }
            maxRun shouldBeLessThanOrEqual 5
        }
    }
})
