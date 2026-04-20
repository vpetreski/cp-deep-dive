package io.vanja.cpsat.ch06

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class InverseSpec : StringSpec({
    "assign and shiftOf are mutually consistent permutations" {
        val sol = solveInverseDemo()
        sol shouldNotBe null
        val s = checkNotNull(sol)
        s.assign.size shouldBe s.shiftOf.size
        s.assign.toSet().size shouldBe s.assign.size
        s.shiftOf.toSet().size shouldBe s.shiftOf.size
        for (i in s.assign.indices) {
            val j = s.assign[i]
            s.shiftOf[j] shouldBe i
        }
    }

    "forbidden pair is respected" {
        val sol = solveInverseDemo(forbiddenPairs = listOf(0 to 0), pinAssignments = emptyMap())
        sol shouldNotBe null
        val s = checkNotNull(sol)
        (s.assign[0] != 0) shouldBe true
    }

    "pinned assignment is honored" {
        val sol = solveInverseDemo(forbiddenPairs = emptyList(), pinAssignments = mapOf(3 to 2))
        sol shouldNotBe null
        val s = checkNotNull(sol)
        s.assign[3] shouldBe 2
        s.shiftOf[2] shouldBe 3
    }
})
