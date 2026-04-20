package io.vanja.cpsat.ch06

import kotlin.system.measureTimeMillis

/**
 * Chapter 6 — global constraints.
 *
 * Tour six globals back-to-back. Every demo is a minimal, self-contained model
 * that exercises one constraint in isolation so you can feel what each does.
 *
 * Run with:
 *   ./gradlew :ch06-globals:run
 */
fun main() {
    section("1. AllDifferent") {
        val sol = solveAllDifferentSum(n = 5, maxVal = 10, targetSum = 30)
            ?: error("AllDifferent returned no solution")
        println("  5 distinct values in 0..10, sum = 30  ->  ${sol.values}")
    }

    section("2. Element") {
        val sol = solveElementDemo(minValue = 21)
            ?: error("Element demo returned no solution")
        println("  menu[${sol.pickedIndex}] = ${sol.pickedValue}  (smallest menu value > 20)")
    }

    section("3. Table") {
        val sol = solveTableDemo(pinDay = Day.MON, pinShift = Shift.NIGHT)
            ?: error("Table demo returned no solution")
        println("  pinned (Mon, night) -> nurse = ${sol.chosen.nurse}  (alternatives available: ${sol.alternativesCount})")
    }

    section("4. Circuit — 8-city TSP on a regular octagon") {
        val ms = measureTimeMillis {
            val r = solveTsp()
            val ordered = r.order.joinToString(" -> ") { OCTAGON_CITIES[it].name }
            println("  optimal tour: $ordered -> ${OCTAGON_CITIES[r.order[0]].name}")
            println("  scaled distance = ${r.totalDistanceScaled}  (≈ ${"%.3f".format(r.totalDistance)} units)")
        }
        println("  (solved in ${ms}ms)")
    }

    section("5. Automaton — no 4 consecutive nights over 14 days, exactly 7 nights") {
        val sol = solveNoFourNights(horizon = 14, targetNights = 7)
            ?: error("Automaton returned no solution")
        println("  days:       ${sol.days.joinToString(" ")}")
        println("  totalNights=${sol.totalNights}  maxRun=${sol.maxRun}")
    }

    section("6. Inverse — 5 nurses ↔ 5 shifts permutation") {
        val sol = solveInverseDemo()
            ?: error("Inverse demo returned no solution")
        println("  assign:  ${sol.assign}")
        println("  shiftOf: ${sol.shiftOf}")
    }
}

private inline fun section(title: String, block: () -> Unit) {
    println("=== $title ===")
    block()
    println()
}
