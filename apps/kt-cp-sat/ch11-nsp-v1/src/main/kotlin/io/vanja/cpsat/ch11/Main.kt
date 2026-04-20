package io.vanja.cpsat.ch11

import io.vanja.cpsat.nsp.InstanceIo
import io.vanja.cpsat.nsp.SolveParams
import io.vanja.cpsat.nsp.SolveResult
import io.vanja.cpsat.nsp.scheduleOrNull
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists

/**
 * CLI: `ch11-nsp-v1 <path-to-instance.json>`
 *
 * Defaults to `data/nsp/toy-01.json` when no argument is given — handy for
 * `./gradlew :ch11-nsp-v1:run` without parameters.
 */
public fun main(args: Array<String>) {
    val path = args.firstOrNull()?.let { Path(it) } ?: defaultInstancePath()
    if (!path.exists()) {
        System.err.println("Instance file not found: $path")
        kotlin.system.exitProcess(2)
    }
    val instance = InstanceIo.load(path)
    println("Loaded: ${instance.name} — ${instance.nurses.size} nurses × " +
            "${instance.horizonDays} days × ${instance.shifts.size} shifts")
    val result = solve(instance, SolveParams(maxTimeSeconds = 30.0))
    when (result) {
        is SolveResult.Optimal -> {
            println("OPTIMAL in ${"%.2f".format(result.solveTimeSeconds)}s (obj=${result.objective})")
            println(render(instance, result.schedule))
        }
        is SolveResult.Feasible -> {
            println("FEASIBLE in ${"%.2f".format(result.solveTimeSeconds)}s " +
                    "(obj=${result.objective}, bound=${result.bestBound}, gap=${"%.3f".format(result.gap)})")
            println(render(instance, result.schedule))
        }
        is SolveResult.Infeasible -> {
            println("INFEASIBLE in ${"%.2f".format(result.solveTimeSeconds)}s")
            kotlin.system.exitProcess(1)
        }
        is SolveResult.Unknown -> {
            println("UNKNOWN in ${"%.2f".format(result.solveTimeSeconds)}s")
            kotlin.system.exitProcess(3)
        }
        is SolveResult.ModelInvalid -> {
            System.err.println("MODEL_INVALID: ${result.message}")
            kotlin.system.exitProcess(4)
        }
    }
    result.scheduleOrNull()?.let {
        // Done — schedule already printed above.
    }
}

/** Walk up from CWD until we find `data/nsp/toy-01.json` — works from any submodule. */
private fun defaultInstancePath(): Path {
    var dir: Path? = Path("").toAbsolutePath()
    while (dir != null) {
        val candidate = dir.resolve("data/nsp/toy-01.json")
        if (candidate.exists()) return candidate
        dir = dir.parent
    }
    return Path("data/nsp/toy-01.json")
}
