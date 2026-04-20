package io.vanja.benchmarks.adapters

import io.vanja.benchmarks.JSON_LENIENT
import io.vanja.benchmarks.RunRecord
import io.vanja.benchmarks.SolverAdapter
import io.vanja.benchmarks.TrajectoryPoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import kotlin.io.path.exists
import kotlin.system.measureTimeMillis

/**
 * Invokes the Python `nsp_core` solver via a thin bench script we ship
 * alongside this runner (see `benchmarks/runner/scripts/cpsat_bench.py`).
 * The script solves the instance with `objective="weighted"` and prints one
 * JSON object to stdout containing status, objective, bound, gap, and wall
 * time — the same shape the other adapters translate into [RunRecord].
 */
class CpSatAdapter : SolverAdapter {
    override val name: String = "cpsat"

    override fun run(instancePath: String, timeLimitSeconds: Int, projectRoot: String): RunRecord {
        val script = Paths.get(projectRoot, "benchmarks", "runner", "scripts", "cpsat_bench.py")
        require(script.exists()) { "cpsat_bench.py not found at $script" }

        val pyCpSatDir = Paths.get(projectRoot, "apps", "py-cp-sat").toString()
        val cmd = listOf(
            "uv", "run",
            "--directory", pyCpSatDir,
            "python", script.toString(),
            "--instance", instancePath,
            "--time-limit", timeLimitSeconds.toString(),
        )
        val start = Instant.now()
        val pb = ProcessBuilder(cmd)
            .directory(java.io.File(projectRoot))
            .redirectErrorStream(false)
        val env = pb.environment()
        env["PYTHONUNBUFFERED"] = "1"

        val process = pb.start()
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val stdoutThread = Thread {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { stdout.appendLine(it) }
            }
        }
        val stderrThread = Thread {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { stderr.appendLine(it) }
            }
        }
        stdoutThread.start()
        stderrThread.start()

        var wallMs: Long = 0
        wallMs = measureTimeMillis {
            val finished = process.waitFor(
                (timeLimitSeconds + 120).toLong(),
                java.util.concurrent.TimeUnit.SECONDS,
            )
            if (!finished) {
                process.destroyForcibly()
            }
        }
        stdoutThread.join()
        stderrThread.join()

        val payload = parseJsonLine(stdout.toString())
        val wallSeconds = wallMs / 1000.0
        return RunRecord(
            timestamp = start.toString(),
            instanceId = payload?.instanceId ?: extractInstanceId(instancePath),
            solver = name,
            status = payload?.status ?: "error",
            objective = payload?.objective,
            bound = payload?.bound,
            gap = payload?.gap,
            solveSeconds = payload?.solveSeconds ?: wallSeconds,
            wallSeconds = wallSeconds,
            incumbentTrajectory = payload?.trajectory ?: emptyList(),
        )
    }

    private fun parseJsonLine(output: String): BenchPayload? {
        // The bench script prints one well-formed JSON line to stdout.
        val line = output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("{") }
            .lastOrNull()
            ?: return null
        return try {
            JSON_LENIENT.decodeFromString<BenchPayload>(line)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractInstanceId(path: String): String {
        val f = Paths.get(path)
        if (!f.exists()) return "unknown"
        val content = Files.readString(f)
        // cheap scrape of "id": "..."
        val m = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(content)
        return m?.groupValues?.get(1) ?: "unknown"
    }

    @Serializable
    internal data class BenchPayload(
        val instanceId: String,
        val status: String,
        val objective: Double? = null,
        val bound: Double? = null,
        val gap: Double? = null,
        val solveSeconds: Double,
        val trajectory: List<TrajectoryPoint> = emptyList(),
    )
}
