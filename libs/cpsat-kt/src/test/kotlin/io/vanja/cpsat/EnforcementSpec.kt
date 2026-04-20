package io.vanja.cpsat

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Reification / enforcement tests: `enforceIf`, `enforceIfAll`, `enforceIfAny`,
 * plus direct `onlyEnforceIf` via ConstraintBuilder's enforcer list.
 */
class EnforcementSpec : StringSpec({

    "enforceIf activates constraint only when the bool is true" {
        lateinit var b: BoolVar
        lateinit var x: IntVar
        val model = cpModel {
            b = boolVar("b")
            x = intVar("x", 0..10)

            enforceIf(b) {
                +(x eq 5L)
            }

            // Force b true → x must be 5.
            constraint { +(b eq 1L) }
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        res.values[x] shouldBe 5L
    }

    "enforceIf leaves x free when bool is false" {
        lateinit var b: BoolVar
        lateinit var x: IntVar
        val model = cpModel {
            b = boolVar("b")
            x = intVar("x", 0..10)

            enforceIf(b) {
                +(x eq 5L)
            }

            constraint {
                +(b eq 0L)
                +(x eq 3L)  // free to pick whatever we want when b is off
            }
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        res.values[x] shouldBe 3L
    }

    "enforceIfAll requires all bools true" {
        lateinit var b1: BoolVar
        lateinit var b2: BoolVar
        lateinit var x: IntVar
        val model = cpModel {
            b1 = boolVar("b1")
            b2 = boolVar("b2")
            x = intVar("x", 0..10)

            enforceIfAll(listOf(b1, b2)) {
                +(x eq 7L)
            }

            constraint {
                +(b1 eq 1L)
                +(b2 eq 1L)
            }
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        res.values[x] shouldBe 7L
    }

    "enforceIfAny activates when at least one bool is true" {
        lateinit var b1: BoolVar
        lateinit var b2: BoolVar
        lateinit var x: IntVar
        val model = cpModel {
            b1 = boolVar("b1")
            b2 = boolVar("b2")
            x = intVar("x", 0..10)

            enforceIfAny(listOf(b1, b2)) {
                +(x eq 9L)
            }

            constraint {
                +(b1 eq 0L)
                +(b2 eq 1L)   // just one
            }
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        res.values[x] shouldBe 9L
    }

    "enforceIfAny stays inactive when all bools are false" {
        lateinit var b1: BoolVar
        lateinit var b2: BoolVar
        lateinit var x: IntVar
        val model = cpModel {
            b1 = boolVar("b1")
            b2 = boolVar("b2")
            x = intVar("x", 0..10)

            enforceIfAny(listOf(b1, b2)) {
                +(x eq 9L)
            }

            constraint {
                +(b1 eq 0L)
                +(b2 eq 0L)
                +(x eq 2L)   // ignore the enforced equality
            }
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        res.values[x] shouldBe 2L
    }

    "boolean implication implies(a, b) requires b when a holds" {
        lateinit var a: BoolVar
        lateinit var b: BoolVar
        val model = cpModel {
            a = boolVar("a")
            b = boolVar("b")
            implies(a, b)
            constraint { +(a eq 1L) }
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        res.values[b] shouldBe true
    }
})
