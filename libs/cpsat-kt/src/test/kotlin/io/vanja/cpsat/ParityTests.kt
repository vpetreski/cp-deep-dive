package io.vanja.cpsat

import com.google.ortools.sat.CpModel as JCpModel
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverStatus
import com.google.ortools.sat.LinearExpr as JLinearExpr
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Cross-check: the same model expressed via raw OR-Tools Java API and via our
 * Kotlin DSL should yield the same optimum. This guards against subtle bugs in
 * how we translate expressions to the underlying builder.
 */
class ParityTests : StringSpec({

    "DSL and raw Java give same optimum for linear minimization" {
        val rawOpt: Long
        val dslOpt: Long

        // Raw Java version -----------------------------------------------------
        run {
            ensureNativesLoaded()
            val m = JCpModel()
            val x = m.newIntVar(0L, 10L, "x")
            val y = m.newIntVar(0L, 10L, "y")
            // 3x + 2y = 12
            val lhs = JLinearExpr.newBuilder()
                .addTerm(x, 3L)
                .addTerm(y, 2L)
                .build()
            m.addEquality(lhs, 12L)
            // x + y ≤ 5
            val sum = JLinearExpr.newBuilder().add(x).add(y).build()
            m.addLessOrEqual(sum, 5L)
            m.minimize(JLinearExpr.newBuilder().add(x).add(y).build())

            val solver = CpSolver()
            solver.parameters.randomSeed = 42
            val status = solver.solve(m)
            status shouldBe CpSolverStatus.OPTIMAL
            rawOpt = solver.objectiveValue().toLong()
        }

        // DSL version ----------------------------------------------------------
        run {
            lateinit var x: IntVar
            lateinit var y: IntVar
            val model = cpModel {
                x = intVar("x", 0..10)
                y = intVar("y", 0..10)
                constraint {
                    +(3 * x + 2 * y eq 12L)
                    +(x + y le 5L)
                }
                minimize { x + y }
            }
            val res = model.solveBlocking { randomSeed = 42 }
            res.shouldBeInstanceOf<SolveResult.Optimal>()
            dslOpt = res.objective
        }

        dslOpt shouldBe rawOpt
        dslOpt shouldBe 4L
    }

    "DSL operator chain equals raw LinearExpr builder for weighted sum" {
        ensureNativesLoaded()

        // Raw version
        val mRaw = JCpModel()
        val xr = mRaw.newIntVar(0L, 10L, "x")
        val yr = mRaw.newIntVar(0L, 10L, "y")
        val zr = mRaw.newIntVar(0L, 10L, "z")
        val rawObj = JLinearExpr.weightedSum(arrayOf(xr, yr, zr), longArrayOf(2L, 3L, 5L))
        mRaw.addEquality(JLinearExpr.newBuilder().add(xr).add(yr).add(zr).build(), 6L)
        mRaw.maximize(rawObj)
        val solverR = CpSolver()
        solverR.parameters.randomSeed = 42
        val statR = solverR.solve(mRaw)
        statR shouldBe CpSolverStatus.OPTIMAL
        val rawObjVal = solverR.objectiveValue().toLong()

        // DSL version
        lateinit var x: IntVar
        lateinit var y: IntVar
        lateinit var z: IntVar
        val model = cpModel {
            x = intVar("x", 0..10)
            y = intVar("y", 0..10)
            z = intVar("z", 0..10)
            constraint { +(x + y + z eq 6L) }
            maximize { 2 * x + 3 * y + 5 * z }
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        res.objective shouldBe rawObjVal
        // Maximum is achieved by z = 6 (if feasible).
        res.values[z] shouldBe 6L
        res.objective shouldBe 30L
    }

    "minimize objective sense matches raw" {
        lateinit var x: IntVar
        val model = cpModel {
            x = intVar("x", -5..5)
            constraint { +(x ge -5L) }
            minimize { x }
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        res.objective shouldBe -5L
        res.values[x] shouldBe -5L
    }

    "maximize objective sense matches raw" {
        lateinit var x: IntVar
        val model = cpModel {
            x = intVar("x", -5..5)
            maximize { x }
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        res.objective shouldBe 5L
        res.values[x] shouldBe 5L
    }
})
