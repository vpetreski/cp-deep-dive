package io.vanja.cpsat.ch12

import io.vanja.cpsat.BoolVar
import io.vanja.cpsat.LinearExpr
import io.vanja.cpsat.Sense
import io.vanja.cpsat.SolveResult as CpSolveResult
import io.vanja.cpsat.lexicographic
import io.vanja.cpsat.minimize
import io.vanja.cpsat.nsp.Assignment
import io.vanja.cpsat.nsp.Decisions
import io.vanja.cpsat.nsp.Instance
import io.vanja.cpsat.nsp.ModelBuilder
import io.vanja.cpsat.nsp.ObjectiveWeights
import io.vanja.cpsat.nsp.Schedule
import io.vanja.cpsat.nsp.SoftTerms
import io.vanja.cpsat.nsp.SolveParams
import io.vanja.cpsat.nsp.SolveResult as NspSolveResult
import io.vanja.cpsat.solveBlocking
import io.vanja.cpsat.solveLexicographic
import io.vanja.cpsat.times
import java.time.Clock
import java.time.Instant
import kotlin.time.TimeSource

/**
 * Chapter 12 entry points.
 *
 * Two flavors:
 *
 * - [solveWeightedSum]: classic scalarization — minimize `∑ wᵢ · SCᵢ`.
 *   Fast, deterministic, but tuning the weights is an art.
 * - [solveLexicographic]: staged optimization — first minimize SC-1, then
 *   within that optimum minimize SC-2, and so on. Heavier, but gives a clean
 *   "priority order" interpretation.
 */
public fun solveWeightedSum(
    instance: Instance,
    params: SolveParams = SolveParams(),
    clock: Clock = Clock.systemUTC(),
): NspSolveResult {
    val (model, dec) = ModelBuilder.buildHardModel(instance)
    val soft = ModelBuilder.addSoftObjective(model, instance, dec, params.objectiveWeights)
    val objective = soft.weightedSum()
    if (objective != null) {
        model.minimize { objective }
    }
    val mark = TimeSource.Monotonic.markNow()
    val result = model.solveBlocking {
        maxTimeInSeconds = params.maxTimeSeconds
        numSearchWorkers = params.numSearchWorkers
        params.randomSeed?.let { randomSeed = it }
        logSearchProgress = params.logSearchProgress
        params.linearizationLevel?.let { linearizationLevel = it }
    }
    return translate(result, instance, dec, mark.elapsedNow().inWholeMilliseconds / 1000.0, clock)
}

/**
 * Lexicographic variant: each SC family gets its own optimization stage, in
 * the declaration order SC-1..SC-5 with family weight > 0 included.
 */
public fun solveLex(
    instance: Instance,
    params: SolveParams = SolveParams(),
    clock: Clock = Clock.systemUTC(),
): NspSolveResult {
    val (model, dec) = ModelBuilder.buildHardModel(instance)
    val soft = ModelBuilder.addSoftObjective(model, instance, dec, params.objectiveWeights)
    val weights = params.objectiveWeights
    val stageExprs = lexStages(soft, weights)
    if (stageExprs.isEmpty()) {
        // Nothing soft to optimize — fall back to hard-only feasibility.
        val mark = TimeSource.Monotonic.markNow()
        val result = model.solveBlocking {
            maxTimeInSeconds = params.maxTimeSeconds
            numSearchWorkers = params.numSearchWorkers
            params.randomSeed?.let { randomSeed = it }
            logSearchProgress = params.logSearchProgress
        }
        return translate(result, instance, dec, mark.elapsedNow().inWholeMilliseconds / 1000.0, clock)
    }
    val stages = model.lexicographic {
        for ((_, expr) in stageExprs) {
            stage(Sense.MINIMIZE) { expr }
        }
    }
    val mark = TimeSource.Monotonic.markNow()
    val result = model.solveLexicographic(stages) {
        maxTimeInSeconds = params.maxTimeSeconds
        numSearchWorkers = params.numSearchWorkers
        params.randomSeed?.let { randomSeed = it }
        logSearchProgress = params.logSearchProgress
        params.linearizationLevel?.let { linearizationLevel = it }
    }
    return translate(result, instance, dec, mark.elapsedNow().inWholeMilliseconds / 1000.0, clock)
}

/**
 * Pick the top-level entry point according to [SolveParams.lexicographic].
 * Used by the CLI and kt-api; chapters can also call the concrete variants
 * directly.
 */
public fun solve(
    instance: Instance,
    params: SolveParams = SolveParams(),
    clock: Clock = Clock.systemUTC(),
): NspSolveResult =
    if (params.lexicographic) solveLex(instance, params, clock)
    else solveWeightedSum(instance, params, clock)

private fun lexStages(
    soft: SoftTerms,
    weights: ObjectiveWeights,
): List<Pair<String, LinearExpr>> = buildList {
    // Include only families with non-zero weight AND non-null expression.
    soft.sc1?.takeIf { weights.sc1 > 0 }?.let { add("SC-1" to it) }
    soft.sc2?.takeIf { weights.sc2 > 0 }?.let { add("SC-2" to it) }
    soft.sc3?.takeIf { weights.sc3 > 0 }?.let { add("SC-3" to it) }
    soft.sc4?.takeIf { weights.sc4 > 0 }?.let { add("SC-4" to it) }
    soft.sc5?.takeIf { weights.sc5 > 0 }?.let { add("SC-5" to it) }
}

private fun translate(
    result: CpSolveResult,
    instance: Instance,
    dec: Decisions,
    elapsed: Double,
    clock: Clock,
): NspSolveResult = when (result) {
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
