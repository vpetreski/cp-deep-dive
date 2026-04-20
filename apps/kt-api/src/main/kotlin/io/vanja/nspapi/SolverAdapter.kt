package io.vanja.nspapi

import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverSolutionCallback
import com.google.ortools.sat.CpSolverStatus
import io.vanja.cpsat.ensureNativesLoaded
import io.vanja.cpsat.nsp.Assignment
import io.vanja.cpsat.nsp.Decisions
import io.vanja.cpsat.nsp.Instance
import io.vanja.cpsat.nsp.ModelBuilder
import io.vanja.cpsat.nsp.ObjectiveWeights
import io.vanja.cpsat.nsp.Schedule
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opinionated solver adapter that takes an [Instance] + [SolverSpec] and
 * drives `cpsat-kt` + `nsp-core` end-to-end:
 *
 * 1. Builds the hard model via [ModelBuilder.buildHardModel].
 * 2. Adds soft objectives via [ModelBuilder.addSoftObjective] + minimizes them.
 * 3. Calls `CpSolver.solve` with a solution callback that reports every
 *    incumbent through [onIncumbent].
 * 4. Returns a final [SolveOutcome] describing the terminal state.
 *
 * The solver is blocking (OR-Tools is a JNI library); callers should invoke
 * [solveSuspending] to run it on [Dispatchers.IO].
 *
 * A cancel handle is exposed via [cancelHandleRef]; invoking it stops the
 * native search, which translates to a [JobStatus.CANCELLED] terminal state.
 */
public class SolverAdapter {

    public data class SolverSpec(
        val maxTimeSeconds: Double = 30.0,
        val numSearchWorkers: Int = 8,
        val randomSeed: Int = 1,
        val linearizationLevel: Int? = null,
        val relativeGapLimit: Double? = null,
        val logSearchProgress: Boolean = true,
        val weights: ObjectiveWeights = ObjectiveWeights.DEFAULT,
    )

    /** Terminal outcome of one solve. */
    public data class SolveOutcome(
        val status: String,
        val schedule: Schedule?,
        val objective: Long?,
        val bestBound: Long?,
        val gap: Double?,
        val solveTimeSeconds: Double,
        val searchLog: String,
        val error: String?,
    )

    /** A handle the caller can invoke to stop the native solver. */
    public class CancelHandle internal constructor(
        private val callback: AtomicReference<CpSolverSolutionCallback?>,
    ) {
        public fun cancel() {
            callback.get()?.stopSearch()
        }
    }

    /** Snapshot emitted from the solution callback. */
    public data class IncumbentSnapshot(
        val schedule: Schedule,
        val objective: Long,
        val bestBound: Long,
        val gap: Double,
        val solveTimeSeconds: Double,
    )

    /**
     * Run one solve. [onIncumbent] is called from the native callback thread for
     * every incumbent; it must not block (push into a channel or similar).
     *
     * [onLog] receives each log line when `logSearchProgress = true`.
     */
    public fun solve(
        instance: Instance,
        spec: SolverSpec,
        cancelHandleRef: AtomicReference<CancelHandle?> = AtomicReference(null),
        onIncumbent: (IncumbentSnapshot) -> Unit = {},
        onLog: (String) -> Unit = {},
    ): SolveOutcome {
        ensureNativesLoaded()
        val (model, decisions) = ModelBuilder.buildHardModel(instance)
        val softTerms = ModelBuilder.addSoftObjective(model, instance, decisions, spec.weights)
        softTerms.weightedSum()?.let { obj ->
            model.toJava().minimize(obj.asLinearArgument())
        }
        val validation = model.validate()
        if (validation.isNotEmpty()) {
            return SolveOutcome(
                status = JobStatus.MODEL_INVALID,
                schedule = null,
                objective = null,
                bestBound = null,
                gap = null,
                solveTimeSeconds = 0.0,
                searchLog = "",
                error = validation,
            )
        }

        val solver = CpSolver()
        val params = solver.parameters
        params.maxTimeInSeconds = spec.maxTimeSeconds
        params.numSearchWorkers = spec.numSearchWorkers.coerceAtLeast(1)
        params.randomSeed = spec.randomSeed
        params.logSearchProgress = spec.logSearchProgress
        spec.linearizationLevel?.let { params.linearizationLevel = it }
        spec.relativeGapLimit?.let { params.relativeGapLimit = it }

        val logBuffer = StringBuilder()
        if (spec.logSearchProgress) {
            solver.setLogCallback { line ->
                logBuffer.appendLine(line)
                try {
                    onLog(line)
                } catch (_: Throwable) {
                    // Swallow — logging is best-effort.
                }
            }
        }

        val callbackRef = AtomicReference<CpSolverSolutionCallback?>(null)
        val callback = object : CpSolverSolutionCallback() {
            override fun onSolutionCallback() {
                val snapshot = IncumbentSnapshot(
                    schedule = extractScheduleFromCallback(this, instance, decisions),
                    objective = this.objectiveValue().toLong(),
                    bestBound = this.bestObjectiveBound().toLong(),
                    gap = relativeGap(this.objectiveValue(), this.bestObjectiveBound()),
                    solveTimeSeconds = this.wallTime(),
                )
                try {
                    onIncumbent(snapshot)
                } catch (_: Throwable) {
                    // Never throw back into native code.
                }
            }
        }
        callbackRef.set(callback)
        cancelHandleRef.set(CancelHandle(callbackRef))

        val status = solver.solve(model.toJava(), callback)
        val wall = solver.wallTime()

        val (finalSchedule, finalObjective, finalBound, finalGap) = when (status) {
            CpSolverStatus.OPTIMAL, CpSolverStatus.FEASIBLE -> {
                val sched = extractScheduleFromSolver(solver, instance, decisions)
                val obj = solver.objectiveValue().toLong()
                val bound = solver.bestObjectiveBound().toLong()
                val gap = relativeGap(solver.objectiveValue(), solver.bestObjectiveBound())
                FinalFour(sched, obj, bound, gap)
            }
            else -> FinalFour(null, null, null, null)
        }

        val wireStatus = mapStatus(status, wall, spec.maxTimeSeconds)
        return SolveOutcome(
            status = wireStatus,
            schedule = finalSchedule,
            objective = finalObjective,
            bestBound = finalBound,
            gap = finalGap,
            solveTimeSeconds = wall,
            searchLog = logBuffer.toString(),
            error = null,
        )
    }

