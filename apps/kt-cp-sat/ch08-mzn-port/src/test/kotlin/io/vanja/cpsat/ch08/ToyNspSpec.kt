package io.vanja.cpsat.ch08

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThanOrEqual

class ToyNspSpec : StringSpec({
    "demo instance solves and every (day, shift) has exactly one nurse" {
        val i = DEMO_TOY_NSP
        val sol = solveToyNsp(i, timeLimitS = 10.0)
        sol.status shouldBe "OPTIMAL"

        // HC-1: exactly one per (day, shift).
        for (d in 0 until i.nDays) {
            for (s in 0 until i.nShifts) {
                val count = (0 until i.nNurses).count { n -> sol.work[n][d][s] }
                count shouldBe 1
            }
        }
    }

    "HC-2: no nurse takes two shifts on the same day" {
        val i = DEMO_TOY_NSP
        val sol = solveToyNsp(i, timeLimitS = 10.0)
        for (n in 0 until i.nNurses) {
            for (d in 0 until i.nDays) {
                val count = (0 until i.nShifts).count { s -> sol.work[n][d][s] }
                count.toLong().shouldBeLessThanOrEqual(1L)
            }
        }
    }

    "HC-3: totals respect workload bounds" {
        val i = DEMO_TOY_NSP
        val sol = solveToyNsp(i, timeLimitS = 10.0)
        for (t in sol.totals) {
            t.shouldBeGreaterThanOrEqual(i.minWork.toLong())
            t.shouldBeLessThanOrEqual(i.maxWork.toLong())
        }
    }

    "spread = max(totals) - min(totals)" {
        val i = DEMO_TOY_NSP
        val sol = solveToyNsp(i, timeLimitS = 10.0)
        val expected = (sol.totals.max() - sol.totals.min())
        sol.spread shouldBe expected
    }

    "tighter workload bounds (balanced instance) gives spread 0 or 1" {
        val tight = NspInstance(nNurses = 7, nDays = 7, nShifts = 2, minWork = 2, maxWork = 2)
        val sol = solveToyNsp(tight, timeLimitS = 10.0)
        sol.status shouldBe "OPTIMAL"
        // Seven nurses, 14 shifts, min=max=2 -> all totals == 2, spread == 0.
        sol.spread shouldBe 0L
    }

    "construction fails when workload bounds are infeasible" {
        var threw = false
        try {
            NspInstance(nNurses = 2, nDays = 7, nShifts = 2, minWork = 0, maxWork = 3)
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        threw shouldBe true
    }
})
