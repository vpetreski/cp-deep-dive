package io.vanja.cpsat.ch04

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class NQueensSpec : StringSpec({
    "4-queens returns a valid placement (one of the two solutions)" {
        val r = solveNQueens(4)
        val cols = checkNotNull(r.columns)
        cols shouldHaveSize 4
        isValidBoard(cols) shouldBe true
        // 4-queens has exactly two solutions: (1,3,0,2) and (2,0,3,1).
        (cols in listOf(listOf(1L, 3L, 0L, 2L), listOf(2L, 0L, 3L, 1L))) shouldBe true
    }

    "8-queens returns a valid placement" {
        val r = solveNQueens(8)
        val cols = checkNotNull(r.columns)
        cols shouldHaveSize 8
        isValidBoard(cols) shouldBe true
    }

    "12-queens returns a valid placement" {
        val r = solveNQueens(12)
        val cols = checkNotNull(r.columns)
        cols shouldHaveSize 12
        isValidBoard(cols) shouldBe true
    }

    "renderBoard prints n rows of n chars each" {
        val cols = listOf(1L, 3L, 0L, 2L)
        val out = renderBoard(cols)
        val lines = out.trimEnd('\n').split('\n')
        lines shouldHaveSize 4
        lines.forEach { line -> (line.replace(" ", "").length) shouldBe 4 }
    }
})
