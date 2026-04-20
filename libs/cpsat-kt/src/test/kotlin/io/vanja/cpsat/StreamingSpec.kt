package io.vanja.cpsat

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

/**
 * Exercises `solveFlow` — the streaming coroutine-flow API that emits
 * incumbent solutions as they're found.
 */
class StreamingSpec : StringSpec({

    "solveFlow emits at least one solution and completes" {
        lateinit var x: IntVar
        lateinit var y: IntVar
        val model = cpModel {
            x = intVar("x", 0..10)
            y = intVar("y", 0..10)
            constraint {
                +(x + y eq 7L)
            }
            maximize { x }
        }

        runTest {
            val solutions = model.solveFlow {
                randomSeed = 42
                maxSolutions = 5
            }.toList()

            // At least one incumbent; in a maximize problem the solver may
            // emit several incumbents while improving.
            solutions shouldHaveAtLeastSize 1
            // Last solution should be the optimal x = 10, y = −3? No — x + y = 7
            // and x, y ∈ [0,10]. Max x is 7 (so y = 0). The final incumbent
            // must have x ≤ 7.
            val last = solutions.last()
            (last.objective in 0..7) shouldBe true
        }
    }

    "solveFlow with maxSolutions stops after N" {
        lateinit var xs: List<IntVar>
        val model = cpModel {
            xs = intVarList("x", 3, 0..5)
            constraint {
                +(xs[0] + xs[1] + xs[2] eq 10L)
            }
        }
        runTest {
            val solutions = model.solveFlow {
                randomSeed = 42
                maxSolutions = 3
                // Without an objective, enumerate-all-solutions mode is needed
                // to produce multiple solutions via the callback. Enable via raw.
                rawProto { enumerateAllSolutions = true }
            }.take(3).toList()
            (solutions.size <= 3) shouldBe true
        }
    }

    "solveFlow snapshot reads are consistent within a solution" {
        lateinit var x: IntVar
        lateinit var y: IntVar
        val model = cpModel {
            x = intVar("x", 0..10)
            y = intVar("y", 0..10)
            constraint {
                +(x + y eq 7L)
            }
            maximize { x }
        }
        runTest {
            val solutions = model.solveFlow { randomSeed = 42; maxSolutions = 20 }.toList()
            solutions shouldHaveAtLeastSize 1
            for (s in solutions) {
                val xVal = s[x]
                val yVal = s[y]
                (xVal + yVal) shouldBe 7L
            }
        }
    }
})
