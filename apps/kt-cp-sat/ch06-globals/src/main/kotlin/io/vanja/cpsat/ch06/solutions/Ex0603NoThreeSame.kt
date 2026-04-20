package io.vanja.cpsat.ch06.solutions

import io.vanja.cpsat.ch06.*
import io.vanja.cpsat.*

/**
 * Exercise 6.3 — variant of the Automaton demo.
 *
 * Schedule 21 days, each marked as DAY (0), NIGHT (1), or OFF (2), with two
 * rules expressed via a single automaton:
 *   - no 3 consecutive DAYs
 *   - no 3 consecutive NIGHTs
 * OFFs reset both counters.
 *
 * Target: exactly 7 NIGHTs and exactly 7 OFFs (the remaining 7 are DAYs).
 *
 * Automaton states encode (lastKind, runLength). We use 5 states:
 *   0: start/OFF or just-saw-OFF
 *   1: 1 DAY in a row
 *   2: 2 DAYs in a row
 *   3: 1 NIGHT in a row
 *   4: 2 NIGHTs in a row
 * Transitions on inputs {0=day, 1=night, 2=off}.
 */
fun main() {
    val horizon = 21
    val targetNights = 7
    val targetOffs = 7

    val transitions = listOf<Triple<Int, Long, Int>>(
        // From state 0 (start/OFF)
        Triple(0, 0L, 1),  // day
        Triple(0, 1L, 3),  // night
        Triple(0, 2L, 0),  // off
        // From state 1 (1 day)
        Triple(1, 0L, 2),
        Triple(1, 1L, 3),
        Triple(1, 2L, 0),
        // From state 2 (2 days) — no third day allowed
        Triple(2, 1L, 3),
        Triple(2, 2L, 0),
        // From state 3 (1 night)
        Triple(3, 0L, 1),
        Triple(3, 1L, 4),
        Triple(3, 2L, 0),
        // From state 4 (2 nights) — no third night allowed
        Triple(4, 0L, 1),
        Triple(4, 2L, 0),
    )

    lateinit var day: List<IntVar>
    lateinit var isNight: List<BoolVar>
    lateinit var isOff: List<BoolVar>

    val model = cpModel {
        day = intVarList("day", horizon, 0..2)
        automaton(day, startState = 0L, transitions = transitions, finalStates = listOf(0, 1, 2, 3, 4))

        // Reify DAY/NIGHT/OFF counts with boolean indicators via channelEq.
        isNight = List(horizon) { i -> boolVar("night$i").also { channelEq(it, day[i], 1L) } }
        isOff = List(horizon) { i -> boolVar("off$i").also { channelEq(it, day[i], 2L) } }

        constraint { +(weightedSum(isNight.map { it as IntVar }, List(horizon) { 1L }) eq targetNights.toLong()) }
        constraint { +(weightedSum(isOff.map { it as IntVar }, List(horizon) { 1L }) eq targetOffs.toLong()) }
    }

    val res = model.solveBlocking {
        randomSeed = 42
        maxTimeInSeconds = 10.0
    }

    when (res) {
        is SolveResult.Optimal, is SolveResult.Feasible -> {
            val values = day.map {
                (res as? SolveResult.Optimal)?.values?.get(it)
                    ?: (res as SolveResult.Feasible).values[it]
            }
            val labels = values.map { listOf("D", "N", "O")[it.toInt()] }
            println("21-day schedule (D=day, N=night, O=off):")
            println("  ${labels.joinToString(" ")}")
            val counts = labels.groupingBy { it }.eachCount()
            println("  counts: $counts")
        }
        else -> println("Solver returned $res")
    }
}
