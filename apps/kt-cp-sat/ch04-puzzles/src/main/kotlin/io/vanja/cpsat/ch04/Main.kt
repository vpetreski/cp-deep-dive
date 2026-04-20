package io.vanja.cpsat.ch04

import kotlin.system.measureTimeMillis

/**
 * Chapter 4 demo: solve three classic CP puzzles end-to-end with cpsat-kt.
 *   1. N-Queens (small scalability trip from n=4..16).
 *   2. SEND + MORE = MONEY.
 *   3. A classic 9x9 Sudoku.
 *
 * Run with:
 *   ./gradlew :ch04-puzzles:run
 */
fun main() {
    section("1. N-Queens") {
        for (n in listOf(4, 8, 12, 16)) {
            val ms = measureTimeMillis {
                val res = solveNQueens(n)
                check(res.columns != null) { "no solution for n=$n" }
                check(isValidBoard(res.columns)) { "invalid board for n=$n: ${res.columns}" }
                println("  n=$n -> columns=${res.columns}")
                if (n <= 8) print(renderBoard(res.columns).prependIndent("    "))
            }
            println("  (solved in ${ms}ms)")
            println()
        }
    }

    section("2. SEND + MORE = MONEY") {
        val ms = measureTimeMillis {
            val sol = solveSendMoreMoney()
                ?: error("SEND+MORE=MONEY returned no solution — unexpected.")
            check(sol.send + sol.more == sol.money) {
                "arithmetic failed: ${sol.send} + ${sol.more} != ${sol.money}"
            }
            print(renderSendMoreMoney(sol))
        }
        println("  (solved in ${ms}ms)")
        println()
    }

    section("3. Classic Sudoku") {
        val ms = measureTimeMillis {
            val solved = solveSudoku(CLASSIC_SUDOKU)
                ?: error("Sudoku has no solution — clue grid is wrong.")
            print(renderSudoku(solved))
        }
        println("  (solved in ${ms}ms)")
    }
}

private inline fun section(title: String, block: () -> Unit) {
    println("=== $title ===")
    block()
}
