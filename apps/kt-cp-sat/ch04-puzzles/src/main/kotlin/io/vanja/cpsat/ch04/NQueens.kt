package io.vanja.cpsat.ch04

import io.vanja.cpsat.*

/**
 * N-queens via the classic three-AllDifferent formulation.
 *
 *   q[i] in {0, ..., n-1} is the row of the queen in column i.
 *   AllDifferent(q)                -> no two queens share a row
 *   AllDifferent(q[i] + i ∀ i)     -> no two queens share an anti-diagonal (/)
 *   AllDifferent(q[i] - i ∀ i)     -> no two queens share a diagonal (\)
 *
 * Known counts: n=4 -> 2 solutions, n=8 -> 92, n=12 -> 14200.
 */

/** Result of solving a single instance. */
public data class NQueensResult(
    val n: Int,
    val columns: List<Long>?, // queens' row per column, or null if no solution
)

/** Build the model and return the first solution (if any). */
public fun solveNQueens(n: Int, seed: Int = 42, timeLimitS: Double = 30.0): NQueensResult {
    require(n >= 1) { "n must be >= 1" }
    lateinit var q: List<IntVar>
    val model = cpModel {
        q = intVarList("q", n, 0 until n)
        allDifferent(q)
        allDifferent(q.mapIndexed { i, v -> v + i.toLong() })
        allDifferent(q.mapIndexed { i, v -> v - i.toLong() })
    }
    val res = model.solveBlocking {
        randomSeed = seed
        maxTimeInSeconds = timeLimitS
    }
    return when (res) {
        is SolveResult.Optimal -> NQueensResult(n, q.map { res.values[it] })
        is SolveResult.Feasible -> NQueensResult(n, q.map { res.values[it] })
        else -> NQueensResult(n, null)
    }
}

/**
 * Pretty-print an N-queens board given column-by-column queen rows.
 * `Q` marks a queen, `.` marks an empty square.
 */
public fun renderBoard(cols: List<Long>): String {
    val n = cols.size
    val sb = StringBuilder()
    for (row in 0 until n) {
        for (col in 0 until n) {
            sb.append(if (cols[col].toInt() == row) "Q " else ". ")
        }
        sb.append('\n')
    }
    return sb.toString()
}

/** Sanity check: return `true` iff the placement has no two queens attacking. */
public fun isValidBoard(cols: List<Long>): Boolean {
    val n = cols.size
    val rows = cols.toSet()
    if (rows.size != n) return false
    val antiDiag = cols.mapIndexed { i, c -> c + i }.toSet()
    val diag = cols.mapIndexed { i, c -> c - i }.toSet()
    return antiDiag.size == n && diag.size == n
}
