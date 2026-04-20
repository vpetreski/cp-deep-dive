package io.vanja.cpsat.ch09

import io.vanja.cpsat.*

/**
 * Job-Shop Scheduling (JSSP) — schedule jobs each made of a sequence of
 * operations, every operation pinned to a machine. Makespan minimization.
 *
 * Model:
 *   - Each operation becomes an [IntervalVar] with fixed duration.
 *   - Per-job precedence: `op_i.end <= op_{i+1}.start`.
 *   - Per-machine `noOverlap` on the intervals that share a machine.
 *   - Objective: minimize a `makespan` variable s.t. every `op.end <= makespan`.
 *
 * The classic 3-job, 3-machine demo is the "Taillard 3x3" instance.
 */

/** One operation: fixed [duration] on [machine], position [index] within [jobId]. */
public data class Operation(
    val jobId: Int,
    val index: Int,
    val machine: Int,
    val duration: Int,
)

/** A full JSSP instance. */
public data class JobShopInstance(
    val jobs: List<List<Operation>>,
    val nMachines: Int,
) {
    public val horizon: Int by lazy { jobs.sumOf { j -> j.sumOf { it.duration } } }
}

/** Canonical 3x3 demo (from textbook examples). */
public val DEMO_33_JSSP: JobShopInstance = run {
    val jobs = listOf(
        // job 0: m0 (3) -> m1 (2) -> m2 (2)
        listOf(
            Operation(jobId = 0, index = 0, machine = 0, duration = 3),
            Operation(jobId = 0, index = 1, machine = 1, duration = 2),
            Operation(jobId = 0, index = 2, machine = 2, duration = 2),
        ),
        // job 1: m0 (2) -> m2 (1) -> m1 (4)
        listOf(
            Operation(jobId = 1, index = 0, machine = 0, duration = 2),
            Operation(jobId = 1, index = 1, machine = 2, duration = 1),
            Operation(jobId = 1, index = 2, machine = 1, duration = 4),
        ),
        // job 2: m1 (4) -> m2 (3)
        listOf(
            Operation(jobId = 2, index = 0, machine = 1, duration = 4),
            Operation(jobId = 2, index = 1, machine = 2, duration = 3),
        ),
    )
    JobShopInstance(jobs, nMachines = 3)
}

/** A scheduled operation with its resolved start time. */
public data class ScheduledOp(
    val op: Operation,
    val start: Int,
    val end: Int,
)

public data class JobShopResult(
    val status: String,
    val makespan: Int?,
    val schedule: List<ScheduledOp>,
)

public fun solveJobShop(
    instance: JobShopInstance = DEMO_33_JSSP,
    seed: Int = 42,
    timeLimitS: Double = 30.0,
): JobShopResult {
    val horizon = instance.horizon
    val nMachines = instance.nMachines

    // Flatten ops so we can iterate easily.
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

        // Per-job precedence: op[i].end <= op[i+1].start
        for (job in instance.jobs) {
            for (k in 1 until job.size) {
                val prev = opByKey[job[k - 1].jobId to job[k - 1].index]!!
                val cur = opByKey[job[k].jobId to job[k].index]!!
                constraint { +(prev.end le cur.start) }
            }
        }

        // Per-machine noOverlap
        for (m in 0 until nMachines) {
            val onThisMachine = ops.withIndex()
                .filter { (_, op) -> op.machine == m }
                .map { (i, _) -> intervals[i] }
            if (onThisMachine.size >= 2) {
                noOverlap(onThisMachine)
            }
        }

        // Makespan = max(end[i]). Express as: makespan >= every end; minimize makespan.
        makespan = intVar("makespan", 0..horizon)
        for (iv in intervals) {
            constraint { +(iv.end le makespan) }
        }
        minimize { makespan }
    }

    val res = model.solveBlocking {
        randomSeed = seed
        maxTimeInSeconds = timeLimitS
        numSearchWorkers = 4
    }

    val (statusStr, assignment) = when (res) {
        is SolveResult.Optimal -> "OPTIMAL" to res.values
        is SolveResult.Feasible -> "FEASIBLE" to res.values
        is SolveResult.Infeasible -> return JobShopResult("INFEASIBLE", null, emptyList())
        is SolveResult.Unknown -> return JobShopResult("UNKNOWN", null, emptyList())
        is SolveResult.ModelInvalid -> return JobShopResult("MODEL_INVALID", null, emptyList())
    }

    val scheduled = ops.mapIndexed { i, op ->
        val iv = intervals[i]
        val ivVal = assignment[iv]
        ScheduledOp(op, ivVal.start.toInt(), ivVal.end.toInt())
    }
    val makespanVal = assignment[makespan].toInt()
    return JobShopResult(statusStr, makespanVal, scheduled)
}

/** Pretty-print the schedule grouped by machine. */
public fun renderSchedule(result: JobShopResult, nMachines: Int): String {
    val sb = StringBuilder()
    sb.appendLine("makespan = ${result.makespan}")
    for (m in 0 until nMachines) {
        sb.append("  machine $m: ")
        val onM = result.schedule.filter { it.op.machine == m }.sortedBy { it.start }
        sb.append(onM.joinToString(", ") { "J${it.op.jobId}:${it.op.index} [${it.start}..${it.end})" })
        sb.appendLine()
    }
    return sb.toString()
}
