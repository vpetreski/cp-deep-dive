package io.vanja.cpsat.ch11

import io.vanja.cpsat.BoolVar
import io.vanja.cpsat.SolveResult as CpSolveResult
import io.vanja.cpsat.nsp.Assignment
import io.vanja.cpsat.nsp.Decisions
import io.vanja.cpsat.nsp.Instance
import io.vanja.cpsat.nsp.ModelBuilder
import io.vanja.cpsat.nsp.Schedule
import io.vanja.cpsat.nsp.SolveParams
import io.vanja.cpsat.nsp.SolveResult as NspSolveResult
import io.vanja.cpsat.solveBlocking
import java.time.Clock
import java.time.Instant
import kotlin.time.TimeSource

/**
 * Chapter 11 entry point: solve [instance] with only the hard constraints.
 *
 * This chapter ignores soft objectives entirely — the solver returns the
 * first feasible schedule (or proves infeasibility). Later chapters add soft
 * objectives on top of the same [ModelBuilder].
 */
public fun solve(
    instance: Instance,
    params: SolveParams = SolveParams(),
    clock: Clock = Clock.systemUTC(),
): NspSolveResult {
    val (model, dec) = ModelBuilder.buildHardModel(instance)
    val mark = TimeSource.Monotonic.markNow()
    val result = model.solveBlocking {
        maxTimeInSeconds = params.maxTimeSeconds
        numSearchWorkers = params.numSearchWorkers
        params.randomSeed?.let { randomSeed = it }
        logSearchProgress = params.logSearchProgress
        params.linearizationLevel?.let { linearizationLevel = it }
        // Chapter 11 is a feasibility problem — stop as soon as the first
        // feasible assignment is found. We don't have an objective to optimize.
        stopAfterFirstSolution = true
    }
    val elapsed = mark.elapsedNow().inWholeMilliseconds / 1000.0
    return when (result) {
        is CpSolveResult.Optimal -> NspSolveResult.Optimal(
            schedule = buildSchedule(instance, dec, result.values, clock),
            objective = result.objective,
            solveTimeSeconds = elapsed,
        )
        is CpSolveResult.Feasible -> NspSolveResult.Feasible(
            schedule = buildSchedule(instance, dec, result.values, clock),
            objective = result.objective,
            bestBound = result.bound,
            gap = result.gap,
            solveTimeSeconds = elapsed,
        )
        is CpSolveResult.Infeasible -> NspSolveResult.Infeasible(
            violations = emptyList(),
            solveTimeSeconds = elapsed,
        )
        is CpSolveResult.Unknown -> NspSolveResult.Unknown(solveTimeSeconds = elapsed)
        is CpSolveResult.ModelInvalid -> NspSolveResult.ModelInvalid(
            message = result.message,
            solveTimeSeconds = elapsed,
        )
    }
}

/** Extract the assignment grid into a [Schedule] — days-off are emitted as `shiftId = null`. */
private fun buildSchedule(
    instance: Instance,
    dec: Decisions,
    values: io.vanja.cpsat.Assignment,
    clock: Clock,
): Schedule {
    val assignments = mutableListOf<Assignment>()
    for (n in instance.nurses) {
        for (d in 0 until instance.horizonDays) {
            val chosen = instance.shifts.firstOrNull { s ->
                val v: BoolVar = dec.x[Triple(n.id, d, s.id)] ?: return@firstOrNull false
                values[v]
            }
            assignments += Assignment(nurseId = n.id, day = d, shiftId = chosen?.id)
        }
    }
    return Schedule(
        instanceId = instance.id,
        assignments = assignments,
        violations = emptyList(),
        generatedAt = Instant.now(clock).toString(),
    )
}
