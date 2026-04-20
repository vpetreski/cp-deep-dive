package io.vanja.cpsat.ch05

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class KnapsackSpec : StringSpec({
    "hand-verified 4-item knapsack returns the expected optimum" {
        val items = listOf(
            Item("A", 2, 3),
            Item("B", 3, 4),
            Item("C", 4, 5),
            Item("D", 5, 6),
        )
        val r = solveKnapsack(items, capacity = 5)
        r.status shouldBe "OPTIMAL"
        r.value shouldBe 7L
        r.chosen shouldContainExactlyInAnyOrder listOf("A", "B")
    }

    "capacity 0 forces all-zeros" {
        val items = listOf(Item("x", 1, 10), Item("y", 1, 20))
        val r = solveKnapsack(items, capacity = 0)
        r.status shouldBe "OPTIMAL"
        r.value shouldBe 0L
        r.chosen shouldBe emptyList()
    }

    "demo instance is feasible with a positive objective" {
        val r = solveKnapsack(DEMO_ITEMS, DEMO_CAPACITY, timeLimitS = 5.0)
        (r.status in setOf("OPTIMAL", "FEASIBLE")) shouldBe true
        (r.value ?: 0L > 0L) shouldBe true
    }
})
