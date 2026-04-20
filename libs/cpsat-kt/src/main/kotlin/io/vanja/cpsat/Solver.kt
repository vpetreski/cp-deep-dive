package io.vanja.cpsat

import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverStatus
import com.google.ortools.sat.SatParameters
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Strongly-typed solver parameters. Covers the most common knobs explicitly;
 * everything else is reachable via [rawProto] which exposes the
 * [SatParameters.Builder] directly.
 */
public class SolverParams {
    /** Hard wall-clock limit. */
    public var maxTimeInSeconds: Double? = null

    /**
     * Number of parallel worker threads. Default (null) lets CP-SAT choose
     * (usually 1 for small models, auto for larger ones).
     */
    public var numSearchWorkers: Int? = null

    /**
     * Seed for the solver's internal random choices. Set to a fixed value
     * for reproducible runs (vital in tests).
     */
    public var randomSeed: Int? = null

    /** When true, CP-SAT emits its search log to stdout via a log callback. */
    public var logSearchProgress: Boolean = false

    /** Enable/disable presolve. Default: true (rarely turn this off). */
    public var cpModelPresolve: Boolean = true

    /** `0..2`: how aggressively to linearize boolean constraints. */
    public var linearizationLevel: Int? = null

    /**
     * Branching strategy override (maps to SatParameters.SearchBranching).
     * Leave null to use the default (AUTOMATIC_SEARCH).
     */
    public var searchBranching: Int? = null

    /**
     * Stop on first feasible solution — useful when you just want *any*
     * assignment rather than the optimum.
     */
    public var stopAfterFirstSolution: Boolean = false

    /**
     * Number of solutions to enumerate in [CpModel.solveFlow]. Not a real
     * CP-SAT parameter — we use it to stop the streaming callback after N
     * solutions. Defaults to Int.MAX_VALUE (stream until the solver gives up).
     */
    public var maxSolutions: Int = Int.MAX_VALUE

    /**
     * Raw proto escape hatch: apply arbitrary [SatParameters] edits. Runs
     * *after* all typed fields above, so it can override them.
     */
    public var rawProto: ((SatParameters.Builder) -> Unit)? = null

    /** Let callers configure via a DSL-style call. */
    public fun rawProto(block: SatParameters.Builder.() -> Unit) {
        rawProto = { b -> b.block() }
    }

    internal fun applyTo(solver: CpSolver) {
        val p = solver.parameters
        maxTimeInSeconds?.let { p.maxTimeInSeconds = it }
        numSearchWorkers?.let { p.numSearchWorkers = it }
        randomSeed?.let { p.randomSeed = it }
        p.logSearchProgress = logSearchProgress
        p.cpModelPresolve = cpModelPresolve
        linearizationLevel?.let { p.linearizationLevel = it }
        searchBranching?.let { sb ->
            val branching = com.google.ortools.sat.SatParameters.SearchBranching.forNumber(sb)
                ?: error("Invalid searchBranching ordinal: $sb")
            p.searchBranching = branching
        }
        if (stopAfterFirstSolution) {
            // CP-SAT convention: stop after 1 feasible solution.
            p.enumerateAllSolutions = false
            p.stopAfterFirstSolution = true
        }
        rawProto?.invoke(p)
    }
}

/**
 * Sealed result returned by [CpModel.solve].
 */
public sealed interface SolveResult {
    /** Optimal solution found (status = OPTIMAL). */
    public data class Optimal(val values: Assignment, val objective: Long) : SolveResult

    /**
     * Feasible solution found but optimality not proven (time limit, etc.).
     * [objective] is the best found, [bound] is the best known lower/upper
     * bound from the solver. [gap] is the normalized optimality gap
     * `|obj - bound| / max(1, |obj|)`.
     */
    public data class Feasible(
        val values: Assignment,
        val objective: Long,
        val bound: Long,
        val gap: Double,
    ) : SolveResult

    /** No solution exists (proven). */
    public data object Infeasible : SolveResult

    /** Solver terminated without concluding. */
    public data object Unknown : SolveResult

    /** Model failed validation. [message] contains the native validator output. */
    public data class ModelInvalid(val message: String) : SolveResult
}

/**
 * Assignment of values to variables in a solution. Access values with
 * `result[variable]` — an operator on [IntVar], [BoolVar], and [IntervalVar].
 */
public class Assignment internal constructor(private val solver: CpSolver) {
    public operator fun get(v: IntVar): Long = solver.value(v.java)
    public operator fun get(v: BoolVar): Boolean = solver.booleanValue(v.java)
    public operator fun get(v: IntervalVar): IntervalValue {
        // Build interval value from the underlying expressions' evaluated values.
        val start = solver.value(v.start.asLinearArgument())
        val size = solver.value(v.size.asLinearArgument())
        val end = solver.value(v.end.asLinearArgument())
        val present = v.presenceLiteral?.let { solver.booleanValue(it.java) } ?: true
        return IntervalValue(start, size, end, present)
    }

