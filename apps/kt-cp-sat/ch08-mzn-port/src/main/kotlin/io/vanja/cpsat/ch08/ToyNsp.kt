package io.vanja.cpsat.ch08

import io.vanja.cpsat.*

/**
 * Toy Nurse Scheduling, ported from `apps/mzn/toy-nsp.mzn`.
 *
 * Decisions: `work[n][d][s]` is a boolean: nurse n works shift s on day d.
 *
 * Hard constraints (match the MZN verbatim):
 *   HC-1: every (d, s) is staffed by exactly one nurse.
 *   HC-2: a nurse works at most one shift per day.
 *   HC-3: each nurse's total workload is in `[min_work, max_work]`.
 *
 * Objective:
 *   minimize `spread = max(totals) - min(totals)`.
 *
 * We express min/max using two auxiliary variables `maxT` and `minT` plus
 * `ge`/`le` constraints — a small trick because CP-SAT needs integer vars
 * for the objective. The `spread = maxT - minT` linear expression is what
 * actually gets minimized.
 */
public data class NspInstance(
    val nNurses: Int,
    val nDays: Int,
    val nShifts: Int,
    val minWork: Int,
    val maxWork: Int,
) {
    init {
        require(nNurses >= 1 && nDays >= 1 && nShifts >= 1) { "positive sizes required" }
        require(minWork in 0..maxWork) { "minWork must be in [0, maxWork]" }
        require(maxWork <= nDays * nShifts) { "maxWork exceeds available slots" }
        // Feasibility guard for HC-1 + workload bounds:
        //   total assignments = nDays * nShifts
        //   each nurse takes between minWork and maxWork
        require(nNurses * minWork <= nDays * nShifts) {
            "Infeasible: nNurses*minWork=${nNurses * minWork} > slots=${nDays * nShifts}"
        }
        require(nNurses * maxWork >= nDays * nShifts) {
            "Infeasible: nNurses*maxWork=${nNurses * maxWork} < slots=${nDays * nShifts}"
        }
    }
}

/** Default instance — the same as `apps/mzn/toy-nsp.dzn`. */
public val DEMO_TOY_NSP: NspInstance = NspInstance(
    nNurses = 3, nDays = 7, nShifts = 2, minWork = 4, maxWork = 6,
)

public data class NspSolution(
    val status: String,
    val spread: Long?,
    val totals: List<Long>,                          // totals[n] for each nurse
    val work: List<List<List<Boolean>>>,             // work[n][d][s]
)

public fun solveToyNsp(
    instance: NspInstance = DEMO_TOY_NSP,
    seed: Int = 42,
    timeLimitS: Double = 10.0,
): NspSolution {
    val N = instance.nNurses
    val D = instance.nDays
    val S = instance.nShifts

    lateinit var work: Array<Array<Array<BoolVar>>>
    lateinit var totals: List<IntVar>
    lateinit var spread: IntVar
    lateinit var maxT: IntVar
    lateinit var minT: IntVar

    val model = cpModel {
        work = Array(N) { n ->
            Array(D) { d ->
                Array(S) { s -> boolVar("work_${n}_${d}_$s") }
            }
        }

        // HC-1: each (d, s) has exactly one nurse.
        for (d in 0 until D) {
            for (s in 0 until S) {
                exactlyOne((0 until N).map { n -> work[n][d][s] })
            }
        }

        // HC-2: at most one shift per nurse per day.
        for (n in 0 until N) {
            for (d in 0 until D) {
                atMostOne((0 until S).map { s -> work[n][d][s] })
            }
        }

        // HC-3 + totals.
        totals = (0 until N).map { n -> intVar("total_$n", 0..(D * S)) }
        for (n in 0 until N) {
            val flat = (0 until D).flatMap { d -> (0 until S).map { s -> work[n][d][s] as IntVar } }
            constraint { +(weightedSum(flat, List(flat.size) { 1L }) eq totals[n]) }
            constraint { +(totals[n] ge instance.minWork.toLong()) }
            constraint { +(totals[n] le instance.maxWork.toLong()) }
        }

        // Objective plumbing: spread = maxT - minT.
        maxT = intVar("maxT", 0..(D * S))
        minT = intVar("minT", 0..(D * S))
        for (t in totals) {
            constraint { +(t le maxT) }
            constraint { +(t ge minT) }
        }
        spread = intVar("spread", 0..(D * S))
        constraint { +(spread eq (maxT - minT)) }
        minimize { spread }
    }

    val res = model.solveBlocking {
        randomSeed = seed
        maxTimeInSeconds = timeLimitS
        numSearchWorkers = 4
    }

    return when (res) {
        is SolveResult.Optimal -> buildSolution("OPTIMAL", res.values, work, totals, spread)
        is SolveResult.Feasible -> buildSolution("FEASIBLE", res.values, work, totals, spread)
        is SolveResult.Infeasible -> NspSolution("INFEASIBLE", null, emptyList(), emptyList())
        is SolveResult.Unknown -> NspSolution("UNKNOWN", null, emptyList(), emptyList())
        is SolveResult.ModelInvalid -> NspSolution("MODEL_INVALID:${res.message}", null, emptyList(), emptyList())
    }
}

private fun buildSolution(
    status: String,
    assignment: Assignment,
    work: Array<Array<Array<BoolVar>>>,
    totals: List<IntVar>,
    spread: IntVar,
): NspSolution {
    val N = work.size
    val D = if (N > 0) work[0].size else 0
    val S = if (D > 0) work[0][0].size else 0
    val workVals: List<List<List<Boolean>>> = (0 until N).map { n ->
        (0 until D).map { d ->
            (0 until S).map { s -> assignment[work[n][d][s]] }
        }
    }
    return NspSolution(
        status = status,
        spread = assignment[spread],
        totals = totals.map { assignment[it] },
        work = workVals,
    )
}

/** Pretty print a solution as a grid of nurses × days × shifts. */
public fun renderToyNsp(instance: NspInstance, sol: NspSolution): String {
    val sb = StringBuilder()
    sb.appendLine("status=${sol.status}")
    if (sol.spread != null) sb.appendLine("spread=${sol.spread}")
    if (sol.totals.isNotEmpty()) sb.appendLine("totals=${sol.totals}")
    if (sol.work.isEmpty()) return sb.toString()

    sb.append("nurse ")
    for (d in 0 until instance.nDays) {
        for (s in 0 until instance.nShifts) {
            sb.append(" d${d}s${s}")
        }
    }
    sb.appendLine()
    for (n in 0 until instance.nNurses) {
        sb.append("  ").append(n).append("   ")
        for (d in 0 until instance.nDays) {
            for (s in 0 until instance.nShifts) {
                sb.append(if (sol.work[n][d][s]) "   X " else "   . ")
            }
        }
        sb.appendLine()
    }
    return sb.toString()
}
