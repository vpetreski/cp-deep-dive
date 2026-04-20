package io.vanja.cpsat.ch05.solutions

import io.vanja.cpsat.ch05.*
import io.vanja.cpsat.*

/**
 * Exercise 5.2 — lexicographic objective.
 *
 * Two-stage objective on the demo knapsack:
 *   Stage 1: maximize total value (the usual knapsack objective).
 *   Stage 2: among the optima, minimize the number of items (fewer choices).
 *
 * Uses cpsat-kt's `lexicographic { ... }` + `solveLexicographic(stages)`.
 */
fun main() {
    val items = DEMO_ITEMS
    val cap = DEMO_CAPACITY

    lateinit var xs: List<BoolVar>
    val model = cpModel {
        xs = boolVarList("x", items.size)
        constraint {
            +(weightedSum(xs.map { it as IntVar }, items.map { it.weight }) le cap)
        }
    }

    val stages = model.lexicographic {
        primary(Sense.MAXIMIZE) { weightedSum(xs.map { it as IntVar }, items.map { it.value }) }
        secondary(Sense.MINIMIZE) { weightedSum(xs.map { it as IntVar }, List(xs.size) { 1L }) }
    }

    val res = model.solveLexicographic(stages) {
        randomSeed = 42
        maxTimeInSeconds = 10.0
        numSearchWorkers = 4
    }

    when (res) {
        is SolveResult.Optimal -> {
            val chosen = items.zip(xs).filter { (_, x) -> res.values[x] }.map { it.first.name }
            println("Lex OK: count=${res.objective} (stage 2 minimized count)")
            println("  chosen: $chosen")
        }
        is SolveResult.Feasible -> {
            val chosen = items.zip(xs).filter { (_, x) -> res.values[x] }.map { it.first.name }
            println("Lex partial: stage2 count=${res.objective}  chosen: $chosen")
        }
        else -> println("Solver returned $res")
    }
}
