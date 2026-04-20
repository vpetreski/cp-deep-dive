package io.vanja.cpsat

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * N-queens via CP-SAT. Models `q[i] = column of queen on row i`, then uses
 * AllDifferent on `q`, `q + i` (anti-diagonals), and `q - i` (diagonals).
 *
 * Known: there are 2 solutions for n=4 and 92 for n=8; the solver returns
 * one. We verify the returned assignment is a valid placement.
 */
class NQueensSpec : StringSpec({

    "4-queens has a valid solution" {
        val n = 4
        lateinit var q: List<IntVar>
        val model = cpModel {
            q = intVarList("q", n, 0 until n)
            allDifferent(q)
            allDifferent(q.mapIndexed { i, v -> v + i.toLong() })
            allDifferent(q.mapIndexed { i, v -> v - i.toLong() })
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()

        val cols = q.map { res.values[it] }
        cols shouldHaveSize n
        cols.toSet().size shouldBe n
        cols.mapIndexed { i, c -> c + i }.toSet().size shouldBe n
        cols.mapIndexed { i, c -> c - i }.toSet().size shouldBe n
    }

    "8-queens has a valid solution" {
        val n = 8
        lateinit var q: List<IntVar>
        val model = cpModel {
            q = intVarList("q", n, 0 until n)
            allDifferent(q)
            allDifferent(q.mapIndexed { i, v -> v + i.toLong() })
            allDifferent(q.mapIndexed { i, v -> v - i.toLong() })
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()

        val cols = q.map { res.values[it] }
        cols shouldHaveSize n
        cols.toSet().size shouldBe n
        cols.mapIndexed { i, c -> c + i }.toSet().size shouldBe n
        cols.mapIndexed { i, c -> c - i }.toSet().size shouldBe n
    }
})
