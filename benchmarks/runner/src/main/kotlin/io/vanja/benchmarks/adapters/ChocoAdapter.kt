package io.vanja.benchmarks.adapters

import io.vanja.benchmarks.RunRecord
import io.vanja.benchmarks.SolverAdapter
import io.vanja.benchmarks.TrajectoryPoint
import java.nio.file.Paths
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.system.measureTimeMillis

/**
 * Invokes `apps/alt-solver/choco` via Gradle. The Choco CLI emits a JSON
 * schedule on stdout and diagnostics on stderr; we pull the objective and
 * wall-time from stderr (which is predictable and small).
 */
class ChocoAdapter : SolverAdapter {
    override val name: String = "choco"

    override fun run(instancePath: String, timeLimitSeconds: Int, projectRoot: String): RunRecord {
        val modulePath = Paths.get(projectRoot, "apps", "alt-solver", "choco")
        require(modulePath.exists()) { "choco module not found at $modulePath" }

        val cmd = listOf(
            "./gradlew",
            "-q",
            "--no-daemon",
            "run",
            "--args=--instance $instancePath --time-limit $timeLimitSeconds",
        )
        val start = Instant.now()
        val pb = ProcessBuilder(cmd)
            .directory(modulePath.toFile())
            .redirectErrorStream(false)
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

        val wallMs = measureTimeMillis {
            val ok = process.waitFor(
                (timeLimitSeconds + 180).toLong(),
                java.util.concurrent.TimeUnit.SECONDS,
            )
            if (!ok) process.destroyForcibly()
        }
        stdoutThread.join()
        stderrThread.join()

        val err = stderr.toString()
        val feasibleMatch = Regex("""Feasible:\s*(true|false)""").find(err)
        val objectiveMatch = Regex("""Objective:\s*(null|-?\d+)""").find(err)
        val wallMatch = Regex("""Wall time:\s*(\d+)ms""").find(err)

        val feasible = feasibleMatch?.groupValues?.get(1) == "true"
        val objective = objectiveMatch?.groupValues?.get(1)
            ?.takeIf { it != "null" }
            ?.toDoubleOrNull()
        val solveMs = wallMatch?.groupValues?.get(1)?.toLongOrNull() ?: wallMs

        val status = when {
            feasible && objective == 0.0 -> "optimal"
            feasible -> "feasible"
            else -> "unknown"
        }

        val solveSec = solveMs / 1000.0
        val trajectory = objective?.let {
            listOf(TrajectoryPoint(tSeconds = solveSec, objective = it))
        } ?: emptyList()

        return RunRecord(
            timestamp = start.toString(),
            instanceId = extractInstanceId(instancePath),
            solver = name,
            status = status,
            objective = objective,
            bound = null,
            gap = null,
            solveSeconds = solveSec,
            wallSeconds = wallMs / 1000.0,
            incumbentTrajectory = trajectory,
        )
    }

    private fun extractInstanceId(path: String): String {
        val f = Paths.get(path)
        if (!f.exists()) return "unknown"
        val content = f.readText()
        val m = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(content)
        return m?.groupValues?.get(1) ?: "unknown"
    }
}
