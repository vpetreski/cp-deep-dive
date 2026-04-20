package io.vanja.benchmarks

import io.vanja.benchmarks.adapters.ChocoAdapter
import io.vanja.benchmarks.adapters.CpSatAdapter
import io.vanja.benchmarks.adapters.TimefoldAdapter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * CLI benchmark harness. Runs each solver on each instance for up to
 * `time-limit` seconds and writes:
 *
 * 1. One CSV row per (solver, instance) to `<out>/<timestamp>/results.csv`
 *    with columns: timestamp, instance_id, solver, status, objective,
 *    bound, gap, solve_seconds, wall_seconds.
 * 2. One JSON file per run to
 *    `<out>/<timestamp>/<instance>-<solver>.json` with the full incumbent
 *    trajectory + metadata.
 *
 * Shell expansion on `--instances` is honoured by the caller; we also
 * accept a glob pattern.
 *
 * ```
 * ./gradlew run --args="--solvers cpsat,timefold,choco \
 *     --instances data/nsp/toy-*.json --time-limit 30 \
 *     --out benchmarks/results/"
 * ```
 */
fun main(args: Array<String>) {
    val parsed = parseArgs(args) ?: kotlin.system.exitProcess(2)
    val projectRoot = parsed.projectRoot.absolutePath
    val runId = parsed.runId ?: timestampId()
    val outDir = parsed.outDir.resolve(runId).toAbsolutePath()
    Files.createDirectories(outDir)

    val adapters = parsed.solvers.mapNotNull { name ->
        when (name.lowercase()) {
            "cpsat" -> CpSatAdapter()
            "timefold" -> TimefoldAdapter()
            "choco" -> ChocoAdapter()
            else -> {
                System.err.println("Unknown solver: $name (skipping)")
                null
            }
        }
    }

    val instances = expandInstances(parsed.instances, parsed.projectRoot)
    if (instances.isEmpty()) {
        System.err.println("No instances matched: ${parsed.instances}")
        kotlin.system.exitProcess(2)
    }

    System.err.println(
        "Running ${adapters.size} solvers x ${instances.size} instances " +
            "at ${parsed.timeLimitSeconds}s each -> $outDir",
    )

    val records = mutableListOf<RunRecord>()
    for (instance in instances) {
        for (adapter in adapters) {
            System.err.println("-- ${adapter.name} on ${instance.fileName} --")
            val rec = try {
                adapter.run(
                    instancePath = instance.toString(),
                    timeLimitSeconds = parsed.timeLimitSeconds,
                    projectRoot = projectRoot,
                )
            } catch (e: Exception) {
                System.err.println("  failed: ${e.message}")
                RunRecord(
                    timestamp = Instant.now().toString(),
                    instanceId = instance.fileName.toString().removeSuffix(".json"),
                    solver = adapter.name,
                    status = "error",
                    objective = null,
                    bound = null,
                    gap = null,
                    solveSeconds = 0.0,
                    wallSeconds = 0.0,
                )
            }
            records += rec
            writeRunJson(outDir, rec)
            System.err.println(
                "  status=${rec.status}  obj=${rec.objective}  " +
                    "solve=${"%.2f".format(rec.solveSeconds)}s  " +
                    "wall=${"%.2f".format(rec.wallSeconds)}s",
            )
        }
    }

    writeCsv(outDir, records)
    System.err.println("Wrote ${records.size} records to $outDir")
}

/** Parsed CLI options. */
internal data class Cli(
    val solvers: List<String>,
    val instances: List<String>,
    val timeLimitSeconds: Int,
    val outDir: Path,
    val projectRoot: File,
    val runId: String?,
)

internal fun parseArgs(args: Array<String>): Cli? {
    var solvers: List<String> = listOf("cpsat", "timefold", "choco")
    var instances: List<String> = emptyList()
    var timeLimit: Int = 30
    var out: Path = Paths.get("benchmarks", "results")
    var runId: String? = null
    var root: File = defaultProjectRoot()
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--solvers" -> {
                solvers = requireNext(args, i, "--solvers").split(",").map { it.trim() }
                i += 2
            }
            "--instances" -> {
                // Allow a comma-separated list OR multiple positional values
                // until the next flag. Cover both cases so callers can pass
                // shell-expanded globs or a single string.
                val parts = mutableListOf<String>()
                val raw = requireNext(args, i, "--instances")
                parts += raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                var j = i + 2
                while (j < args.size && !args[j].startsWith("--")) {
                    parts += args[j]
                    j++
                }
                instances = parts
                i = j
            }
            "--time-limit" -> {
                timeLimit = requireNext(args, i, "--time-limit").toInt()
                i += 2
            }
            "--out" -> {
                out = Paths.get(requireNext(args, i, "--out"))
                i += 2
            }
            "--run-id" -> {
                runId = requireNext(args, i, "--run-id")
                i += 2
            }
            "--project-root" -> {
                root = File(requireNext(args, i, "--project-root"))
                i += 2
            }
            "-h", "--help" -> {
                System.err.println(USAGE)
                return null
            }
            else -> {
                System.err.println("Unknown flag: ${args[i]}\n$USAGE")
                return null
            }
        }
    }
    if (instances.isEmpty()) {
        System.err.println("--instances is required\n$USAGE")
        return null
    }
    return Cli(
        solvers = solvers,
        instances = instances,
        timeLimitSeconds = timeLimit,
        outDir = out,
        projectRoot = root,
        runId = runId,
    )
}

