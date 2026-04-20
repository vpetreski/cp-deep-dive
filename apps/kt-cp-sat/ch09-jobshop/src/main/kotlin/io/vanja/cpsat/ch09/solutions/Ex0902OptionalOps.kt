package io.vanja.cpsat.ch09.solutions

import io.vanja.cpsat.ch09.*
import io.vanja.cpsat.*

/**
 * Exercise 9.2 — make the last operation of each job *optional* and trade
 * skipping it for a penalty. The objective becomes:
 *
 *   makespan + penalty * sum(skipped)
 *
 * With a high enough penalty, the solver keeps every operation; tune it
 * down and skipping starts paying off.
 */
fun main() {
    val instance = DEMO_33_JSSP
    val horizon = instance.horizon
    val nMachines = instance.nMachines
    val penalty = 5L   // per-skip penalty (try 2, 5, 20)

    val ops = instance.jobs.flatten()
    val opByKey: MutableMap<Pair<Int, Int>, IntervalVar> = mutableMapOf()
    val presence: MutableMap<Pair<Int, Int>, BoolVar?> = mutableMapOf()

    lateinit var intervals: List<IntervalVar>
    lateinit var makespan: IntVar
    lateinit var skippedCount: IntVar

    val model = cpModel {
        // Materialize intervals; last op of each job is optional.
        val constructedIntervals = mutableListOf<IntervalVar>()
        val skipBools = mutableListOf<BoolVar>()
        for (job in instance.jobs) {
            for (op in job) {
                val isLast = op.index == job.size - 1
                val start = intVar("s_${op.jobId}_${op.index}", 0..horizon)
                val iv: IntervalVar
                if (isLast) {
                    val present = boolVar("present_${op.jobId}_${op.index}")
                    iv = optionalInterval("i_${op.jobId}_${op.index}", present) {
                        this.start = start
                        size = op.duration.toLong()
                    }
                    presence[op.jobId to op.index] = present
                    // Define `skip = 1 - present`. We represent this as a BoolVar and link.
                    val skip = boolVar("skip_${op.jobId}_${op.index}")
                    constraint { +((skip as IntVar) + (present as IntVar) eq 1L) }
                    skipBools.add(skip)
                } else {
                    iv = interval("i_${op.jobId}_${op.index}") {
                        this.start = start
                        size = op.duration.toLong()
                    }
                    presence[op.jobId to op.index] = null
                }
                opByKey[op.jobId to op.index] = iv
                constructedIntervals.add(iv)
            }
        }
        intervals = constructedIntervals

        // Per-job ordering. Non-optional intervals are always present; optional
        // intervals contribute nothing when absent (precedence vacuously satisfied).
        for (job in instance.jobs) {
            for (k in 1 until job.size) {
                val prev = opByKey[job[k - 1].jobId to job[k - 1].index]!!
                val cur = opByKey[job[k].jobId to job[k].index]!!
                constraint { +(prev.end le cur.start) }
            }
        }

        // Per-machine noOverlap (absent intervals are ignored by CP-SAT).
        for (m in 0 until nMachines) {
            val onM = ops.withIndex()
                .filter { (_, op) -> op.machine == m }
                .map { (i, _) -> intervals[i] }
            if (onM.size >= 2) noOverlap(onM)
        }

        makespan = intVar("makespan", 0..horizon)
        for (iv in intervals) {
            // end <= makespan — if the interval is absent, the solver still must honor
            // the constraint, but CP-SAT does not auto-ignore — so we have to guard.
            // The cleanest way is: only enforce when the interval is present.
            val pres = iv.presenceLiteral
            if (pres != null) {
                enforceIf(pres) { +(iv.end le makespan) }
            } else {
                constraint { +(iv.end le makespan) }
            }
        }

        skippedCount = intVar("skipped", 0..skipBools.size.toLong())
        constraint { +(weightedSum(skipBools.map { it as IntVar }, List(skipBools.size) { 1L }) eq skippedCount) }
        minimize { makespan + skippedCount * penalty }
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
            println("Ex 9.2 ($label) — skip-penalty=$penalty")
            println("  composite objective = $obj   makespan=${assignment[makespan]}   skipped=${assignment[skippedCount]}")
            for ((key, iv) in opByKey) {
                val pres = presence[key]
                val isPresent = if (pres == null) true else assignment[pres]
                if (isPresent) {
                    val ivVal = assignment[iv]
                    println("  J${key.first}:${key.second}  [${ivVal.start}..${ivVal.end})")
                } else {
                    println("  J${key.first}:${key.second}  SKIPPED")
                }
            }
        }
        else -> println("Ex 9.2 solver returned $res")
    }
}
