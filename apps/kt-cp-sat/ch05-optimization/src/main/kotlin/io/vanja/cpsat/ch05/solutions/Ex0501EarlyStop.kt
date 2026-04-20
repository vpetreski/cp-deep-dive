package io.vanja.cpsat.ch05.solutions

import io.vanja.cpsat.ch05.*
import io.vanja.cpsat.*
import kotlin.system.measureTimeMillis

/**
 * Exercise 5.1 — early stopping via gap/time limits.
 *
 * Compare three solver configurations on the demo knapsack:
 *   A) prove optimality (no gap limit)
 *   B) stop at 5% gap — relativeGapLimit = 0.05
 *   C) stop after 0.2 s — maxTimeInSeconds = 0.2
 *
 * Report wall time and final gap for each.
 */
fun main() {
    val items = DEMO_ITEMS
    val cap = DEMO_CAPACITY

    runOne("A prove-optimal") {
        solveKnapsack(items, cap, timeLimitS = 10.0)
    }
    runOne("B 5% gap limit") {
        solveKnapsackGapped(items, cap, relGap = 0.05)
    }
    runOne("C 0.2s time limit") {
        solveKnapsack(items, cap, timeLimitS = 0.2)
    }
}

private inline fun runOne(label: String, block: () -> KnapsackResult) {
    val ms = measureTimeMillis {
        val r = block()
        println("$label: status=${r.status} value=${r.value} bound=${r.bound} gap=${r.gap?.let { "%.3f".format(it) }}")
    }
    println("  wall=${ms}ms")
}

private fun solveKnapsackGapped(items: List<Item>, capacity: Long, relGap: Double): KnapsackResult {
    lateinit var xs: List<BoolVar>
    val model = cpModel {
        xs = boolVarList("x", items.size)
        constraint {
            +(weightedSum(xs.map { it as IntVar }, items.map { it.weight }) le capacity)
        }
        maximize { weightedSum(xs.map { it as IntVar }, items.map { it.value }) }
    }
    val res = model.solveBlocking {
        randomSeed = 42
        maxTimeInSeconds = 10.0
        numSearchWorkers = 4
        rawProto { relativeGapLimit = relGap }
    }
    return when (res) {
        is SolveResult.Optimal -> KnapsackResult(
            "OPTIMAL", res.objective, res.objective,
            items.zip(xs).filter { (_, x) -> res.values[x] }.map { it.first.name }, 0.0,
        )
        is SolveResult.Feasible -> KnapsackResult(
            "FEASIBLE", res.objective, res.bound,
            items.zip(xs).filter { (_, x) -> res.values[x] }.map { it.first.name }, res.gap,
        )
        else -> KnapsackResult(res.javaClass.simpleName, null, null, emptyList(), null)
    }
}
