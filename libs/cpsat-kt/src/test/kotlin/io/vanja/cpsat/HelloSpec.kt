package io.vanja.cpsat

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Smoke test: the simplest possible model compiles, loads OR-Tools natives,
 * and returns an [SolveResult.Optimal].
 *
 * Problem:
 *   minimize x + y
 *   subject to
 *     3x + 2y = 12
 *     x + y ≤ 5
 *     x, y ∈ [0, 10]
 *
 * Integer assignments satisfying 3x + 2y = 12 with x+y ≤ 5 include
 * (x=4, y=0) → sum=4 and (x=2, y=3) → sum=5. Optimum: sum=4.
 */
class HelloSpec : StringSpec({

    "trivial model returns Optimal with objective 4" {
        lateinit var x: IntVar
        lateinit var y: IntVar

        val model = cpModel {
            x = intVar("x", 0..10)
            y = intVar("y", 0..10)

            constraint {
                +(3 * x + 2 * y eq 12)
                +(x + y le 5)
            }

            minimize { x + y }
        }

        val result = model.solveBlocking {
            randomSeed = 42
        }

        result.shouldBeInstanceOf<SolveResult.Optimal>()
        result.objective shouldBe 4L
        // Optimum is (x=4, y=0).
        result.values[x] shouldBe 4L
        result.values[y] shouldBe 0L
    }

    "infeasible model returns Infeasible" {
        val model = cpModel {
            val x = intVar("x", 0..10)
            constraint {
                +(x ge 5)
                +(x le 3)
            }
        }
        val result = model.solveBlocking { randomSeed = 42 }
        result shouldBe SolveResult.Infeasible
    }

    "no-objective feasibility returns Optimal" {
        lateinit var x: IntVar
        lateinit var y: IntVar
        val model = cpModel {
            x = intVar("x", 0..10)
            y = intVar("y", 0..10)
            constraint {
                +(x + y eq 7)
                +(x eq 3)
            }
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        res.values[x] shouldBe 3L
        res.values[y] shouldBe 4L
    }

    "BoolVar fixed to true is readable as Boolean" {
        lateinit var b: BoolVar
        val model = cpModel {
            b = boolVar("b")
            constraint { +(b eq 1L) }
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        res.values[b] shouldBe true
    }
})
