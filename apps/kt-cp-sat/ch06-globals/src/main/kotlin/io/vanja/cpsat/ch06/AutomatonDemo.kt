package io.vanja.cpsat.ch06

import io.vanja.cpsat.*

/**
 * Automaton — express a regular language constraint.
 *
 * Goal: decide for each of 14 days whether a nurse is "on night" (1) or "off"
 * (0). The only restriction is: **no 4 consecutive nights**. We encode that as
 * a deterministic finite automaton with four states:
 *
 *   - state 0: just saw 0 (or start)
 *   - state 1: saw 1 night in a row
 *   - state 2: saw 2 nights in a row
 *   - state 3: saw 3 nights in a row  (dead-end on another 1)
 *
 * Transitions:
 *   (0, 0) -> 0       (0, 1) -> 1
 *   (1, 0) -> 0       (1, 1) -> 2
 *   (2, 0) -> 0       (2, 1) -> 3
 *   (3, 0) -> 0       (3, 1) -> -        (no 4th consecutive night)
 *
 * All four states {0, 1, 2, 3} are accepting. We drop the (3, 1) edge — the
 * automaton has no transition out of state 3 on input 1, so the solver rules
 * that path out.
 *
 * To make the instance interesting we *also* pin the total number of nights
 * to a target with a linear constraint: the solver must find a valid layout
 * whose sum equals [targetNights].
 */
public data class AutomatonSchedule(val days: List<Int>) {
    val totalNights: Int get() = days.count { it == 1 }
    val maxRun: Int
        get() {
            var maxRun = 0
            var cur = 0
            for (d in days) {
                if (d == 1) { cur += 1; maxRun = maxOf(maxRun, cur) } else { cur = 0 }
            }
            return maxRun
        }
}

private val TRANSITIONS: List<Triple<Int, Long, Int>> = listOf(
    Triple(0, 0L, 0),
    Triple(0, 1L, 1),
    Triple(1, 0L, 0),
    Triple(1, 1L, 2),
    Triple(2, 0L, 0),
    Triple(2, 1L, 3),
    Triple(3, 0L, 0),
    // No (3, 1) — state 3 on input 1 is stuck, which is precisely "no 4 consecutive nights".
)

public fun solveNoFourNights(
    horizon: Int = 14,
    targetNights: Int = 7,
    seed: Int = 42,
    timeLimitS: Double = 5.0,
): AutomatonSchedule? {
    require(horizon >= 1)
    require(targetNights in 0..horizon)

    lateinit var xs: List<IntVar>
    val model = cpModel {
        xs = intVarList("day", horizon, 0..1)
        automaton(xs, startState = 0L, transitions = TRANSITIONS, finalStates = listOf(0, 1, 2, 3))
        constraint { +(weightedSum(xs, List(horizon) { 1L }) eq targetNights.toLong()) }
    }
    val res = model.solveBlocking {
        randomSeed = seed
        maxTimeInSeconds = timeLimitS
    }
    val assignment = when (res) {
        is SolveResult.Optimal -> res.values
        is SolveResult.Feasible -> res.values
        else -> return null
    }
    return AutomatonSchedule(xs.map { assignment[it].toInt() })
}
