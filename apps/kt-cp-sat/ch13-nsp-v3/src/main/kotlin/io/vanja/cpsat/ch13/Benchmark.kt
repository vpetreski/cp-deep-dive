package io.vanja.cpsat.ch13

import io.vanja.cpsat.ch12.solveWeightedSum
import io.vanja.cpsat.nsp.Instance
import io.vanja.cpsat.nsp.ObjectiveWeights
import io.vanja.cpsat.nsp.SolveParams
import io.vanja.cpsat.nsp.SolveResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import kotlin.time.TimeSource

/**
 * Benchmark runner.
 *
 * Produces a CSV row per (instance, SolverParams variant) with columns:
 * `timestamp, instance_id, nurses, days, shifts, variant, status, objective,
 *  bound, gap, solve_seconds, wall_seconds`.
 */
public object Benchmark {

    /** One variant of solver configuration to compare. */
    public data class Variant(
        val name: String,
        val params: SolveParams,
    )

    /** One row of the CSV output. */
    public data class Row(
        val instanceId: String,
        val nurses: Int,
        val days: Int,
        val shifts: Int,
        val variant: String,
        val status: SolveResult.Status,
        val objective: Long?,
        val bound: Long?,
        val gap: Double?,
        val solveSeconds: Double,
        val wallSeconds: Double,
    ) {
        public fun toCsv(timestamp: String): String = listOf(
            timestamp,
            instanceId,
            nurses, days, shifts,
            variant,
            status.name,
            objective?.toString() ?: "",
            bound?.toString() ?: "",
            gap?.let { "%.4f".format(it) } ?: "",
            "%.4f".format(solveSeconds),
            "%.4f".format(wallSeconds),
        ).joinToString(",")
    }

    /** CSV header matching [Row.toCsv]. */
    public const val HEADER: String =
        "timestamp,instance_id,nurses,days,shifts,variant,status,objective,bound,gap,solve_seconds,wall_seconds"

    /**
     * Run every [variants] over every [instances] and return one [Row] per
     * combination. Errors surface as rows with status MODEL_INVALID and the
     * message in the objective column.
     */
    public fun run(instances: List<Instance>, variants: List<Variant>): List<Row> {
        val out = mutableListOf<Row>()
        for (inst in instances) {
            for (variant in variants) {
                val mark = TimeSource.Monotonic.markNow()
                val result = solveWeightedSum(inst, variant.params)
                val wall = mark.elapsedNow().inWholeMilliseconds / 1000.0
                out += toRow(inst, variant, result, wall)
            }
        }
        return out
    }

    /** Append rows to [path], writing the header if the file is new. */
    public fun writeCsv(path: Path, rows: List<Row>) {
        val parent = path.parent
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent)
        }
        val existed = Files.exists(path)
        val timestamp = Instant.now().toString()
        val lines = buildList {
            if (!existed) add(HEADER)
            for (r in rows) add(r.toCsv(timestamp))
        }
        val body = lines.joinToString(System.lineSeparator(), postfix = System.lineSeparator())
        Files.writeString(
            path,
            body,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND,
        )
    }

    private fun toRow(
        inst: Instance,
        variant: Variant,
        result: SolveResult,
        wall: Double,
    ): Row {
        val shiftsCount = inst.shifts.size
        return when (result) {
            is SolveResult.Optimal -> Row(
                instanceId = inst.id,
                nurses = inst.nurses.size, days = inst.horizonDays, shifts = shiftsCount,
                variant = variant.name,
                status = result.status,
                objective = result.objective,
                bound = result.objective,
                gap = 0.0,
                solveSeconds = result.solveTimeSeconds,
                wallSeconds = wall,
            )
            is SolveResult.Feasible -> Row(
                instanceId = inst.id,
                nurses = inst.nurses.size, days = inst.horizonDays, shifts = shiftsCount,
                variant = variant.name,
                status = result.status,
                objective = result.objective,
                bound = result.bestBound,
                gap = result.gap,
                solveSeconds = result.solveTimeSeconds,
                wallSeconds = wall,
            )
            is SolveResult.Infeasible -> baseRow(inst, variant, result.status, wall, result.solveTimeSeconds)
            is SolveResult.Unknown -> baseRow(inst, variant, result.status, wall, result.solveTimeSeconds)
            is SolveResult.ModelInvalid -> baseRow(inst, variant, result.status, wall, result.solveTimeSeconds)
        }
    }

    private fun baseRow(
        inst: Instance,
        variant: Variant,
        status: SolveResult.Status,
        wall: Double,
        solve: Double,
    ): Row = Row(
        instanceId = inst.id,
        nurses = inst.nurses.size, days = inst.horizonDays, shifts = inst.shifts.size,
        variant = variant.name,
        status = status,
        objective = null, bound = null, gap = null,
        solveSeconds = solve, wallSeconds = wall,
    )

    /** Handy preset of variants covering the common tuning knobs. */
    public fun defaultVariants(timeSeconds: Double = 60.0, workers: Int = 8): List<Variant> = listOf(
        Variant(
            "default",
            SolveParams(maxTimeSeconds = timeSeconds, numSearchWorkers = workers, objectiveWeights = ObjectiveWeights.DEFAULT),
        ),
        Variant(
            "linearization-2",
            SolveParams(
                maxTimeSeconds = timeSeconds,
                numSearchWorkers = workers,
                linearizationLevel = 2,
                objectiveWeights = ObjectiveWeights.DEFAULT,
            ),
        ),
        Variant(
            "single-worker",
            SolveParams(
                maxTimeSeconds = timeSeconds,
                numSearchWorkers = 1,
                objectiveWeights = ObjectiveWeights.DEFAULT,
            ),
        ),
    )
}
