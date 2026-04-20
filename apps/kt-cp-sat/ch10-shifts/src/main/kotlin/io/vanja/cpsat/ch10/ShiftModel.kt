package io.vanja.cpsat.ch10

import io.vanja.cpsat.*

/**
 * Chapter 10 — shift scheduling with calendar-aware transitions.
 *
 * A step up from the toy NSP: we explicitly model a DAY and NIGHT shift
 * stream and enforce a calendar rule that's universal in real rosters:
 *
 *   "After a NIGHT shift, the next day must be OFF or DAY — never NIGHT → DAY
 *   with no rest."
 *
 * This is our NSP warm-up — we exercise the same constraint tricks the full
 * NSP will need in later chapters (coverage, one-per-day, per-nurse workload,
 * soft/hard transitions) on a 5-nurse, 7-day, 2-shift instance that still
 * fits on one screen.
 */

public enum class Shift(public val label: String) { DAY("D"), NIGHT("N") }

public data class ShiftInstance(
    val nNurses: Int,
    val nDays: Int,
    /** Minimum staff on each (day, shift). Forces HC-1 coverage. */
    val coverage: Int,
    val minWork: Int,
    val maxWork: Int,
) {
    init {
        require(nNurses >= 1 && nDays >= 1) { "positive sizes required" }
        require(coverage >= 1 && coverage <= nNurses) {
            "coverage must be in [1, nNurses]"
        }
        require(minWork in 0..maxWork) { "minWork <= maxWork" }
        // One shift per nurse per day ceiling.
        require(maxWork <= nDays) {
            "maxWork ($maxWork) > nDays ($nDays) impossible with at-most-one shift/day"
        }
        val slots = nDays * Shift.entries.size
        // Coarse coverage feasibility: capacity must meet coverage demand.
        require(coverage * slots <= nNurses * maxWork) {
            "Infeasible: coverage demand ${coverage * slots} > nurses capacity ${nNurses * maxWork}"
        }
    }
}

/** Default demo: 5 nurses, 7 days, 2 shifts, ≥ 1 per (day, shift), workload in [3, 5]. */
public val DEMO_SHIFTS: ShiftInstance = ShiftInstance(
    nNurses = 5, nDays = 7, coverage = 1, minWork = 3, maxWork = 5,
)

public data class ShiftAssignment(
    val nurseIndex: Int,
    val day: Int,
    val shift: Shift,
)

public data class ShiftResult(
    val status: String,
    val assignments: List<ShiftAssignment>,
    val totals: List<Int>,
    val spread: Int?,
)

/**
 * Solve the shift problem. Decision variables:
 *   - `x[n, d, s]` — BoolVar, 1 iff nurse n takes shift s on day d
 * Constraints:
 *   - Coverage: ∀ d, s: sum_n x[n,d,s] ≥ coverage
 *   - One-per-day: ∀ n, d: sum_s x[n,d,s] ≤ 1
 *   - Night→Day rest: x[n,d,NIGHT] + x[n,d+1,DAY] ≤ 1 for every d in [0, D-2]
 *   - Workload: minWork ≤ totals[n] ≤ maxWork
 * Objective: minimize max(totals) - min(totals).
 */
public fun solveShifts(
    instance: ShiftInstance = DEMO_SHIFTS,
    seed: Int = 42,
    timeLimitS: Double = 10.0,
): ShiftResult {
    val N = instance.nNurses
    val D = instance.nDays
    val shifts = Shift.entries
    val S = shifts.size

    lateinit var x: Array<Array<Array<BoolVar>>>
    lateinit var totals: List<IntVar>
    lateinit var spread: IntVar

    val model = cpModel {
        x = Array(N) { n ->
            Array(D) { d ->
                Array(S) { s -> boolVar("x_${n}_${d}_${shifts[s].label}") }
            }
        }

        // Coverage: at least `coverage` nurses per (day, shift).
        for (d in 0 until D) {
            for (s in 0 until S) {
                val column = (0 until N).map { n -> x[n][d][s] as IntVar }
                constraint {
                    +(weightedSum(column, List(N) { 1L }) ge instance.coverage.toLong())
                }
            }
        }

        // At most one shift per nurse per day.
        for (n in 0 until N) {
            for (d in 0 until D) {
                atMostOne((0 until S).map { s -> x[n][d][s] })
            }
        }

        // Night → Day transition forbidden: x[n,d,NIGHT] + x[n,d+1,DAY] ≤ 1.
        val nightIdx = Shift.NIGHT.ordinal
        val dayIdx = Shift.DAY.ordinal
        for (n in 0 until N) {
            for (d in 0 until D - 1) {
                val pair = listOf(x[n][d][nightIdx] as IntVar, x[n][d + 1][dayIdx] as IntVar)
                constraint { +(weightedSum(pair, listOf(1L, 1L)) le 1L) }
            }
        }

        // Totals + workload bounds.
        totals = (0 until N).map { n -> intVar("total_$n", 0..(D * S)) }
        for (n in 0 until N) {
            val flat = (0 until D).flatMap { d -> (0 until S).map { s -> x[n][d][s] as IntVar } }
            constraint { +(weightedSum(flat, List(flat.size) { 1L }) eq totals[n]) }
            constraint { +(totals[n] ge instance.minWork.toLong()) }
            constraint { +(totals[n] le instance.maxWork.toLong()) }
        }

        // Objective: minimize spread = max(totals) - min(totals).
        val maxT = intVar("maxT", 0..(D * S))
        val minT = intVar("minT", 0..(D * S))
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

    val (statusStr, assignment, spreadVal) = when (res) {
        is SolveResult.Optimal -> Triple("OPTIMAL", res.values, res.objective.toInt())
        is SolveResult.Feasible -> Triple("FEASIBLE", res.values, res.objective.toInt())
        is SolveResult.Infeasible -> return ShiftResult("INFEASIBLE", emptyList(), emptyList(), null)
        is SolveResult.Unknown -> return ShiftResult("UNKNOWN", emptyList(), emptyList(), null)
        is SolveResult.ModelInvalid -> return ShiftResult("MODEL_INVALID:${res.message}", emptyList(), emptyList(), null)
    }

    val out = mutableListOf<ShiftAssignment>()
    for (n in 0 until N) {
        for (d in 0 until D) {
            for (s in 0 until S) {
                if (assignment[x[n][d][s]]) {
                    out.add(ShiftAssignment(n, d, shifts[s]))
                }
            }
        }
    }
    val totalsVal = totals.map { assignment[it].toInt() }
    return ShiftResult(statusStr, out, totalsVal, spreadVal)
}

/** Pretty-print a calendar: rows = nurses, columns = days. */
public fun renderCalendar(instance: ShiftInstance, result: ShiftResult): String {
    val N = instance.nNurses
    val D = instance.nDays
    val grid = Array(N) { Array(D) { "." } }
    for (a in result.assignments) {
        grid[a.nurseIndex][a.day] = a.shift.label
    }
    val sb = StringBuilder()
    sb.appendLine("status=${result.status}   spread=${result.spread}   totals=${result.totals}")
    sb.append("nurse ")
    for (d in 0 until D) sb.append(" d$d")
    sb.appendLine()
    for (n in 0 until N) {
        sb.append("  $n   ")
        for (d in 0 until D) sb.append("  ${grid[n][d]}")
        sb.appendLine()
    }
    return sb.toString()
}
