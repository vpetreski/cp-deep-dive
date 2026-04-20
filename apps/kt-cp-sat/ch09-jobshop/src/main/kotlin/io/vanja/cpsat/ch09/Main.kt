package io.vanja.cpsat.ch09

import java.io.File
import kotlin.system.measureTimeMillis

/**
 * Chapter 9 — job-shop scheduling with `intervalVar` + `noOverlap`.
 *
 * Solve the classic 3-job/3-machine demo and write a Gantt SVG to
 * `build/ch09-gantt.svg`.
 *
 * Run with:
 *   ./gradlew :ch09-jobshop:run
 */
fun main() {
    println("=== Job-shop scheduling ===")
    val instance = DEMO_33_JSSP
    println("  jobs=${instance.jobs.size}  machines=${instance.nMachines}  horizon=${instance.horizon}")

    val ms = measureTimeMillis {
        val result = solveJobShop(instance)
        check(result.status == "OPTIMAL" || result.status == "FEASIBLE") {
            "solver returned status ${result.status}"
        }
        println(renderSchedule(result, instance.nMachines))

        val out = File("build/ch09-gantt.svg")
        out.parentFile.mkdirs()
        out.writeText(renderGanttSvg(result, instance.nMachines))
        println("  Gantt written to ${out.absolutePath}")
    }
    println("  (solved in ${ms}ms)")
}
