package io.vanja.benchmarks

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A single benchmark measurement for one (solver, instance, time-limit)
 * triple. The runner collects a list of these, writes a CSV, and emits a
 * per-run JSON file with the full incumbent trajectory.
 */
data class RunRecord(
    val timestamp: String,
    val instanceId: String,
    val solver: String,
    val status: String,
    val objective: Double?,
    val bound: Double?,
    val gap: Double?,
    val solveSeconds: Double,
    val wallSeconds: Double,
    val incumbentTrajectory: List<TrajectoryPoint> = emptyList(),
)

/**
 * One point on a solver's incumbent-objective-over-time curve. For solvers
 * that don't report intermediate incumbents (the current minimal adapters
 * don't), only the final point is emitted.
 */
@Serializable
data class TrajectoryPoint(val tSeconds: Double, val objective: Double)

/**
 * Adapts an external solver process to a uniform benchmark interface:
 * "solve this instance for at most N seconds, tell me what happened."
 *
 * Implementations invoke a subprocess, parse its stdout/stderr, and translate
 * the result into a [RunRecord]. Subprocess-per-run isolates runs from each
 * other (no leaked state between solves) at the cost of JVM startup latency,
 * which is fine for any run budget >= a few seconds.
 */
interface SolverAdapter {
    val name: String
    fun run(instancePath: String, timeLimitSeconds: Int, projectRoot: String): RunRecord
}

internal val JSON_LENIENT: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}
