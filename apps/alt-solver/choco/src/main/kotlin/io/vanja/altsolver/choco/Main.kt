package io.vanja.altsolver.choco

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import kotlin.system.measureTimeMillis

/**
 * CLI entry point for the Choco NSP solver.
 *
 * ```
 * ./gradlew run --args="--instance ../../../data/nsp/toy-01.json --time-limit 30"
 * ```
 *
 * Produces a JSON schedule on stdout that matches
 * `apps/shared/schemas/schedule.schema.json`. Human-readable diagnostics go to
 * stderr so the stdout stream stays parseable for the benchmark runner.
 */
fun main(args: Array<String>) {
    val parsed = parseArgs(args) ?: kotlin.system.exitProcess(2)
    val result = solveNsp(parsed.instancePath, parsed.timeLimitSeconds)

    // Human-readable diagnostics on stderr so stdout is pure JSON.
    System.err.println(
        "Instance: ${result.instanceId}  " +
            "(${result.horizonDays}d x ${result.numNurses}n " +
            "x ${result.numShifts}s)",
    )
    System.err.println("Time limit: ${parsed.timeLimitSeconds}s")
    System.err.println("Feasible: ${result.feasible}   Objective: ${result.objective}")
    System.err.println("Wall time: ${result.wallTimeMs}ms")

    println(renderScheduleJson(result))
}

/** Parsed CLI arguments. */
internal data class Cli(
    val instancePath: Path,
    val timeLimitSeconds: Long,
)

internal fun parseArgs(args: Array<String>): Cli? {
    var instance: Path? = null
    var timeLimit: Long = 30L
    var i = 0
    while (i < args.size) {
        when (val a = args[i]) {
            "--instance" -> {
                instance = Paths.get(requireNotNull(args.getOrNull(i + 1)) { "--instance needs a value" })
                i += 2
            }
            "--time-limit" -> {
                timeLimit = requireNotNull(args.getOrNull(i + 1)?.toLongOrNull()) {
                    "--time-limit needs an integer seconds value"
                }
                i += 2
            }
            "-h", "--help" -> {
                System.err.println(USAGE)
                return null
            }
            else -> {
                System.err.println("Unknown flag: $a\n$USAGE")
                return null
            }
        }
    }
    if (instance == null) {
        System.err.println("Missing --instance\n$USAGE")
        return null
    }
    return Cli(instancePath = instance, timeLimitSeconds = timeLimit)
}

private const val USAGE =
    "Usage: choco --instance <instance.json> [--time-limit <seconds>=30]"

/**
 * Run the Choco solver on the instance at [instancePath] for up to
 * [timeLimitSeconds] seconds. Returns a [ChocoSolveResult] with the best
 * incumbent schedule the solver found (or empty assignments if infeasible).
 */
fun solveNsp(instancePath: Path, timeLimitSeconds: Long): ChocoSolveResult {
    val instance = loadInstance(instancePath)
    val compiled = NspModelBuilder.compile(instance)
    val solver = compiled.model.solver

    // Choco's solver.limitTime accepts a duration string: "30s", "1m30s", ...
    solver.limitTime("${timeLimitSeconds}s")

    var bestObjective: Int? = null
    var bestAssignments: List<ScheduleAssignment> = emptyList()

    val wallMs = measureTimeMillis {
        // `findOptimalSolution` returns the last (optimal) solution found; we
        // iterate manually so we can record the best incumbent if the time
        // limit fires before proving optimality.
        while (solver.solve()) {
            val objValue = compiled.objective.value
            val snapshot = extractAssignments(compiled)
            bestObjective = objValue
            bestAssignments = snapshot
        }
    }

    val feasible = bestObjective != null
    return ChocoSolveResult(
        instanceId = instance.id ?: "anon",
        horizonDays = instance.horizonDays,
        numNurses = instance.nurses.size,
        numShifts = instance.shifts.size,
        assignments = bestAssignments,
        objective = bestObjective,
        feasible = feasible,
        wallTimeMs = wallMs,
        timeLimitSeconds = timeLimitSeconds,
    )
}

/** Extract the current Choco solution as a flat assignment list. */
private fun extractAssignments(c: NspCompiled): List<ScheduleAssignment> {
    val out = mutableListOf<ScheduleAssignment>()
    for (n in 0 until c.nN) {
        val nurseId = c.nurseIds[n]
        for (d in 0 until c.nD) {
            val v = c.x[n][d].value
            if (v != c.OFF) {
                out += ScheduleAssignment(
                    nurseId = nurseId,
                    day = d,
                    shiftId = c.shiftIds[v],
                )
            }
        }
    }
    // Same ordering as Timefold's toScheduleAssignments() so diff tools are happy.
    return out.sortedWith(compareBy({ it.day }, { it.shiftId }, { it.nurseId }))
}

/**
 * A flat `(nurseId, day, shiftId)` row. Same shape as the Timefold module's
 * record so cross-solver diffs stay textually clean.
 */
data class ScheduleAssignment(val nurseId: String, val day: Int, val shiftId: String)

/** Everything the CLI, benchmark runner, and tests need out of one solve. */
data class ChocoSolveResult(
    val instanceId: String,
    val horizonDays: Int,
    val numNurses: Int,
    val numShifts: Int,
    val assignments: List<ScheduleAssignment>,
    val objective: Int?,
    val feasible: Boolean,
    val wallTimeMs: Long,
    val timeLimitSeconds: Long,
)

/**
 * Serialise a [ChocoSolveResult] into the shape
 * `apps/shared/schemas/schedule.schema.json` expects.
 *
 * The schema allows additional keys to be absent when the data is not
 * available, so we emit only `instanceId`, `assignments`, and `generatedAt`
 * — enough for validators and downstream pipelines.
 */
@OptIn(ExperimentalSerializationApi::class)
fun renderScheduleJson(result: ChocoSolveResult): String {
    val payload = ScheduleJson(
        instanceId = result.instanceId,
        generatedAt = Instant.now().toString(),
        assignments = result.assignments.map {
            AssignmentJson(nurseId = it.nurseId, day = it.day, shiftId = it.shiftId)
        },
    )
    return JSON_OUT.encodeToString(ScheduleJson.serializer(), payload)
}

@kotlinx.serialization.Serializable
internal data class ScheduleJson(
    val instanceId: String,
    val generatedAt: String,
    val assignments: List<AssignmentJson>,
)

@kotlinx.serialization.Serializable
internal data class AssignmentJson(
    val nurseId: String,
    val day: Int,
    val shiftId: String,
)

private val JSON_OUT: Json = Json {
    prettyPrint = true
    encodeDefaults = true
}
