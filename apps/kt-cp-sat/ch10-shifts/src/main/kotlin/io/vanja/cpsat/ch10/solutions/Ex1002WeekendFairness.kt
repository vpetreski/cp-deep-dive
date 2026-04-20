package io.vanja.cpsat.ch10.solutions

import io.vanja.cpsat.ch10.*
import io.vanja.cpsat.*

/**
 * Exercise 10.2 — weekend fairness.
 *
 * Real rosters care deeply about who gets Saturday and Sunday. We minimize the
 * weekend "spread": difference between the nurse who works the most weekend
 * shifts and the nurse who works the fewest.
 *
 * Combined objective: 100 * weekendSpread + totalSpread — the weight favors
 * weekend fairness but still cares about total fairness as a tiebreaker.
 *
 * Days are 0..6 (Mon..Sun). Saturday = day 5, Sunday = day 6.
 */
fun main() {
    val instance = DEMO_SHIFTS   // 5 nurses × 7 days × 2 shifts
    val weekendDays = setOf(5, 6)

    val N = instance.nNurses
    val D = instance.nDays
    val shifts = Shift.entries
    val S = shifts.size

    lateinit var x: Array<Array<Array<BoolVar>>>
    lateinit var totals: List<IntVar>
    lateinit var weekendTotals: List<IntVar>
    lateinit var totalSpread: IntVar
    lateinit var weekendSpread: IntVar

    val model = cpModel {
        x = Array(N) { n ->
            Array(D) { d ->
                Array(S) { s -> boolVar("x_${n}_${d}_${shifts[s].label}") }
            }
        }

        for (d in 0 until D) {
            for (s in 0 until S) {
                val column = (0 until N).map { n -> x[n][d][s] as IntVar }
                constraint {
                    +(weightedSum(column, List(N) { 1L }) ge instance.coverage.toLong())
                }
            }
        }

        for (n in 0 until N) {
            for (d in 0 until D) {
                atMostOne((0 until S).map { s -> x[n][d][s] })
            }
        }

        val nightIdx = Shift.NIGHT.ordinal
        val dayIdx = Shift.DAY.ordinal
        for (n in 0 until N) {
            for (d in 0 until D - 1) {
                val pair = listOf(x[n][d][nightIdx] as IntVar, x[n][d + 1][dayIdx] as IntVar)
                constraint { +(weightedSum(pair, listOf(1L, 1L)) le 1L) }
            }
        }

        // Totals and workload.
        totals = (0 until N).map { n -> intVar("total_$n", 0..(D * S)) }
        for (n in 0 until N) {
            val flat = (0 until D).flatMap { d -> (0 until S).map { s -> x[n][d][s] as IntVar } }
            constraint { +(weightedSum(flat, List(flat.size) { 1L }) eq totals[n]) }
            constraint { +(totals[n] ge instance.minWork.toLong()) }
            constraint { +(totals[n] le instance.maxWork.toLong()) }
        }

        // Weekend totals.
        weekendTotals = (0 until N).map { n -> intVar("wkend_$n", 0..(weekendDays.size * S)) }
        for (n in 0 until N) {
            val wkend = weekendDays.toSortedSet().flatMap { d -> (0 until S).map { s -> x[n][d][s] as IntVar } }
            constraint { +(weightedSum(wkend, List(wkend.size) { 1L }) eq weekendTotals[n]) }
        }

        // Spread aux pairs.
        val maxTot = intVar("maxTot", 0..(D * S))
        val minTot = intVar("minTot", 0..(D * S))
        for (t in totals) {
            constraint { +(t le maxTot) }
            constraint { +(t ge minTot) }
        }
        totalSpread = intVar("totalSpread", 0..(D * S))
        constraint { +(totalSpread eq (maxTot - minTot)) }

        val maxWk = intVar("maxWk", 0..(weekendDays.size * S))
        val minWk = intVar("minWk", 0..(weekendDays.size * S))
        for (t in weekendTotals) {
            constraint { +(t le maxWk) }
            constraint { +(t ge minWk) }
        }
        weekendSpread = intVar("weekendSpread", 0..(weekendDays.size * S))
        constraint { +(weekendSpread eq (maxWk - minWk)) }

        // Lexicographic: weight weekend fairness heavily, break ties with total spread.
        minimize { weekendSpread * 100L + totalSpread }
    }

    val res = model.solveBlocking {
        randomSeed = 42
        maxTimeInSeconds = 10.0
        numSearchWorkers = 4
    }

    when (res) {
        is SolveResult.Optimal, is SolveResult.Feasible -> {
            val label = if (res is SolveResult.Optimal) "OPTIMAL" else "FEASIBLE"
            val assignment = if (res is SolveResult.Optimal) res.values else (res as SolveResult.Feasible).values
            val obj = if (res is SolveResult.Optimal) res.objective else (res as SolveResult.Feasible).objective
            println("Ex 10.2 ($label)")
            println("  composite objective = $obj")
            println("  weekendSpread = ${assignment[weekendSpread]}   totalSpread = ${assignment[totalSpread]}")
            println("  totals per nurse        = ${totals.map { assignment[it].toInt() }}")
            println("  weekend totals per nurse = ${weekendTotals.map { assignment[it].toInt() }}")
        }
        else -> println("Ex 10.2 solver returned $res")
    }
}