    /** Evaluate a general [LinearExpr] against the solution. */
    public fun valueOf(expr: LinearExpr): Long = solver.value(expr.asLinearArgument())
}

/**
 * A solved interval's concrete values — `start + size == end`, `present`
 * is false only for optional intervals the solver chose to skip.
 */
public data class IntervalValue(
    val start: Long,
    val size: Long,
    val end: Long,
    val present: Boolean,
)

// -----------------------------------------------------------------------------
// Solve entry points
// -----------------------------------------------------------------------------

/**
 * Blocking solve. Prefer [solve] (suspending) in async code.
 */
public fun CpModel.solveBlocking(params: SolverParams.() -> Unit = {}): SolveResult {
    ensureNativesLoaded()
    val validation = validate()
    if (validation.isNotEmpty()) {
        return SolveResult.ModelInvalid(validation)
    }
    val solver = CpSolver()
    SolverParams().apply(params).applyTo(solver)
    val status = solver.solve(toJava())
    return buildResult(solver, status)
}

/**
 * Suspending solve. Runs the (blocking) CP-SAT solve on [Dispatchers.Default]
 * so it doesn't block the calling coroutine's thread.
 */
public suspend fun CpModel.solve(params: SolverParams.() -> Unit = {}): SolveResult =
    withContext(Dispatchers.Default) { solveBlocking(params) }

internal fun buildResult(solver: CpSolver, status: CpSolverStatus): SolveResult {
    val assignment = Assignment(solver)
    return when (status) {
        CpSolverStatus.OPTIMAL -> SolveResult.Optimal(
            values = assignment,
            objective = solver.objectiveValue().toLong(),
        )
        CpSolverStatus.FEASIBLE -> {
            val obj = solver.objectiveValue().toLong()
            val bound = solver.bestObjectiveBound().toLong()
            val gap = if (obj == 0L) abs((obj - bound).toDouble()) else abs((obj - bound).toDouble()) / maxOf(1.0, abs(obj.toDouble()))
            SolveResult.Feasible(assignment, obj, bound, gap)
        }
        CpSolverStatus.INFEASIBLE -> SolveResult.Infeasible
        CpSolverStatus.MODEL_INVALID -> SolveResult.ModelInvalid("CP-SAT returned MODEL_INVALID")
        CpSolverStatus.UNKNOWN -> SolveResult.Unknown
        CpSolverStatus.UNRECOGNIZED -> SolveResult.Unknown
    }
}

/**
 * Solve a lexicographic sequence of objectives. Each stage is optimized in
 * turn; after each, a constraint is added fixing its objective at the best
 * value before solving the next. Returns the result of the *final* stage
 * (so you always see the best balance across all stages).
 *
 * This is implemented in pure Kotlin because CP-SAT has no built-in
 * multi-objective lexicographic mode.
 */
public fun CpModel.solveLexicographic(
    stages: List<LexStage>,
    params: SolverParams.() -> Unit = {},
): SolveResult {
    require(stages.isNotEmpty()) { "solveLexicographic: need at least one stage" }
    ensureNativesLoaded()
    val validation = validate()
    if (validation.isNotEmpty()) return SolveResult.ModelInvalid(validation)

    var last: SolveResult = SolveResult.Unknown
    for ((index, stage) in stages.withIndex()) {
        // Clear any previous objective.
        toJava().clearObjective()
        val expr = stage.block.invoke(this)
        when (stage.sense) {
            Sense.MINIMIZE -> toJava().minimize(expr.asLinearArgument())
            Sense.MAXIMIZE -> toJava().maximize(expr.asLinearArgument())
        }
        val solver = CpSolver()
        SolverParams().apply(params).applyTo(solver)
        val status = solver.solve(toJava())
        last = buildResult(solver, status)
        when (last) {
            is SolveResult.Optimal -> {
                // Freeze this stage's optimum before moving on.
                if (index < stages.size - 1) {
                    val optObj = (last as SolveResult.Optimal).objective
                    // Constrain the objective expression to its optimum.
                    toJava().addEquality(expr.asLinearArgument(), optObj)
                }
            }
            is SolveResult.Feasible -> {
                // If we only got a feasible (not optimal), we cap to the
                // found value — it's a best-effort lex procedure.
                if (index < stages.size - 1) {
                    val obj = (last as SolveResult.Feasible).objective
                    if (stage.sense == Sense.MINIMIZE) {
                        toJava().addLessOrEqual(expr.asLinearArgument(), obj)
                    } else {
                        toJava().addGreaterOrEqual(expr.asLinearArgument(), obj)
                    }
                }
            }
            // Non-feasible: short-circuit — no point continuing.
            is SolveResult.Infeasible, is SolveResult.Unknown, is SolveResult.ModelInvalid -> return last
        }
    }
    return last
}
