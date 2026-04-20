package io.vanja.cpsat.ch06

import io.vanja.cpsat.*

/**
 * Inverse — couple a permutation with its inverse.
 *
 * If `assign[i] = j` means "nurse `i` is assigned to shift `j`", then
 * `shiftOf[j] = i` means "shift `j` is covered by nurse `i`". The Inverse
 * constraint keeps the two arrays in lock-step: `assign[i] = j ⇔ shiftOf[j] = i`.
 *
 * Demo: assign 5 nurses to 5 shifts, one preferred forbidden pair
 * (nurse 0 refuses shift 4), and pin nurse 2 to shift 1. The solver returns a
 * permutation where both arrays are consistent.
 */
public data class InverseSolution(
    val assign: List<Int>,   // assign[i] = j
    val shiftOf: List<Int>,  // shiftOf[j] = i
)

public fun solveInverseDemo(
    n: Int = 5,
    forbiddenPairs: List<Pair<Int, Int>> = listOf(0 to 4),
    pinAssignments: Map<Int, Int> = mapOf(2 to 1),
    seed: Int = 42,
    timeLimitS: Double = 5.0,
): InverseSolution? {
    require(n >= 1)
    for ((i, j) in forbiddenPairs) {
        require(i in 0 until n && j in 0 until n) { "forbiddenPair out of range: $i -> $j" }
    }
    for ((i, j) in pinAssignments) {
        require(i in 0 until n && j in 0 until n) { "pinAssignment out of range: $i -> $j" }
    }

    lateinit var assign: List<IntVar>
    lateinit var shiftOf: List<IntVar>
    val model = cpModel {
        assign = intVarList("assign", n, 0..(n - 1))
        shiftOf = intVarList("shiftOf", n, 0..(n - 1))
        inverse(assign, shiftOf)
        // Forbid specific (nurse, shift) pairs.
        for ((i, j) in forbiddenPairs) {
            constraint { +(assign[i] neq j.toLong()) }
        }
        // Pin specific assignments.
        for ((i, j) in pinAssignments) {
            constraint { +(assign[i] eq j.toLong()) }
        }
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
    return InverseSolution(
        assign = assign.map { assignment[it].toInt() },
        shiftOf = shiftOf.map { assignment[it].toInt() },
    )
}