private fun requireNext(args: Array<String>, i: Int, flag: String): String {
    return args.getOrNull(i + 1) ?: error("$flag needs a value")
}

private const val USAGE = """Usage: runner
    --solvers cpsat,timefold,choco
    --instances data/nsp/toy-01.json,data/nsp/toy-02.json
    --time-limit 30
    --out benchmarks/results/
  [ --run-id 2026-04-baseline ]
  [ --project-root /path/to/cp-deep-dive ]
"""

/**
 * Walk up from the current working directory to locate `CLAUDE.md`, which
 * marks the repo root. Falls back to `.` if nothing matches.
 */
private fun defaultProjectRoot(): File {
    var d: File? = File(".").absoluteFile.canonicalFile
    while (d != null) {
        if (File(d, "CLAUDE.md").exists()) return d
        d = d.parentFile
    }
    return File(".").absoluteFile
}

/**
 * Turn the user's `--instances` argument into concrete paths. Accepts:
 *   - plain paths (absolute or relative to the project root).
 *   - glob patterns containing `*` or `?`, expanded relative to the project root.
 */
internal fun expandInstances(raws: List<String>, root: File): List<Path> {
    val out = mutableListOf<Path>()
    for (raw in raws) {
        if (raw.contains('*') || raw.contains('?')) {
            val base = root.toPath()
            val matcher = base.fileSystem.getPathMatcher("glob:$raw")
            Files.walk(base).use { stream ->
                stream.filter { p ->
                    val rel = base.relativize(p).toString()
                    matcher.matches(Paths.get(rel))
                }.forEach { out.add(it.toAbsolutePath()) }
            }
        } else {
            val p = Paths.get(raw)
            val abs = if (p.isAbsolute) p else root.toPath().resolve(p)
            out.add(abs.toAbsolutePath())
        }
    }
    return out.distinct().sorted()
}

private fun timestampId(): String = DateTimeFormatter
    .ofPattern("yyyy-MM-dd'T'HHmmss")
    .withZone(ZoneOffset.UTC)
    .format(Instant.now())

private fun writeCsv(outDir: Path, records: List<RunRecord>) {
    val header = listOf(
        "timestamp", "instance_id", "solver", "status", "objective",
        "bound", "gap", "solve_seconds", "wall_seconds",
    )
    val path = outDir.resolve("results.csv")
    path.toFile().bufferedWriter().use { w ->
        w.write(header.joinToString(","))
        w.write("\n")
        for (r in records) {
            val row = listOf(
                r.timestamp,
                r.instanceId,
                r.solver,
                r.status,
                r.objective?.toString() ?: "",
                r.bound?.toString() ?: "",
                r.gap?.toString() ?: "",
                "%.3f".format(r.solveSeconds),
                "%.3f".format(r.wallSeconds),
            )
            w.write(row.joinToString(","))
            w.write("\n")
        }
    }
}

private fun writeRunJson(outDir: Path, record: RunRecord) {
    val name = "${record.instanceId}-${record.solver}.json"
    val path = outDir.resolve(name)
    val payload = RunJson(
        timestamp = record.timestamp,
        instanceId = record.instanceId,
        solver = record.solver,
        status = record.status,
        objective = record.objective,
        bound = record.bound,
        gap = record.gap,
        solveSeconds = record.solveSeconds,
        wallSeconds = record.wallSeconds,
        trajectory = record.incumbentTrajectory,
    )
    val json = Json { prettyPrint = true; encodeDefaults = true }
    path.toFile().writeText(json.encodeToString(payload))
}

@Serializable
internal data class RunJson(
    val timestamp: String,
    val instanceId: String,
    val solver: String,
    val status: String,
    val objective: Double? = null,
    val bound: Double? = null,
    val gap: Double? = null,
    val solveSeconds: Double,
    val wallSeconds: Double,
    val trajectory: List<TrajectoryPoint> = emptyList(),
)
