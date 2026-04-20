package io.vanja.cpsat.ch06

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class ElementSpec : StringSpec({
    "element picks smallest above the threshold" {
        val sol = solveElementDemo(menu = longArrayOf(10, 20, 30, 40, 50), minValue = 21)
        sol shouldNotBe null
        val s = checkNotNull(sol)
        s.pickedValue shouldBe 30L
        s.pickedIndex shouldBe 2
    }

    "element returns lowest menu value when no threshold binds" {
        val sol = solveElementDemo(menu = longArrayOf(7, 3, 5), minValue = 0)
        sol shouldNotBe null
        val s = checkNotNull(sol)
        s.pickedValue shouldBe 3L
        s.pickedIndex shouldBe 1
    }

    "element is infeasible when no entry meets the threshold" {
        val sol = solveElementDemo(menu = longArrayOf(1, 2, 3), minValue = 100)
        sol shouldBe null
    }
})
