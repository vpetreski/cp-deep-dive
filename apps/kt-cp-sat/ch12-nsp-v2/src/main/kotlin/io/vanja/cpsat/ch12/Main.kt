package io.vanja.cpsat.ch12

import io.vanja.cpsat.ch11.render
import io.vanja.cpsat.nsp.InstanceIo
import io.vanja.cpsat.nsp.ObjectiveWeights
import io.vanja.cpsat.nsp.SolveParams
import io.vanja.cpsat.nsp.SolveResult
import io.vanja.cpsat.nsp.scheduleOrNull
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists

/**
 * CLI: `ch12-nsp-v2 [--lex] [--instance <path>]`
 *
 * Defaults to weighted-sum over the DEFAULT objective weights on toy-01.
 * Pass `--lex` to solve the lexicographic variant instead.
 */
public fun main(args: Array<String>) {
    val argv = args.toMutableList()
    val lex = argv.remove("--lex")
    val instanceArg = argv.indexOf("--instance").let { i -> if (i >= 0 && i + 1 < argv.size) argv[i + 1] else null }
    val path = instanceArg?.let { Path(it) } ?: defaultInstancePath()
    if (!path.exists()) {
        System.err.println("Instance file not found: $path")
        kotlin.system.exitProcess(2)
    }
    val instance = InstanceIo.load(path)
    val params = SolveParams(
        maxTimeSeconds = 30.0,
        objectiveWeights = ObjectiveWeights.DEFAULT,
        lexicographic = lex,
    )
    println("Loaded: ${instance.name} | mode=${if (lex) "lex" else "weighted-sum"}")
    val result = solve(instance, params)
    when (result) {
        is SolveResult.Optimal -> {
            println("OPTIMAL in ${"%.2f".format(result.solveTimeSeconds)}s (obj=${result.objective})")
        }
        is SolveResult.Feasible -> {
            println("FEASIBLE in ${"%.2f".format(result.solveTimeSeconds)}s " +
                    "(obj=${result.objective}, bound=${result.bestBound}, gap=${"%.3f".format(result.gap)})")
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
    result.scheduleOrNull()?.let { sched -> println(render(instance, sched)) }
}

private fun defaultInstancePath(): Path {
    var dir: Path? = Path("").toAbsolutePath()
    while (dir != null) {
        val c = dir.resolve("data/nsp/toy-01.json")
        if (c.exists()) return c
        dir = dir.parent
    }
    return Path("data/nsp/toy-01.json")
}
