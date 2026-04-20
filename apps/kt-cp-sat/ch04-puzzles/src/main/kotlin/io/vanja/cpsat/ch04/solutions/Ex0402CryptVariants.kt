package io.vanja.cpsat.ch04.solutions

import io.vanja.cpsat.*
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.runBlocking

/**
 * Exercise 4.2 — cryptarithmetic variants.
 *
 *   (a) SEND + MOST = MONEY          feasible? (try it)
 *   (b) CROSS + ROADS = DANGER       at least one solution printed
 *   (c) count all solutions to TWO + TWO = FOUR
 */

fun main(): Unit = runBlocking {
    // (a) SEND + MOST = MONEY
    val a = solveCrypt(
        leading = setOf('S', 'M'),
        addends = listOf("SEND", "MOST"),
        result = "MONEY",
    )
    println("SEND + MOST = MONEY: ${a ?: "INFEASIBLE"}")

    // (b) CROSS + ROADS = DANGER
    val b = solveCrypt(
        leading = setOf('C', 'R', 'D'),
        addends = listOf("CROSS", "ROADS"),
        result = "DANGER",
    )
    println("CROSS + ROADS = DANGER: ${b ?: "INFEASIBLE"}")

    // (c) TWO + TWO = FOUR — count all solutions.
    val count = countCrypt(
        leading = setOf('T', 'F'),
        addends = listOf("TWO", "TWO"),
        result = "FOUR",
    )
    println("TWO + TWO = FOUR: $count solutions")
}

/** Generic cryptarithmetic solver — returns a letter->digit map or null. */
fun solveCrypt(
    leading: Set<Char>,
    addends: List<String>,
    result: String,
): Map<Char, Long>? {
    val letters = (addends.joinToString("") + result).toSet().toList()
    if (letters.size > 10) return null // more letters than digits

    val vars = mutableMapOf<Char, IntVar>()
    val model = cpModel {
        for (c in letters) {
            val lo = if (c in leading) 1 else 0
            vars[c] = intVar(c.toString(), lo..9)
        }
        allDifferent(vars.values.toList())

        val sumExprs = addends.map { word -> wordExpr(word, vars) }
        val resultExpr = wordExpr(result, vars)
        val totalExpr = sumExprs.reduce { a, b -> a + b }
        constraint { +(totalExpr eq resultExpr) }
    }
    return when (val r = model.solveBlocking { randomSeed = 42 }) {
        is SolveResult.Optimal -> letters.associateWith { r.values[vars[it]!!] }
        is SolveResult.Feasible -> letters.associateWith { r.values[vars[it]!!] }
        else -> null
    }
}

/** Count all distinct (letter -> digit) solutions for this puzzle. */
suspend fun countCrypt(
    leading: Set<Char>,
    addends: List<String>,
    result: String,
): Int {
    val letters = (addends.joinToString("") + result).toSet().toList()
    if (letters.size > 10) return 0
    val vars = mutableMapOf<Char, IntVar>()
    val model = cpModel {
        for (c in letters) {
            val lo = if (c in leading) 1 else 0
            vars[c] = intVar(c.toString(), lo..9)
        }
        allDifferent(vars.values.toList())
        val sumExprs = addends.map { word -> wordExpr(word, vars) }
        val resultExpr = wordExpr(result, vars)
        val totalExpr = sumExprs.reduce { a, b -> a + b }
        constraint { +(totalExpr eq resultExpr) }
    }
    return model.solveFlow {
        rawProto { enumerateAllSolutions = true }
    }.count()
}

private fun wordExpr(word: String, vars: Map<Char, IntVar>): io.vanja.cpsat.LinearExpr {
    // Build 10^k * v[digit_k] term by term, avoiding empty expressions.
    val terms = word.mapIndexed { idx, c ->
        val power = pow10(word.length - 1 - idx)
        power * vars[c]!!
    }
    return terms.reduce { a, b -> a + b }
}

private fun pow10(n: Int): Long {
    var p = 1L
    repeat(n) { p *= 10 }
    return p
}
