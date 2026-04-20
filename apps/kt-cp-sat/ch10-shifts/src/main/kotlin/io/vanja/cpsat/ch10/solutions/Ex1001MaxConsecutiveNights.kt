package io.vanja.cpsat.ch10.solutions

import io.vanja.cpsat.ch10.*
import io.vanja.cpsat.*

/**
 * Exercise 10.1 — cap the number of consecutive NIGHT shifts any nurse can
 * take. Most real rosters treat "3 nights in a row" as the upper limit; the
 * fourth is either forbidden or triggers mandatory rest.
 *
 * We add a sliding-window constraint:
 *   for every window of length (maxConsecutive + 1) days,
 *   sum of x[n, d..d+k, NIGHT]  ≤  maxConsecutive.
 *
 * Keeps every other constraint from the base model.
 */
fun main() {
    val instance = DEMO_SHIFTS
    val maxConsecutiveNights = 2

    val N = instance.nNurses
    val D = instance.nDays
    val shifts = Shift.entries
    val S = shifts.size

    lateinit var x: Array<Array<Array<BoolVar>>>
    lateinit var totals: List<IntVar>
    lateinit var spread: IntVar

    val model = cpModel {
        x = Array(N) { n ->
            Array(D) { d ->
                Array(S) { s -> boolVar("x_${n}_${d}_${shifts[s].label}") }
            }
        }

        // Coverage.
        for (d in 0 until D) {
            for (s in 0 until S) {
                val column = (0 until N).map { n -> x[n][d][s] as IntVar }
                constraint {
                    +(weightedSum(column, List(N) { 1L }) ge instance.coverage.toLong())
                }
            }
        }

        // At most one shift per nurse per day.
        for (n in 0 until N) {
            for (d in 0 until D) {
                atMostOne((0 until S).map { s -> x[n][d][s] })
            }
        }

        // Night -> Day transition forbidden.
        val nightIdx = Shift.NIGHT.ordinal
        val dayIdx = Shift.DAY.ordinal
        for (n in 0 until N) {
            for (d in 0 until D - 1) {
                val pair = listOf(x[n][d][nightIdx] as IntVar, x[n][d + 1][dayIdx] as IntVar)
                constraint { +(weightedSum(pair, listOf(1L, 1L)) le 1L) }
            }
        }

        // Sliding window: no nurse works more than `maxConsecutiveNights` nights in a row.
        val window = maxConsecutiveNights + 1
        if (D >= window) {
            for (n in 0 until N) {
                for (d in 0..D - window) {
                    val block = (0 until window).map { k -> x[n][d + k][nightIdx] as IntVar }
                    constraint {
                        +(weightedSum(block, List(window) { 1L }) le maxConsecutiveNights.toLong())
                    }
                }
            }
        }

        // Workload.
        totals = (0 until N).map { n -> intVar("total_$n", 0..(D * S)) }
        for (n in 0 until N) {
            val flat = (0 until D).flatMap { d -> (0 until S).map { s -> x[n][d][s] as IntVar } }
            constraint { +(weightedSum(flat, List(flat.size) { 1L }) eq totals[n]) }
            constraint { +(totals[n] ge instance.minWork.toLong()) }
            constraint { +(totals[n] le instance.maxWork.toLong()) }
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
        is SolveResult.Optimal, is SolveResult.Feasible -> {
            val label = if (res is SolveResult.Optimal) "OPTIMAL" else "FEASIBLE"
            val assignment = if (res is SolveResult.Optimal) res.values else (res as SolveResult.Feasible).values
            val spreadVal = if (res is SolveResult.Optimal) res.objective else (res as SolveResult.Feasible).objective
            println("Ex 10.1 ($label) — max consecutive nights = $maxConsecutiveNights   spread=$spreadVal")
            val grid = Array(N) { Array(D) { "." } }
            for (n in 0 until N) {
                for (d in 0 until D) {
                    for (s in 0 until S) {
                        if (assignment[x[n][d][s]]) grid[n][d] = shifts[s].label
                    }
                }
            }
            print("nurse ")
            for (d in 0 until D) print(" d$d")
            println()
            for (n in 0 until N) {
                print("  $n   ")
                for (d in 0 until D) print("  ${grid[n][d]}")
                println()
            }
            // Sanity check on the printed grid.
            for (n in 0 until N) {
                var run = 0
                for (d in 0 until D) {
                    run = if (grid[n][d] == "N") run + 1 else 0
                    check(run <= maxConsecutiveNights) {
                        "Nurse $n violated night cap at day $d (run=$run)"
                    }
                }
            }
            println("  (all nurses respect the $maxConsecutiveNights-consecutive-night cap)")
        }
        else -> println("Ex 10.1 solver returned $res")
    }
}
