package io.vanja.cpsat.ch04.solutions

import io.vanja.cpsat.*
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.runBlocking

/**
 * Exercise 4.5 — measure the effect of a simple symmetry-breaker.
 *
 * Break reflection symmetry with `q[0] < q[n-1]`. Enumerate all N-queens
 * solutions for `n=8` with and without the breaker and report counts + time.
 *
 * Expected numbers (cheat sheet — do not memorize; re-run and learn):
 *   n=8 without breaker: 92 solutions
 *   n=8 with breaker:   ≈46 (exactly half — a single reflection kills each pair)
 */
fun main(): Unit = runBlocking {
    val n = 8
    runVariant("no symmetry breaker", n, breakSymmetry = false)
    runVariant("with q[0] < q[n-1]", n, breakSymmetry = true)
}

private suspend fun runVariant(label: String, n: Int, breakSymmetry: Boolean) {
    lateinit var q: List<IntVar>
    val model = cpModel {
        q = intVarList("q", n, 0 until n)
        allDifferent(q)
        allDifferent(q.mapIndexed { i, v -> v + i.toLong() })
        allDifferent(q.mapIndexed { i, v -> v - i.toLong() })
        if (breakSymmetry) constraint { +(q[0] lt q[n - 1]) }
    }

    // Count by streaming solutions via solveFlow; stop when the solver is done.
    var count = 0L
    val ms = measureTimeMillis {
        count = model.solveFlow {
            maxSolutions = Int.MAX_VALUE
            rawProto { enumerateAllSolutions = true }
        }.count().toLong()
    }
    println("$label: count=$count in ${ms}ms")
}

@Suppress("UNUSED") // Kept for reference: the "raw" callback-free approach using the convenience helper.
private suspend fun alternativeCount(n: Int): Int {
    // Note: enumerateSolutions doesn't expose variable readings; use solveFlow in real code.
    val model = cpModel {
        val q = intVarList("q", n, 0 until n)
        allDifferent(q)
        allDifferent(q.mapIndexed { i, v -> v + i.toLong() })
        allDifferent(q.mapIndexed { i, v -> v - i.toLong() })
    }
    return model.enumerateSolutions(maxSolutions = 200).size
}
