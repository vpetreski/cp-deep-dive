package io.vanja.cpsat.ch07

import java.io.File

/**
 * Chapter 7 — drive a MiniZinc model from Kotlin.
 *
 * This intentionally does *not* bundle MiniZinc — it assumes the `minizinc`
 * CLI is on your PATH (see `docs/knowledge/minizinc/overview.md` for install).
 * When it isn't, every run returns `MzStatus.UNAVAILABLE` and we print a
 * helpful note instead of crashing.
 *
 * Run with:
 *   ./gradlew :ch07-mzn-bridge:run
 */
fun main() {
    val runner = MiniZincRunner(timeLimitMs = 10_000L)
    val available = runner.isAvailable()
    println("=== MiniZinc bridge ===")
    println("minizinc on PATH: $available")
    if (!available) {
        println()
        println("Install MiniZinc and re-run — see docs/knowledge/minizinc/overview.md.")
        println("The shared .mzn models live in apps/mzn/.")
        return
    }

    val mznDir = findMznDir()
    if (mznDir == null) {
        println("Could not locate apps/mzn/ — running from an unexpected cwd? Skipping live demos.")
        return
    }
    println("Using models from: ${mznDir.absolutePath}")

    section("N-Queens (n=8)") {
        val result = runner.run(File(mznDir, "nqueens.mzn"), File(mznDir, "nqueens.dzn"))
        println("  status=${result.status}  wall=${result.wallMs}ms  solutions=${result.solutions.size}")
        result.lastSolution?.let { println("  last block:\n${it.prependIndent("    ")}") }
    }

    section("Knapsack (15 items, cap=20)") {
        val result = runner.run(File(mznDir, "knapsack.mzn"), File(mznDir, "knapsack.dzn"))
        println("  status=${result.status}  wall=${result.wallMs}ms  solutions=${result.solutions.size}")
        result.lastSolution?.let { println("  last block:\n${it.prependIndent("    ")}") }
    }

    section("Toy NSP (3 nurses x 7 days x 2 shifts)") {
        val result = runner.run(File(mznDir, "toy-nsp.mzn"), File(mznDir, "toy-nsp.dzn"))
        println("  status=${result.status}  wall=${result.wallMs}ms  solutions=${result.solutions.size}")
        result.lastSolution?.let { block ->
            val kv = parseKeyValues(block)
            println("  parsed keys: ${kv.keys}")
            kv["spread"]?.let { println("  spread=$it") }
            kv["totals"]?.let { println("  totals=$it") }
        }
    }
}

/** Resolve `apps/mzn/` relative to the current working directory. */
internal fun findMznDir(startDir: File = File("").absoluteFile): File? {
    var cur: File? = startDir
    repeat(5) {
        val candidate = File(cur, "apps/mzn")
        if (candidate.isDirectory) return candidate
        val apps = File(cur, "../mzn")
        if (apps.isDirectory) return apps.canonicalFile
        cur = cur?.parentFile ?: return null
    }
    return null
}

private inline fun section(title: String, block: () -> Unit) {
    println("=== $title ===")
    block()
    println()
}
