package io.vanja.cpsat.ch04

import io.vanja.cpsat.*

/**
 * Sudoku via three overlapping AllDifferent families:
 *   - each row contains 1..9 exactly once
 *   - each column contains 1..9 exactly once
 *   - each 3x3 box contains 1..9 exactly once
 *
 * Clues are modeled by fixing the corresponding cell to its clue value.
 */

/** A 9x9 Sudoku grid. `0` means "empty cell" in clues; `1..9` are clues or solutions. */
public typealias SudokuGrid = List<List<Int>>

public fun parseSudoku(text: String): SudokuGrid {
    val tokens = text.trim().split(Regex("[^0-9.]+"))
        .flatMap { tok -> tok.map { if (it == '.') 0 else it.digitToInt() } }
    require(tokens.size == 81) { "Expected 81 digits/dots, got ${tokens.size}" }
    return List(9) { r -> List(9) { c -> tokens[r * 9 + c] } }
}

public fun renderSudoku(grid: SudokuGrid): String = buildString {
    for (r in 0 until 9) {
        if (r % 3 == 0 && r != 0) appendLine("- - - + - - - + - - -")
        for (c in 0 until 9) {
            if (c % 3 == 0 && c != 0) append("| ")
            val v = grid[r][c]
            append(if (v == 0) ". " else "$v ")
        }
        appendLine()
    }
}

public fun solveSudoku(clues: SudokuGrid, seed: Int = 42, timeLimitS: Double = 10.0): SudokuGrid? {
    require(clues.size == 9 && clues.all { it.size == 9 }) { "Sudoku must be 9x9" }

    val cells = Array(9) { arrayOfNulls<IntVar>(9) }

    val model = cpModel {
        for (r in 0 until 9) {
            for (c in 0 until 9) {
                val v = intVar("c_${r}_$c", 1..9)
                cells[r][c] = v
                val clue = clues[r][c]
                if (clue != 0) {
                    constraint { +(v eq clue.toLong()) }
                }
            }
        }
        // Row, column, and 3x3 box AllDifferent families.
        for (r in 0 until 9) allDifferent((0 until 9).map { c -> cells[r][c]!! })
        for (c in 0 until 9) allDifferent((0 until 9).map { r -> cells[r][c]!! })
        for (br in 0 until 3) {
            for (bc in 0 until 3) {
                val block = buildList {
                    for (dr in 0 until 3) for (dc in 0 until 3) add(cells[br * 3 + dr][bc * 3 + dc]!!)
                }
                allDifferent(block)
            }
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
    return List(9) { r -> List(9) { c -> assignment[cells[r][c]!!].toInt() } }
}

/** The classic newspaper puzzle with a well-defined unique solution. */
public val CLASSIC_SUDOKU: SudokuGrid = parseSudoku(
    """
    5 3 . | . 7 . | . . .
    6 . . | 1 9 5 | . . .
    . 9 8 | . . . | . 6 .
    ------+-------+------
    8 . . | . 6 . | . . 3
    4 . . | 8 . 3 | . . 1
    7 . . | . 2 . | . . 6
    ------+-------+------
    . 6 . | . . . | 2 8 .
    . . . | 4 1 9 | . . 5
    . . . | . 8 . | . 7 9
    """
)
