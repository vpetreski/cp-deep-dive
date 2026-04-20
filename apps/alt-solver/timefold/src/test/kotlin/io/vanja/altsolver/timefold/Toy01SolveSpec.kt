package io.vanja.altsolver.timefold

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.file.Paths

/**
 * End-to-end solve on toy-01 (3 nurses × 7 days × {D, N}).
 *
 * Expectation: Timefold converges to a feasible (hard=0) schedule well under
 * the time budget; this test uses a shorter 8-second limit so CI doesn't
 * blow up while still giving local search room to breathe.
 *
 * We also sanity-check the schedule shape: exactly one assignment per slot,
 * no forbidden N->D transitions, no two shifts for one nurse on one day.
 */
class Toy01SolveSpec : StringSpec({

    "toy-01 solves feasibly within the time limit" {
        val path = Paths.get("../../../data/nsp/toy-01.json").toAbsolutePath().normalize()
        val result = solveNsp(path, secondsLimit = 8L)

        // HC-1..HC-5 all hold -> hard score must be exactly 0.
        result.hardScore shouldBe 0
        result.feasible shouldBe true

        val flat = result.solution.toScheduleAssignments()
        flat.size shouldBeGreaterThan 0

        // toy-01 has 7 days × (1 D + 1 N) minimum demand = 14 required slots.
        flat.size shouldBe 14

        // HC-2: each (nurse, day) pair appears at most once.
        val perNurseDay = flat.groupBy { it.nurseId to it.day }
        perNurseDay.values.forEach { it.size shouldBeLessThanOrEqual 1 }

        // HC-3: no N (day d) immediately followed by D (day d+1) for any nurse.
        val byNurse = flat.groupBy { it.nurseId }
        byNurse.values.forEach { rows ->
            val byDay = rows.associate { it.day to it.shiftId }
            for ((d, shift) in byDay) {
                if (shift == "N") {
                    byDay[d + 1] shouldNotBe "D"
                }
            }
        }
    }

    "toy-01 render produces one row per nurse" {
        val path = Paths.get("../../../data/nsp/toy-01.json").toAbsolutePath().normalize()
        val result = solveNsp(path, secondsLimit = 3L)
        val render = renderSchedule(result.solution)
        val lines = render.lines()
        // header + 3 nurses = 4 lines
        lines.size shouldBe 4
    }
})
