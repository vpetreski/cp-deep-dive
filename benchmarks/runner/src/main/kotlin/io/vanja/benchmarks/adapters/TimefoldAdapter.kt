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
 * Invokes `apps/alt-solver/timefold` via its Gradle application plugin.
 * Parses the CLI's stdout ("Score: 0hard/-1soft", "Feasible: ...", "Wall
 * time: Xms") into a [RunRecord]. Timefold's soft score is negative when
 * bad, so we flip the sign to land on the repo-wide "smaller = better"
 * objective convention.
 */
class TimefoldAdapter : SolverAdapter {
    override val name: String = "timefold"

    override fun run(instancePath: String, timeLimitSeconds: Int, projectRoot: String): RunRecord {
        val modulePath = Paths.get(projectRoot, "apps", "alt-solver", "timefold")
        require(modulePath.exists()) { "timefold module not found at $modulePath" }

        val cmd = listOf(
            "./gradlew",
            "-q",
            "--no-daemon",
            "run",
            "--args=$instancePath $timeLimitSeconds",
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

        val output = stdout.toString()
        val scoreMatch = Regex("""Score:\s*(-?\d+)hard/(-?\d+)soft""").find(output)
        val feasibleMatch = Regex("""Feasible:\s*(true|false)""").find(output)
        val wallMatch = Regex("""Wall time:\s*(\d+)ms""").find(output)

        val hard = scoreMatch?.groupValues?.get(1)?.toIntOrNull()
        val soft = scoreMatch?.groupValues?.get(2)?.toIntOrNull()
        val feasible = feasibleMatch?.groupValues?.get(1) == "true"
        val solveMs = wallMatch?.groupValues?.get(1)?.toLongOrNull() ?: wallMs

        val status = when {
            hard == null -> "error"
            feasible -> "feasible"
            else -> "infeasible"
        }
        // Convert soft score to repo-wide "lower = better": Timefold reports
        // soft as negative-when-bad, so the objective is -soft.
        val objective = soft?.let { -it.toDouble() }

        val instanceId = extractInstanceId(instancePath)
        val solveSec = solveMs / 1000.0
        val trajectory = objective?.let {
            listOf(TrajectoryPoint(tSeconds = solveSec, objective = it))
        } ?: emptyList()

        return RunRecord(
            timestamp = start.toString(),
            instanceId = instanceId,
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
