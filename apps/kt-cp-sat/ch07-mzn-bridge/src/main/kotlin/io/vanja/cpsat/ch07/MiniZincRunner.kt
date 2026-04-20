package io.vanja.cpsat.ch07

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * MiniZinc CLI wrapper.
 *
 * MiniZinc writes solutions to stdout using two separators:
 *   `----------`   ends one solution
 *   `==========`   declares the last solution is optimal (for optimization models)
 *   `=====UNSATISFIABLE=====` declares infeasibility
 *   `=====UNKNOWN=====` the solver gave up (usually due to a time limit)
 *
 * Output before the first `----------` is the user-defined `output` block from
 * the model. We split on the separators and preserve each block verbatim so
 * callers can parse whatever custom format the model emits.
 *
 * The wrapper is deliberately minimal — no FlatZinc parsing, no AST walking —
 * because we only need it for Chapter 7/8 teaching (compare MZN to `cpsat-kt`
 * on the same instance). For production, you'd want [MiniZinc Python] or a
 * direct FlatZinc backend.
 */
public data class MiniZincResult(
    val status: MzStatus,
    /** Raw stdout blocks, each the body of one solution. Last entry wins for optimization. */
    val solutions: List<String>,
    /** Merged raw output text — useful for debugging. */
    val raw: String,
    /** Wall time for the full run. */
    val wallMs: Long,
) {
    public val lastSolution: String? get() = solutions.lastOrNull()
}

public enum class MzStatus {
    /** At least one solution emitted, proof of optimality. */
    OPTIMAL,

    /** At least one solution emitted, no proof. */
    SATISFIED,

    /** Solver reported UNSAT. */
    UNSATISFIABLE,

    /** Solver said UNKNOWN (typically a timeout). */
    UNKNOWN,

    /** We could not invoke `minizinc` at all (binary missing, exit nonzero, etc). */
    UNAVAILABLE,

    /** Reserved for malformed output. */
    ERROR,
}

public class MiniZincRunner(
    /** Path to the `minizinc` binary — defaults to "minizinc" on PATH. */
    private val binary: String = "minizinc",
    /** Solver tag passed via `--solver`. CP-SAT is named `cp-sat`; default Gecode. */
    private val solver: String = "cp-sat",
    /** Stop the solver after this many seconds. */
    private val timeLimitMs: Long = 10_000L,
    /** When true, return all solutions (for satisfaction problems). */
    private val allSolutions: Boolean = false,
) {
    /** True iff `minizinc --version` runs cleanly on this machine. */
    public fun isAvailable(): Boolean = try {
        val p = ProcessBuilder(binary, "--version")
            .redirectErrorStream(true)
            .start()
        val finished = p.waitFor(3, TimeUnit.SECONDS)
        finished && p.exitValue() == 0
    } catch (_: Throwable) {
        false
    }

    /**
     * Run a [model] (.mzn file) optionally paired with a [data] file (.dzn).
     * Returns a parsed [MiniZincResult]; when the binary is missing or fails
     * to launch, the status is [MzStatus.UNAVAILABLE].
     */
    public fun run(
        model: File,
        data: File? = null,
        extraArgs: List<String> = emptyList(),
    ): MiniZincResult {
        require(model.exists()) { "MiniZinc model does not exist: ${model.absolutePath}" }
        if (data != null) require(data.exists()) { "MiniZinc data file does not exist: ${data.absolutePath}" }

        val args = mutableListOf(binary, "--solver", solver)
        args += "--time-limit"
        args += timeLimitMs.toString()
        if (allSolutions) args += "-a"
        args += extraArgs
        args += model.absolutePath
        if (data != null) args += data.absolutePath

        val start = System.currentTimeMillis()
        val raw: String
        val exit: Int
        try {
            val pb = ProcessBuilder(args).redirectErrorStream(true)
            val proc = pb.start()
            val finished = proc.waitFor(timeLimitMs + 5_000L, TimeUnit.MILLISECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return MiniZincResult(
                    status = MzStatus.UNKNOWN,
                    solutions = emptyList(),
                    raw = "[minizinc killed after timeout]",
                    wallMs = System.currentTimeMillis() - start,
                )
            }
            raw = proc.inputStream.bufferedReader().readText()
            exit = proc.exitValue()
        } catch (ex: Throwable) {
            return MiniZincResult(
                status = MzStatus.UNAVAILABLE,
                solutions = emptyList(),
                raw = "[failed to invoke '$binary': ${ex.message}]",
                wallMs = System.currentTimeMillis() - start,
            )
        }
        val wallMs = System.currentTimeMillis() - start
        if (exit != 0) {
            return MiniZincResult(
                status = MzStatus.ERROR,
                solutions = emptyList(),
                raw = raw,
                wallMs = wallMs,
            )
        }
        val (status, solutions) = parse(raw)
        return MiniZincResult(status = status, solutions = solutions, raw = raw, wallMs = wallMs)
    }

    /** Expose the parser so tests can feed it hand-crafted fixtures. */
    public fun parse(output: String): Pair<MzStatus, List<String>> {
        val lines = output.lines()
        val solutions = mutableListOf<String>()
        val buffer = StringBuilder()
        var status = MzStatus.UNKNOWN
        var sawAnySolution = false

        for (raw in lines) {
            val line = raw.trimEnd()
            when {
                line == "----------" -> {
                    // End of one solution block.
                    solutions.add(buffer.toString().trimEnd('\n'))
                    buffer.clear()
                    sawAnySolution = true
                    if (status == MzStatus.UNKNOWN) status = MzStatus.SATISFIED
                }
                line == "==========" -> {
                    // Optimization proof — whatever we've got is optimal.
                    status = MzStatus.OPTIMAL
                }
                line.contains("UNSATISFIABLE") && line.startsWith("=====") -> {
                    status = MzStatus.UNSATISFIABLE
                }
                line.contains("UNKNOWN") && line.startsWith("=====") -> {
                    status = MzStatus.UNKNOWN
                }
                line.contains("ERROR") && line.startsWith("=====") -> {
                    status = MzStatus.ERROR
                }
                else -> {
                    buffer.appendLine(line)
                }
            }
        }
        // Any trailing text without a trailing `----------` is discarded.
        if (!sawAnySolution && status == MzStatus.UNKNOWN) {
            status = MzStatus.UNKNOWN
        }
        return status to solutions.toList()
    }
}

/**
 * Very small helper to extract `key = value` lines from a MiniZinc output
 * block. The toy NSP model emits lines like `spread=2` and `totals=[4, 5, 5]`
 * — callers can use [parseKeyValues] to pull them into a map.
 */
public fun parseKeyValues(block: String): Map<String, String> {
    val out = mutableMapOf<String, String>()
    for (line in block.lines()) {
        val eq = line.indexOf('=')
        if (eq <= 0) continue
        val k = line.substring(0, eq).trim()
        val v = line.substring(eq + 1).trim()
        if (k.isNotEmpty()) out[k] = v
    }
    return out
}
