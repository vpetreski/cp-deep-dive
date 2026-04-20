package io.vanja.cpsat.ch08.solutions

import io.vanja.cpsat.ch08.*

/**
 * Exercise 8.2 — scale the toy NSP up and inspect the spread.
 *
 * We keep the same formulation as `solveToyNsp` but scale to 8 nurses x 14
 * days x 3 shifts (morning/evening/night). The interesting output is how the
 * objective moves as workload bounds tighten.
 */
fun main() {
    val variants = listOf(
        NspInstance(nNurses = 8, nDays = 14, nShifts = 3, minWork = 4, maxWork = 6),
        NspInstance(nNurses = 8, nDays = 14, nShifts = 3, minWork = 5, maxWork = 6),
        NspInstance(nNurses = 8, nDays = 14, nShifts = 3, minWork = 5, maxWork = 5),
    )
    for (inst in variants) {
        val sol = solveToyNsp(inst, timeLimitS = 20.0)
        println("[min=${inst.minWork} max=${inst.maxWork}] status=${sol.status} spread=${sol.spread} totals=${sol.totals}")
    }
}
