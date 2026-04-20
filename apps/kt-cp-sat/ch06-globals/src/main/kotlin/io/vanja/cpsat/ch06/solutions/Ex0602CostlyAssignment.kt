package io.vanja.cpsat.ch06.solutions

import io.vanja.cpsat.*

/**
 * Exercise 6.2 — minimum-cost nurse-to-shift assignment.
 *
 * Uses Inverse + Element to pose a standard 1-to-1 assignment problem and
 * minimize the total cost. `assign[i] = j` says nurse `i` works shift `j`.
 * `costOf[i]` pulls that nurse's cost for the picked shift via Element:
 *
 *     costOf[i] = costs[i][assign[i]]
 *
 * The objective is `sum(costOf[i])`.
 */

private val COSTS: List<List<Long>> = listOf(
    //          s0  s1  s2  s3  s4
    listOf(    9L, 2L, 7L, 8L, 3L), // nurse 0
    listOf(    6L, 4L, 3L, 7L, 5L), // nurse 1
    listOf(    5L, 8L, 1L, 8L, 6L), // nurse 2
    listOf(    7L, 6L, 9L, 4L, 2L), // nurse 3
    listOf(    2L, 5L, 4L, 3L, 9L), // nurse 4
)

fun main() {
    val n = COSTS.size
    require(COSTS.all { it.size == n }) { "costs must be $n x $n" }

    lateinit var assign: List<IntVar>
    lateinit var shiftOf: List<IntVar>
    lateinit var costOf: List<IntVar>

    val model = cpModel {
        assign = intVarList("assign", n, 0..(n - 1))
        shiftOf = intVarList("shiftOf", n, 0..(n - 1))
        inverse(assign, shiftOf)

        costOf = (0 until n).map { i ->
            val row = COSTS[i].toLongArray()
            val lo = row.min()
            val hi = row.max()
            val c = intVar("cost$i", lo..hi)
            element(assign[i], row, c)
            c
        }
        minimize { weightedSum(costOf, List(n) { 1L }) }
    }

    val res = model.solveBlocking {
        randomSeed = 42
        numSearchWorkers = 4
        maxTimeInSeconds = 10.0
    }

    when (res) {
        is SolveResult.Optimal -> {
            val a = assign.map { res.values[it].toInt() }
            val c = costOf.map { res.values[it] }
            println("Optimal total cost: ${res.objective}")
            for (i in 0 until n) {
                println("  nurse $i -> shift ${a[i]}  (cost ${c[i]})")
            }
        }
        is SolveResult.Feasible -> println("Feasible (not proven optimal): objective=${res.objective}")
        else -> println("Solver returned $res")
    }
}
