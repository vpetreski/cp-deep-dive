package io.vanja.cpsat.ch05

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class StreamingSpec : StringSpec({
    "streaming emits at least one incumbent for the knapsack demo" {
        runTest {
            val incumbents = streamKnapsack(DEMO_ITEMS, DEMO_CAPACITY, timeLimitS = 5.0).toList()
            incumbents shouldHaveAtLeastSize 1
            val last = incumbents.last()
            (last.objective > 0L) shouldBe true
            (last.bound >= last.objective) shouldBe true
        }
    }

    "incumbents are monotonically non-decreasing (maximize)" {
        runTest {
            val incumbents = streamKnapsack(DEMO_ITEMS, DEMO_CAPACITY, timeLimitS = 5.0).toList()
            for (i in 1 until incumbents.size) {
                (incumbents[i].objective >= incumbents[i - 1].objective) shouldBe true
            }
        }
    }
})
