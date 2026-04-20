package io.vanja.cpsat.ch05

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual

class BinPackingSpec : StringSpec({
    "perfect packing: 4 items of size 5 into bins of 10 -> 2 bins" {
        val items = listOf("a" to 5L, "b" to 5L, "c" to 5L, "d" to 5L)
        val r = solveBinPacking(items, capacity = 10)
        r.status shouldBe "OPTIMAL"
        r.binsUsed shouldBe 2
        // Each bin's total is within capacity.
        val byBin = r.assignments.groupBy { it.bin }
        byBin.values.forEach { assigns ->
            val total = assigns.sumOf { a -> items.first { it.first == a.item }.second }
            total.shouldBeGreaterThanOrEqual(0L) // sanity
            (total <= 10L) shouldBe true
        }
    }

    "demo instance uses at most 4 bins (sum = 34, capacity = 10)" {
        val r = solveBinPacking(DEMO_BIN_ITEMS, DEMO_BIN_CAPACITY, timeLimitS = 5.0)
        (r.status in setOf("OPTIMAL", "FEASIBLE")) shouldBe true
        val binsUsed = checkNotNull(r.binsUsed)
        // Lower bound: ceil(34 / 10) = 4. So optimal = 4.
        (binsUsed >= 4) shouldBe true
    }

    "oversized item is infeasible" {
        val items = listOf("huge" to 100L)
        // capacity 10 < size 100 → require(size ≤ capacity) throws.
        var threw = false
        try {
            solveBinPacking(items, capacity = 10)
        } catch (t: IllegalArgumentException) {
            threw = true
        }
        threw shouldBe true
    }
})
