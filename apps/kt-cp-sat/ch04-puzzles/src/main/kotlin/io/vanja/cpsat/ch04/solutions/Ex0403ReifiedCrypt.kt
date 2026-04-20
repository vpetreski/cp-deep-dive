package io.vanja.cpsat.ch04.solutions

import io.vanja.cpsat.*

/**
 * Exercise 4.3 — reified cryptarithmetic.
 *
 * Add a boolean `odd_e` that tracks "E is odd" via reification (both
 * directions of the implication). Then impose `odd_e ⇒ M + O ≥ 5`.
 *
 * In the canonical SEND+MORE=MONEY solution, E=5 (odd), M+O = 1+0 = 1. The
 * extra constraint demands ≥5, so we expect **infeasible** — proving that
 * reifying conditions on top of a uniquely-solved puzzle can eliminate the
 * only solution and yield no feasible assignment.
 */
fun main() {
    val letters = "SENDMORY".toList()
    val vars = mutableMapOf<Char, IntVar>()
    lateinit var oddE: BoolVar

    val model = cpModel {
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

        // Full reification: odd_e <-> (E == 1 or 3 or 5 or 7 or 9).
        // Build helper booleans b5/b7 etc. The cleanest pattern: tie oddE to
        // (E % 2 == 1). CP-SAT lacks direct modular reification, but we can
        // express it as a disjunction over odd digits via channelEq-per-digit.
        oddE = boolVar("odd_e")
        // Five candidates for odd E values: 1, 3, 5, 7, 9.
        val candidates = listOf(1L, 3L, 5L, 7L, 9L)
        val isEquals = candidates.map { v -> boolVar("e_eq_$v").also { channelEq(it, e, v) } }
        // oddE = OR(isEquals[i]).
        // Encode: oddE => sum(isEquals) >= 1  and  !oddE => sum(isEquals) == 0.
        val sumIs = isEquals.drop(1).fold(isEquals[0] as io.vanja.cpsat.LinearExpr) { acc, b -> acc + b }
        enforceIf(oddE) { +(sumIs ge 1L) }
        enforceIf(oddE) { /* no-op placeholder to keep block syntax consistent */ }
        // For the negative direction we reuse `enforceIf` with a fresh !oddE proxy via the DSL.
        // Simplest encoding: !oddE => sum == 0.
        val notOddE = boolVar("not_odd_e")
        constraint {
            +(notOddE + oddE eq 1L) // notOddE == !oddE
        }
        enforceIf(notOddE) { +(sumIs eq 0L) }

        // The conditional rule: oddE => M + O >= 5.
        enforceIf(oddE) { +(m + o ge 5L) }
    }

    val res = model.solveBlocking { randomSeed = 42 }
    when (res) {
        is SolveResult.Optimal, is SolveResult.Feasible -> {
            val a = if (res is SolveResult.Optimal) res.values else (res as SolveResult.Feasible).values
            println("Feasible with odd_e=${a[oddE]}:")
            for (c in letters) println("  $c = ${a[vars[c]!!]}")
        }
        SolveResult.Infeasible -> {
            println("Infeasible — the reified implication (odd_e => M+O >= 5) kills the")
            println("only SEND+MORE=MONEY solution (E=5 is odd but M+O=1).")
        }
        else -> println("Solver terminated without a definite answer: $res")
    }
}
