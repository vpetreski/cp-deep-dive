package io.vanja.cpsat.ch06

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class AllDifferentSpec : StringSpec({
    "five distinct values in 0..10 summing to 30 exist" {
        val sol = solveAllDifferentSum(n = 5, maxVal = 10, targetSum = 30)
        sol shouldNotBe null
        val s = checkNotNull(sol)
        s.values.toSet().size shouldBe s.values.size
        s.values.all { it in 0..10 } shouldBe true
        s.sum shouldBe 30L
    }

    "requested sum below minimum is infeasible" {
        // Distinct values 0..10 minimum sum for 5 values is 0+1+2+3+4 = 10.
        val sol = solveAllDifferentSum(n = 5, maxVal = 10, targetSum = 9)
        sol shouldBe null
    }

    "requested sum above maximum is infeasible" {
        // Distinct values 0..10 maximum sum for 5 values is 6+7+8+9+10 = 40.
        val sol = solveAllDifferentSum(n = 5, maxVal = 10, targetSum = 41)
        sol shouldBe null
    }
})
