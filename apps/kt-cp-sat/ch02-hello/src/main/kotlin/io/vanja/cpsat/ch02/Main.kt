package io.vanja.cpsat.ch02

/*
 * ------------------------------------------------------------
 * Chapter 2 — "Hello, CP-SAT" (the motivational pain demo)
 * ------------------------------------------------------------
 *
 * Why does cpsat-kt exist? This file answers that question by solving a
 * trivial feasibility problem using the RAW OR-Tools Java API from Kotlin.
 * Every line here could be one expressive line with `cpsat-kt`; the
 * companion `MainWithDsl.kt.template` shows the contrast.
 *
 * Problem:
 *   Find integers x, y in [0, 10] with:
 *     3x + 2y == 12
 *     x + y   <=  5
 *   and print any feasible assignment.
 *
 * Running:
 *   ./gradlew :ch02-hello:run
 *
 * The Loader.loadNativeLibraries() call unpacks the platform-specific
 * jniortools library from the ortools-java jar and loads it. That alone is
 * one of the things cpsat-kt hides for you.
 */

import com.google.ortools.Loader
import com.google.ortools.sat.CpModel
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverStatus
import com.google.ortools.sat.LinearExpr

fun main() {
    // 1. Load native libs. Forgetting this is the #1 gotcha.
    Loader.loadNativeLibraries()

    // 2. Build the model.
    val model = CpModel()
    val x = model.newIntVar(0L, 10L, "x")
    val y = model.newIntVar(0L, 10L, "y")

    // 3x + 2y == 12  (note the builder chain — no operator overloads).
    val lhs = LinearExpr.newBuilder()
        .addTerm(x, 3L)
        .addTerm(y, 2L)
        .build()
    model.addEquality(lhs, 12L)

    // x + y <= 5
    val sum = LinearExpr.newBuilder().add(x).add(y).build()
    model.addLessOrEqual(sum, 5L)

    // 3. Solve.
    val solver = CpSolver()
    solver.parameters.randomSeed = 42
    val status = solver.solve(model)

    // 4. Inspect the result — status-by-status.
    when (status) {
        CpSolverStatus.OPTIMAL, CpSolverStatus.FEASIBLE -> {
            println("Found: x=${solver.value(x)}, y=${solver.value(y)}")
            println("Status: $status")
            println()
            println("Check: 3*${solver.value(x)} + 2*${solver.value(y)} = " +
                "${3 * solver.value(x) + 2 * solver.value(y)} (expected 12)")
            println("Check: ${solver.value(x)} + ${solver.value(y)} = " +
                "${solver.value(x) + solver.value(y)} (expected <= 5)")
        }
        CpSolverStatus.INFEASIBLE -> println("Proven infeasible.")
        CpSolverStatus.MODEL_INVALID -> println("Model invalid.")
        CpSolverStatus.UNKNOWN, CpSolverStatus.UNRECOGNIZED -> println("Unknown.")
        else -> println("Other status: $status")
    }

    println()
    println("---")
    println("This is the raw OR-Tools API. See MainWithDsl.kt.template for")
    println("the same program written with cpsat-kt — the difference is the")
    println("whole point of this library.")
}
