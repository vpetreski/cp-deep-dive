package io.vanja.cpsat

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Lexicographic multi-stage optimization: solve primary, fix it, then solve
 * secondary. Result is the final stage's result.
 */
class LexicographicSpec : StringSpec({

    "two-stage lex: minimize primary, then secondary within primary optimum" {
        // primary  = x + y, want to minimize → x + y = 0 (with x, y ∈ [0, 10]).
        // secondary = -x (i.e., maximize x) → under primary x+y=0, x=0, y=0.
        // Last result must report secondary optimum with x=0 and primary still 0.
        lateinit var x: IntVar
        lateinit var y: IntVar
        val model = cpModel {
            x = intVar("x", 0..10)
            y = intVar("y", 0..10)
        }

        val stages = model.lexicographic {
            primary(Sense.MINIMIZE) { x + y }
            secondary(Sense.MAXIMIZE) { x }
        }

        val res = model.solveLexicographic(stages) { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        // Secondary stage objective = maximize x under x+y = 0. Only (0, 0)
        // feasible, so x = 0.
        res.objective shouldBe 0L
        res.values[x] shouldBe 0L
        res.values[y] shouldBe 0L
    }

    "three-stage lex: primary then tie-break twice" {
        lateinit var x: IntVar
        lateinit var y: IntVar
        lateinit var z: IntVar
        val model = cpModel {
            x = intVar("x", 0..10)
            y = intVar("y", 0..10)
            z = intVar("z", 0..10)
            constraint { +(x + y + z eq 6L) }
        }
        val stages = model.lexicographic {
            primary(Sense.MINIMIZE) { x }        // force x to its min
            stage(Sense.MINIMIZE) { y }          // then y to its min
            secondary(Sense.MINIMIZE) { z }      // then z to its min
        }
        val res = model.solveLexicographic(stages) { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        // Under x+y+z=6 and minimizing x then y then z in order:
        // x=0, then y=0, then z=6.
        res.values[x] shouldBe 0L
        res.values[y] shouldBe 0L
        res.values[z] shouldBe 6L
    }
})
