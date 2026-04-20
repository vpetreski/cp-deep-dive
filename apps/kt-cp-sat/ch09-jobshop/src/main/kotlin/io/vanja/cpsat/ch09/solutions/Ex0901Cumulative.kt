package io.vanja.cpsat.ch09.solutions

import io.vanja.cpsat.ch09.*
import io.vanja.cpsat.*

/**
 * Exercise 9.1 — swap `noOverlap` for `cumulative` to model a machine that
 * can run two jobs in parallel (capacity = 2).
 *
 * Keeps the same 3x3 demo but lets machine 1 accept two simultaneous
 * operations. Expected outcome: a lower makespan than the baseline (the
 * bottleneck on machine 1 disappears).
 */
fun main() {
    val instance = DEMO_33_JSSP
    val horizon = instance.horizon
    val nMachines = instance.nMachines

    val ops = instance.jobs.flatten()
    val opByKey: MutableMap<Pair<Int, Int>, IntervalVar> = mutableMapOf()

    lateinit var intervals: List<IntervalVar>
    lateinit var makespan: IntVar

    val model = cpModel {
        intervals = ops.map { op ->
            val start = intVar("s_${op.jobId}_${op.index}", 0..horizon)
            interval("i_${op.jobId}_${op.index}") {
                this.start = start
                size = op.duration.toLong()
            }.also { iv -> opByKey[op.jobId to op.index] = iv }
        }

        for (job in instance.jobs) {
            for (k in 1 until job.size) {
                val prev = opByKey[job[k - 1].jobId to job[k - 1].index]!!
                val cur = opByKey[job[k].jobId to job[k].index]!!
                constraint { +(prev.end le cur.start) }
            }
        }

        for (m in 0 until nMachines) {
            val onM = ops.withIndex()
                .filter { (_, op) -> op.machine == m }
                .map { (i, _) -> intervals[i] }
            val capacity = if (m == 1) 2L else 1L
            if (onM.size >= 2) {
                cumulative(onM, demands = List(onM.size) { 1L }, capacity = capacity)
            }
        }

        makespan = intVar("makespan", 0..horizon)
        for (iv in intervals) constraint { +(iv.end le makespan) }
        minimize { makespan }
    }

    val res = model.solveBlocking {
        randomSeed = 42
        numSearchWorkers = 4
        maxTimeInSeconds = 30.0
    }

    when (res) {
        is SolveResult.Optimal, is SolveResult.Feasible -> {
            val label = if (res is SolveResult.Optimal) "OPTIMAL" else "FEASIBLE"
            val assignment = if (res is SolveResult.Optimal) res.values else (res as SolveResult.Feasible).values
            val obj = if (res is SolveResult.Optimal) res.objective else (res as SolveResult.Feasible).objective
            println("Ex 9.1 ($label) — machine 1 capacity=2")
            println("  makespan = $obj")
            for (m in 0 until nMachines) {
                val onM = ops.withIndex()
                    .filter { (_, op) -> op.machine == m }
                    .map { (i, op) ->
                        val ivVal = assignment[intervals[i]]
                        "J${op.jobId}:${op.index} [${ivVal.start}..${ivVal.end})"
                    }
                println("  machine $m  ${onM.joinToString(", ")}")
            }
        }
        else -> println("Ex 9.1 solver returned $res")
    }
}
