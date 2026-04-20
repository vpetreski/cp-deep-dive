package io.vanja.cpsat

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * 0/1 knapsack via CP-SAT:
 *   maximize Σ value[i] * x[i]
 *   subject to Σ weight[i] * x[i] ≤ capacity
 *   x[i] ∈ {0, 1}
 *
 * Tiny hand-verified instance:
 *   weights  = [2, 3, 4, 5]
 *   values   = [3, 4, 5, 6]
 *   capacity = 5
 *
 * Enumerating subsets with weight ≤ 5:
 *   {0,1}     weight 5, value 7   <- optimum
 *   {0,2}     weight 6 — infeasible
 *   {0,3}     weight 7 — infeasible
 *   {1,2}     weight 7 — infeasible
 *   {1,3}     weight 8 — infeasible
 *   {2}       weight 4, value 5
 *   {3}       weight 5, value 6
 *   {0}       weight 2, value 3
 *   {0,1,2}   weight 9 — infeasible
 *   {0,1,3}   weight 10 — infeasible
 *   ... all bigger subsets infeasible.
 *
 * Optimum value = 7, picking items 0 and 1.
 */
class KnapsackSpec : StringSpec({

    "0/1 knapsack finds the expected optimum" {
        val weights = listOf(2L, 3L, 4L, 5L)
        val values = listOf(3L, 4L, 5L, 6L)
        val capacity = 5L

        lateinit var xs: List<BoolVar>
        val model = cpModel {
            xs = boolVarList("x", weights.size)
            constraint {
                +(weightedSum(xs.map { it as IntVar }, weights) le capacity)
            }
            maximize { weightedSum(xs.map { it as IntVar }, values) }
        }

        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        res.objective shouldBe 7L

        res.values[xs[0]] shouldBe true
        res.values[xs[1]] shouldBe true
        res.values[xs[2]] shouldBe false
        res.values[xs[3]] shouldBe false
    }

    "capacity 0 forces all-zeros solution" {
        lateinit var xs: List<BoolVar>
        val model = cpModel {
            xs = boolVarList("x", 3)
            constraint {
                +(weightedSum(xs.map { it as IntVar }, listOf(1L, 1L, 1L)) le 0L)
            }
            maximize { weightedSum(xs.map { it as IntVar }, listOf(10L, 20L, 30L)) }
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        res.objective shouldBe 0L
        xs.forEach { res.values[it] shouldBe false }
    }
})
