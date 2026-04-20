package io.vanja.cpsat.ch11

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.vanja.cpsat.nsp.Instance
import io.vanja.cpsat.nsp.InstanceIo
import io.vanja.cpsat.nsp.Schedule
import io.vanja.cpsat.nsp.SolveParams
import io.vanja.cpsat.nsp.SolveResult
import io.vanja.cpsat.nsp.scheduleOrNull
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Chapter 11 guarantees: every bundled toy instance solves to OPTIMAL/FEASIBLE
 * under the hard-only model, and the returned schedule respects every HC.
 */
class SolveTest : StringSpec({

    "toy-01 solves to OPTIMAL or FEASIBLE" {
        val result = solve(loadToy("toy-01.json"), SolveParams(maxTimeSeconds = 15.0))
        (result is SolveResult.Optimal || result is SolveResult.Feasible).shouldBeTrue()
        result.scheduleOrNull() shouldNotBe null
    }

    "toy-02 solves to OPTIMAL or FEASIBLE" {
        val result = solve(loadToy("toy-02.json"), SolveParams(maxTimeSeconds = 30.0))
        (result is SolveResult.Optimal || result is SolveResult.Feasible).shouldBeTrue()
        result.scheduleOrNull() shouldNotBe null
    }

    "schedule honors HC-1 (coverage bounds per cell)" {
        val inst = loadToy("toy-01.json")
        val sched = solve(inst).scheduleOrNull()!!
        for (req in inst.coverage) {
            val n = sched.assignments.count { it.day == req.day && it.shiftId == req.shiftId }
            n shouldBe req.min  // toy-01 has min == max on every cell
        }
    }

    "schedule honors HC-2 (one shift per day per nurse)" {
        val inst = loadToy("toy-02.json")
        val sched = solve(inst).scheduleOrNull()!!
        for (n in inst.nurses) {
            for (d in 0 until inst.horizonDays) {
                val dayAssignments = sched.assignments
                    .filter { it.nurseId == n.id && it.day == d }
                dayAssignments shouldHaveSize 1
            }
        }
    }

    "schedule honors HC-3 (no banned transitions)" {
        val inst = loadToy("toy-02.json")
        val sched = solve(inst).scheduleOrNull()!!
        val banned = inst.forbiddenTransitions.toSet()
        for (n in inst.nurses) {
            val daily = (0 until inst.horizonDays).map { d ->
                sched.assignments.first { it.nurseId == n.id && it.day == d }.shiftId
            }
            for (d in 0 until inst.horizonDays - 1) {
                val a = daily[d] ?: continue
                val b = daily[d + 1] ?: continue
                ((a to b) in banned) shouldBe false
            }
        }
    }

    "schedule honors HC-4 (max consecutive working days)" {
        val inst = loadToy("toy-02.json")
        val sched = solve(inst).scheduleOrNull()!!
        for (n in inst.nurses) {
            val k = n.maxConsecutiveWorkingDays ?: inst.maxConsecutiveWorkingDays
            val worksDays = (0 until inst.horizonDays).map { d ->
                sched.assignments.first { it.nurseId == n.id && it.day == d }.shiftId != null
            }
            // Maximum run of consecutive trues must be ≤ k
            val maxRun = worksDays.runningFold(0) { acc, w -> if (w) acc + 1 else 0 }.max()
            maxRun shouldBeLessThanOrEqual k
        }
    }

    "fixedOff days are respected" {
        val inst = loadToy("toy-01.json")
        val sched = solve(inst).scheduleOrNull()!!
        val n3day3 = sched.assignments.first { it.nurseId == "N3" && it.day == 3 }
        n3day3.shiftId shouldBe null
    }
})

internal fun loadToy(name: String): Instance {
    var dir: Path? = Path.of("").toAbsolutePath()
    while (dir != null) {
        val candidate = dir.resolve("data/nsp/$name")
        if (candidate.exists()) return InstanceIo.load(candidate)
        dir = dir.parent
    }
    error("Could not locate data/nsp/$name above CWD")
}
