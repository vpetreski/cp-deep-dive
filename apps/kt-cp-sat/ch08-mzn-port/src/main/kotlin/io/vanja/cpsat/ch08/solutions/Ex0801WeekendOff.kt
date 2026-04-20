package io.vanja.cpsat.ch08.solutions

import io.vanja.cpsat.ch08.*
import io.vanja.cpsat.*

/**
 * Exercise 8.1 — extend the toy NSP with a weekend-off preference.
 *
 * Add an extra constraint: day 5 and day 6 (the "weekend") must be covered by
 * at most two nurses each across both shifts. That is, the total workload on
 * the weekend is bounded, forcing a more uniform distribution.
 *
 * We rebuild the model inline rather than refactoring `ToyNsp.kt` — the
 * exercise keeps the base model pristine.
 */
fun main() {
    val instance = DEMO_TOY_NSP
    val N = instance.nNurses
    val D = instance.nDays
    val S = instance.nShifts
    val WEEKEND_DAYS = listOf(5, 6)
    val WEEKEND_PER_NURSE_LIMIT = 2L   // at most 2 weekend slots per nurse

    lateinit var work: Array<Array<Array<BoolVar>>>
    lateinit var totals: List<IntVar>
    lateinit var spread: IntVar

    val model = cpModel {
        work = Array(N) { n ->
            Array(D) { d ->
                Array(S) { s -> boolVar("work_${n}_${d}_$s") }
            }
        }
        // HC-1
        for (d in 0 until D) for (s in 0 until S) {
            exactlyOne((0 until N).map { n -> work[n][d][s] })
        }
        // HC-2
        for (n in 0 until N) for (d in 0 until D) {
            atMostOne((0 until S).map { s -> work[n][d][s] })
        }
        totals = (0 until N).map { n -> intVar("total_$n", 0..(D * S)) }
        for (n in 0 until N) {
            val flat = (0 until D).flatMap { d -> (0 until S).map { s -> work[n][d][s] as IntVar } }
            constraint { +(weightedSum(flat, List(flat.size) { 1L }) eq totals[n]) }
            constraint { +(totals[n] ge instance.minWork.toLong()) }
            constraint { +(totals[n] le instance.maxWork.toLong()) }
        }

        // Weekend-off: each nurse works at most WEEKEND_PER_NURSE_LIMIT slots on days 5,6.
        for (n in 0 until N) {
            val weekendSlots = WEEKEND_DAYS.flatMap { d ->
                (0 until S).map { s -> work[n][d][s] as IntVar }
            }
            constraint {
                +(weightedSum(weekendSlots, List(weekendSlots.size) { 1L }) le WEEKEND_PER_NURSE_LIMIT)
            }
        }

        val maxT = intVar("maxT", 0..(D * S))
        val minT = intVar("minT", 0..(D * S))
        for (t in totals) {
            constraint { +(t le maxT) }
            constraint { +(t ge minT) }
        }
        spread = intVar("spread", 0..(D * S))
        constraint { +(spread eq (maxT - minT)) }
        minimize { spread }
    }

    val res = model.solveBlocking {
        randomSeed = 42
        maxTimeInSeconds = 10.0
        numSearchWorkers = 4
    }

    when (res) {
        is SolveResult.Optimal -> {
            println("Ex 8.1 optimal spread with weekend cap $WEEKEND_PER_NURSE_LIMIT: ${res.objective}")
            val totalsVal = totals.map { res.values[it] }
            println("  totals=$totalsVal")
            for (n in 0 until N) {
                val wk = WEEKEND_DAYS.sumOf { d -> (0 until S).count { s -> res.values[work[n][d][s]] } }
                println("  nurse $n  weekend slots = $wk")
            }
        }
        is SolveResult.Feasible -> println("Ex 8.1 feasible (not proven optimal): spread=${res.objective}")
        else -> println("Ex 8.1 solver returned $res")
    }
}
