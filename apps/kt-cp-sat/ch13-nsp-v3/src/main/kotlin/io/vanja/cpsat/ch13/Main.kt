package io.vanja.cpsat.ch13

import io.vanja.cpsat.nsp.Instance
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.Path
import kotlin.io.path.exists

/**
 * CLI: `ch13-nsp-v3 [--time N] [--workers N] [--output-dir DIR] [--preset tiny|small|medium]`
 *
 * Generates a curated set of synthetic instances and runs every solver
 * variant from [Benchmark.defaultVariants] on each. Writes one CSV to
 * `<output-dir>/kt-ch13-<timestamp>.csv`.
 */
public fun main(args: Array<String>) {
    val opts = parseArgs(args)
    val instances = presets[opts.preset] ?: error("unknown preset ${opts.preset}")
    val variants = Benchmark.defaultVariants(timeSeconds = opts.timeSeconds, workers = opts.workers)
    val rows = Benchmark.run(instances, variants)
    val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    val outPath = opts.outputDir.resolve("kt-ch13-${opts.preset}-$ts.csv")
    Benchmark.writeCsv(outPath, rows)
    println("Wrote ${rows.size} rows to $outPath")
    for (r in rows) {
        println(
            "%-34s %-12s %s obj=%-8s gap=%s in %.2fs".format(
                r.instanceId,
                r.variant,
                r.status,
                r.objective?.toString() ?: "-",
                r.gap?.let { "%.3f".format(it) } ?: "-",
                r.solveSeconds,
            ),
        )
    }
}

private data class Opts(
    val timeSeconds: Double = 60.0,
    val workers: Int = 8,
    val outputDir: Path,
    val preset: String = "small",
)

private fun parseArgs(args: Array<String>): Opts {
    var time = 60.0
    var workers = 8
    var output: Path = defaultOutputDir()
    var preset = "small"
    var i = 0
    while (i < args.size) {
        when (val a = args[i]) {
            "--time" -> { time = args[++i].toDouble() }
            "--workers" -> { workers = args[++i].toInt() }
            "--output-dir" -> { output = Path(args[++i]) }
            "--preset" -> { preset = args[++i] }
            else -> error("Unknown arg: $a")
        }
        i++
    }
    return Opts(timeSeconds = time, workers = workers, outputDir = output, preset = preset)
}

/** Walk up from CWD to find the repo root (contains `benchmarks/`). */
private fun defaultOutputDir(): Path {
    var dir: Path? = Path("").toAbsolutePath()
    while (dir != null) {
        val candidate = dir.resolve("benchmarks/results")
        if (candidate.parent != null && candidate.parent.parent != null && candidate.parent.exists()) {
            return candidate
        }
        // Alternative check — look for `data/nsp` to anchor.
        if (dir.resolve("data/nsp").exists()) {
            return dir.resolve("benchmarks/results")
        }
        dir = dir.parent
    }
    return Path("benchmarks/results")
}

// -----------------------------------------------------------------------------
// Presets — keep generation cheap so CI can actually run these.
// -----------------------------------------------------------------------------

private val presets: Map<String, List<Instance>> = mapOf(
    "tiny" to listOf(
        Generator.make(Generator.Config(nurses = 5, days = 7, shiftTypes = 2, seed = 1)),
        Generator.make(Generator.Config(nurses = 8, days = 7, shiftTypes = 3, seed = 2)),
    ),
    "small" to listOf(
        Generator.make(Generator.Config(nurses = 10, days = 14, shiftTypes = 3, seed = 1)),
        Generator.make(Generator.Config(nurses = 15, days = 14, shiftTypes = 3, seed = 2)),
        Generator.make(Generator.Config(nurses = 20, days = 14, shiftTypes = 3, seed = 3, withSkills = true)),
    ),
    "medium" to listOf(
        Generator.make(Generator.Config(nurses = 30, days = 14, shiftTypes = 3, seed = 1)),
        Generator.make(Generator.Config(nurses = 50, days = 14, shiftTypes = 3, seed = 2, withSkills = true)),
    ),
)
