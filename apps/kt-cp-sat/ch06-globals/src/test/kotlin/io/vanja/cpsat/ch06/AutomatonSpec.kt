package io.vanja.cpsat.ch06

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class AutomatonSpec : StringSpec({
    "14-day schedule with 7 nights and no 4 in a row is feasible" {
        val sol = solveNoFourNights(horizon = 14, targetNights = 7)
        sol shouldNotBe null
        val s = checkNotNull(sol)
        s.totalNights shouldBe 7
        s.maxRun.shouldBeLessThanOrEqual(3)
        s.days.all { it == 0 || it == 1 } shouldBe true
    }

    "14-day schedule with 0 nights is trivially feasible" {
        val sol = solveNoFourNights(horizon = 14, targetNights = 0)
        sol shouldNotBe null
        val s = checkNotNull(sol)
        s.totalNights shouldBe 0
        s.maxRun shouldBe 0
    }

    "packing too many nights into too short a horizon is infeasible" {
        // Over 4 consecutive days you can fit at most 3 nights (no 4 in a row).
        val sol = solveNoFourNights(horizon = 4, targetNights = 4)
        sol shouldBe null
    }
})
