package io.vanja.cpsat.ch04

import io.vanja.cpsat.*

/**
 * SEND + MORE = MONEY — the classic cryptarithmetic puzzle.
 *
 * Each distinct letter stands for a distinct digit (0..9). Leading digits
 * (S, M) are non-zero. The addition must hold.
 *
 * Unique solution:
 *   S=9 E=5 N=6 D=7  M=1 O=0 R=8 Y=2
 *   9567 + 1085 = 10652
 */

public data class SendMoreMoneySolution(
    val digits: Map<Char, Long>,
    val send: Long,
    val more: Long,
    val money: Long,
)

public fun solveSendMoreMoney(seed: Int = 42): SendMoreMoneySolution? {
    val letters = "SENDMORY".toList()
    val vars = mutableMapOf<Char, IntVar>()

    val model = cpModel {
        // Leading digits must be >= 1; others >= 0. Whole alphabet shares 0..9.
        for (c in letters) {
            val lo = if (c == 'S' || c == 'M') 1 else 0
            vars[c] = intVar(c.toString(), lo..9)
        }
        allDifferent(vars.values.toList())

        val s = vars['S']!!; val e = vars['E']!!; val n = vars['N']!!; val d = vars['D']!!
        val m = vars['M']!!; val o = vars['O']!!; val r = vars['R']!!; val y = vars['Y']!!

        val send = 1000 * s + 100 * e + 10 * n + d
        val more = 1000 * m + 100 * o + 10 * r + e
        val money = 10000 * m + 1000 * o + 100 * n + 10 * e + y

        constraint { +(send + more eq money) }
    }

    val res = model.solveBlocking { randomSeed = seed }
    val assignment = when (res) {
        is SolveResult.Optimal -> res.values
        is SolveResult.Feasible -> res.values
        else -> return null
    }
    val digits = letters.associateWith { assignment[vars[it]!!] }
    val sendV = 1000 * digits['S']!! + 100 * digits['E']!! + 10 * digits['N']!! + digits['D']!!
    val moreV = 1000 * digits['M']!! + 100 * digits['O']!! + 10 * digits['R']!! + digits['E']!!
    val moneyV = 10000 * digits['M']!! + 1000 * digits['O']!! + 100 * digits['N']!! +
        10 * digits['E']!! + digits['Y']!!
    return SendMoreMoneySolution(digits, sendV, moreV, moneyV)
}

public fun renderSendMoreMoney(sol: SendMoreMoneySolution): String = buildString {
    val d = sol.digits
    appendLine("  SEND  = ${sol.send}")
    appendLine("+ MORE  = ${sol.more}")
    appendLine("-------")
    appendLine("= MONEY = ${sol.money}")
    appendLine()
    appendLine("letters:")
    for (c in "SENDMORY") appendLine("  $c = ${d[c]}")
}
