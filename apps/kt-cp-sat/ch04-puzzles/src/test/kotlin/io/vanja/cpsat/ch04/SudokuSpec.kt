package io.vanja.cpsat.ch04

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class SudokuSpec : StringSpec({
    "classic Sudoku solves to a valid 9x9 grid" {
        val solved = solveSudoku(CLASSIC_SUDOKU)
        solved shouldNotBe null
        val g = solved!!
        g.size shouldBe 9
        g.all { it.size == 9 } shouldBe true

        // Every row, column, and 3x3 box contains digits 1..9 exactly once.
        val expected = (1..9).toList()
        for (r in 0 until 9) g[r].sorted() shouldContainExactlyInAnyOrder expected
        for (c in 0 until 9) (0 until 9).map { g[it][c] }.sorted() shouldContainExactlyInAnyOrder expected
        for (br in 0 until 3) for (bc in 0 until 3) {
            val box = mutableListOf<Int>()
            for (dr in 0 until 3) for (dc in 0 until 3) box.add(g[br * 3 + dr][bc * 3 + dc])
            box.sorted() shouldContainExactlyInAnyOrder expected
        }

        // Clues preserved.
        for (r in 0 until 9) for (c in 0 until 9) {
            val clue = CLASSIC_SUDOKU[r][c]
            if (clue != 0) g[r][c] shouldBe clue
        }
    }

    "parseSudoku accepts dots as empty" {
        val grid = parseSudoku(
            """
            . . . | . . . | . . .
            . . . | . . . | . . .
            . . . | . . . | . . .
            ------+-------+------
            . . . | . . . | . . .
            . . . | . . . | . . .
            . . . | . . . | . . .
            ------+-------+------
            . . . | . . . | . . .
            . . . | . . . | . . .
            . . . | . . . | . . .
            """
        )
        grid.flatten().all { it == 0 } shouldBe true
    }
})
