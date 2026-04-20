package io.vanja.cpsat.ch06

import io.vanja.cpsat.*

/**
 * AllDifferent — the workhorse global constraint.
 *
 * Ask CP-SAT to pick `n` distinct values from a shared domain and (optionally)
 * satisfy a simple linear property — here: the values must sum to a target.
 *
 * Why a global? You *could* post `n*(n-1)/2` pairwise `x_i != x_j` constraints,
 * but AllDifferent ships a far stronger propagator (matching-based) and keeps
 * the model human-readable. This is chapter 6's motivating example.
 */
public data class AllDifferentSolution(
    val values: List<Long>,
    val sum: Long,
)

/**
 * Find `n` distinct integers in `[0, maxVal]` that sum to [targetSum].
 *
 * Returns `null` when no such assignment exists.
 */
public fun solveAllDifferentSum(
    n: Int,
    maxVal: Int,
    targetSum: Long,
    seed: Int = 42,
    timeLimitS: Double = 10.0,
): AllDifferentSolution? {
    require(n >= 1) { "n must be >= 1" }
    require(maxVal >= n - 1) { "domain 0..$maxVal is too small for $n distinct values" }

    lateinit var xs: List<IntVar>
    val model = cpModel {
        xs = intVarList("x", n, 0..maxVal)
        allDifferent(xs)
        // Break the symmetry that all permutations of a solution are equally valid.
        for (i in 0 until n - 1) {
            constraint { +(xs[i] lt xs[i + 1]) }
        }
        constraint { +(weightedSum(xs, List(n) { 1L }) eq targetSum) }
    }
    val res = model.solveBlocking {
        randomSeed = seed
        maxTimeInSeconds = timeLimitS
    }
    val values = when (res) {
        is SolveResult.Optimal -> xs.map { res.values[it] }
        is SolveResult.Feasible -> xs.map { res.values[it] }
        else -> return null
    }
    return AllDifferentSolution(values, values.sum())
}
