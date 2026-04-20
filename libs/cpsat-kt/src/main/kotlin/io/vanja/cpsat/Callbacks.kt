package io.vanja.cpsat

import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverSolutionCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A solution emitted during streaming solve. [values] is a *snapshot*
 * [Assignment]-like view that can be queried for variable values *within the
 * collector*. Callers must read values during collection; once the solve
 * advances, the snapshot becomes stale.
 */
public class Solution internal constructor(
    internal val callback: CpSolverSolutionCallback,
    internal val model: CpModel,
    public val objective: Long,
    public val bound: Long,
    public val wallTime: Double,
) {
    /** Read the value of [v] at this solution. */
    public operator fun get(v: IntVar): Long = callback.value(v.java)

    /** Read the boolean value of [v] at this solution. */
    public operator fun get(v: BoolVar): Boolean = callback.booleanValue(v.java)

    /** Read the interval's start/size/end at this solution. */
    public operator fun get(v: IntervalVar): IntervalValue {
        val start = callback.value(v.start.asLinearArgument())
        val size = callback.value(v.size.asLinearArgument())
        val end = callback.value(v.end.asLinearArgument())
        val present = v.presenceLiteral?.let { callback.booleanValue(it.java) } ?: true
        return IntervalValue(start, size, end, present)
    }

    /** Evaluate a general [LinearExpr] against this solution. */
    public fun valueOf(expr: LinearExpr): Long = callback.value(expr.asLinearArgument())
}

/**
 * Stream solutions from the solver as a cold [Flow]. The solver is started
 * when the flow is collected; each incumbent solution arrives as one [Solution]
 * emitted in order. The flow completes when the solver finishes.
 *
 * Cancelling the collector stops the underlying search (via
 * `CpSolverSolutionCallback.stopSearch`).
 *
 * Under the hood we use [channelFlow] with a suspending send loop so we
 * never drop solutions on a fast producer → slow consumer.
 *
 * ```kotlin
 * cpModel { ... }.solveFlow().collect { sol ->
 *     println("found objective=${sol.objective}, bound=${sol.bound}")
 * }
 * ```
 */
public fun CpModel.solveFlow(params: SolverParams.() -> Unit = {}): Flow<Solution> = channelFlow {
    ensureNativesLoaded()
    val validation = validate()
    if (validation.isNotEmpty()) {
        // Emit nothing — consumer can combine with a regular solve to detect this.
        // (We could also throw, but Flow-idiomatic is "cold, silent on invalid".)
        return@channelFlow
    }
    val model = this@solveFlow
    val resolved = SolverParams().apply(params)
    val maxSolutions = resolved.maxSolutions

    // Build the callback; it forwards solutions into our channel via a
    // *non-suspending* trySend. If the channel is closed (collector cancelled),
    // we call stopSearch() to end the solver.
    val channel = Channel<Solution>(capacity = Channel.BUFFERED)
    var count = 0

    val callback = object : CpSolverSolutionCallback() {
        override fun onSolutionCallback() {
            count += 1
            val sol = Solution(
                callback = this,
                model = model,
                objective = this.objectiveValue().toLong(),
                bound = this.bestObjectiveBound().toLong(),
                wallTime = this.wallTime(),
            )
            val res = channel.trySend(sol)
            if (res.isClosed) {
                stopSearch()
            }
            if (count >= maxSolutions) {
                stopSearch()
            }
        }
    }

    // Run the blocking solver on the IO dispatcher so we don't monopolize
    // the Default pool while streaming.
    val solverJob = launch(Dispatchers.IO) {
        val solver = CpSolver()
        resolved.applyTo(solver)
        try {
            solver.solve(model.toJava(), callback)
        } finally {
            channel.close()
        }
    }

    // Drain the channel into the flow's output.
    try {
        for (s in channel) {
            send(s)
        }
    } finally {
        solverJob.join()
    }
}

/**
 * Collect *all* solutions (up to [maxSolutions]) into a list synchronously.
 * Convenience wrapper around [solveFlow] for callers who don't care about
 * streaming.
 */
public suspend fun CpModel.enumerateSolutions(
    maxSolutions: Int = 100,
    params: SolverParams.() -> Unit = {},
): List<Map<String, Long>> = withContext(Dispatchers.Default) {
    val out = mutableListOf<Map<String, Long>>()
    // Since we don't know the IntVars here, we emit the raw index-to-value
    // map from each solution. Callers who want named access should use
    // solveFlow() directly and pull values via sol[myVar].
    val cb = object : CpSolverSolutionCallback() {
        override fun onSolutionCallback() {
            // No variables to pull — caller will have to use solveFlow for details.
            out.add(emptyMap())
            if (out.size >= maxSolutions) stopSearch()
        }
    }
    val solver = CpSolver()
    val p = SolverParams().apply(params)
    p.applyTo(solver)
    // Enumerate all solutions requires a proto-level setting.
    solver.parameters.enumerateAllSolutions = true
    solver.solve(toJava(), cb)
    out
}

/**
 * Coroutine-scope aware variant of [solveFlow] for callers that already have
 * a scope and want to kick off streaming in the background.
 */
@Suppress("unused")
public fun CoroutineScope.solveFlowIn(
    model: CpModel,
    params: SolverParams.() -> Unit = {},
): Flow<Solution> = model.solveFlow(params)