    /** Suspending variant — runs [solve] on [Dispatchers.IO]. */
    public suspend fun solveSuspending(
        instance: Instance,
        spec: SolverSpec,
        cancelHandleRef: AtomicReference<CancelHandle?> = AtomicReference(null),
        onIncumbent: (IncumbentSnapshot) -> Unit = {},
        onLog: (String) -> Unit = {},
    ): SolveOutcome = withContext(Dispatchers.IO) {
        try {
            solve(instance, spec, cancelHandleRef, onIncumbent, onLog)
        } catch (e: CancellationException) {
            cancelHandleRef.get()?.cancel()
            SolveOutcome(
                status = JobStatus.CANCELLED,
                schedule = null,
                objective = null,
                bestBound = null,
                gap = null,
                solveTimeSeconds = 0.0,
                searchLog = "",
                error = e.message,
            )
        } catch (e: Throwable) {
            SolveOutcome(
                status = JobStatus.ERROR,
                schedule = null,
                objective = null,
                bestBound = null,
                gap = null,
                solveTimeSeconds = 0.0,
                searchLog = "",
                error = e.message,
            )
        }
    }

    private data class FinalFour(
        val schedule: Schedule?,
        val objective: Long?,
        val bestBound: Long?,
        val gap: Double?,
    )

    private fun mapStatus(status: CpSolverStatus, wallTime: Double, limit: Double): String = when (status) {
        CpSolverStatus.OPTIMAL -> JobStatus.OPTIMAL
        CpSolverStatus.FEASIBLE -> if (wallTime >= limit * 0.99) JobStatus.TIMEOUT else JobStatus.FEASIBLE
        CpSolverStatus.INFEASIBLE -> JobStatus.INFEASIBLE
        CpSolverStatus.MODEL_INVALID -> JobStatus.MODEL_INVALID
        CpSolverStatus.UNKNOWN -> if (wallTime >= limit * 0.99) JobStatus.TIMEOUT else JobStatus.UNKNOWN
        CpSolverStatus.UNRECOGNIZED -> JobStatus.UNKNOWN
    }

    private fun relativeGap(objective: Double, bound: Double): Double {
        val obj = kotlin.math.abs(objective)
        val diff = kotlin.math.abs(objective - bound)
        val base = maxOf(1.0, obj)
        return diff / base
    }

    private fun extractScheduleFromSolver(
        solver: CpSolver,
        instance: Instance,
        decisions: Decisions,
    ): Schedule {
        val assignments = mutableListOf<Assignment>()
        for (n in instance.nurses) {
            for (d in 0 until instance.horizonDays) {
                var chosen: String? = null
                for (s in instance.shifts) {
                    val v = decisions.x[Triple(n.id, d, s.id)] ?: continue
                    if (solver.booleanValue(v.toJava())) {
                        chosen = s.id
                        break
                    }
                }
                assignments += Assignment(nurseId = n.id, day = d, shiftId = chosen)
            }
        }
        return Schedule(
            instanceId = instance.id,
            assignments = assignments,
            generatedAt = Instant.now().toString(),
        )
    }

    private fun extractScheduleFromCallback(
        cb: CpSolverSolutionCallback,
        instance: Instance,
        decisions: Decisions,
    ): Schedule {
        val assignments = mutableListOf<Assignment>()
        for (n in instance.nurses) {
            for (d in 0 until instance.horizonDays) {
                var chosen: String? = null
                for (s in instance.shifts) {
                    val v = decisions.x[Triple(n.id, d, s.id)] ?: continue
                    if (cb.booleanValue(v.toJava())) {
                        chosen = s.id
                        break
                    }
                }
                assignments += Assignment(nurseId = n.id, day = d, shiftId = chosen)
            }
        }
        return Schedule(
            instanceId = instance.id,
            assignments = assignments,
            generatedAt = Instant.now().toString(),
        )
    }
}
