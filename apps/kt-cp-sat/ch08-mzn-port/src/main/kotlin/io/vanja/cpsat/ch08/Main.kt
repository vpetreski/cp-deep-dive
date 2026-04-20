package io.vanja.cpsat.ch08

import kotlin.system.measureTimeMillis

/**
 * Chapter 8 — run the toy NSP through `cpsat-kt`, same instance as
 * `apps/mzn/toy-nsp.mzn`.
 *
 * Run with:
 *   ./gradlew :ch08-mzn-port:run
 */
fun main() {
    val instance = DEMO_TOY_NSP
    println("=== Toy NSP via cpsat-kt ===")
    println("  instance: nurses=${instance.nNurses}  days=${instance.nDays}  shifts=${instance.nShifts}")
    println("  workload bounds: min=${instance.minWork}  max=${instance.maxWork}")
    println()

    val ms = measureTimeMillis {
        val sol = solveToyNsp(instance)
        print(renderToyNsp(instance, sol))
    }
    println("  (solved in ${ms}ms)")
}
