package io.vanja.cpsat.ch07.solutions

import io.vanja.cpsat.ch07.MiniZincRunner
import io.vanja.cpsat.ch07.MzStatus
import io.vanja.cpsat.ch07.parseKeyValues
import java.io.File
import java.nio.file.Files

/**
 * Exercise 7.2 — generate a MiniZinc model on the fly from Kotlin.
 *
 * We write a tiny .mzn and .dzn into a temp directory, then feed them to
 * [MiniZincRunner]. This illustrates the pipeline you'd use for quick
 * experiments where the instance data comes from the host Kotlin program.
 */
fun main() {
    val runner = MiniZincRunner(timeLimitMs = 5_000L)
    if (!runner.isAvailable()) {
        println("minizinc not available — this exercise is a no-op on this machine.")
        return
    }

    val tmp = Files.createTempDirectory("ch07-inline").toFile()
    val mzn = File(tmp, "inline.mzn").also {
        it.writeText(
            """
            int: n;
            array[1..n] of int: price;
            int: budget;
            array[1..n] of var 0..1: buy;
            constraint sum(i in 1..n)(price[i] * buy[i]) <= budget;
            solve maximize sum(i in 1..n)(buy[i]);
            output [
              "items = ", show(sum(i in 1..n)(buy[i])), "\n",
              "spend = ", show(sum(i in 1..n)(price[i] * buy[i])), "\n"
            ];
            """.trimIndent(),
        )
    }
    val dzn = File(tmp, "inline.dzn").also {
        it.writeText(
            """
            n = 5;
            price = [3, 7, 2, 9, 5];
            budget = 12;
            """.trimIndent(),
        )
    }

    val res = runner.run(mzn, dzn)
    println("status=${res.status}  wall=${res.wallMs}ms")
    if (res.status == MzStatus.UNAVAILABLE) {
        println("(solver did not run — maybe a wrong --solver arg?)")
    }
    res.lastSolution?.let { block ->
        val kv = parseKeyValues(block)
        println("  parsed: items=${kv["items"]}  spend=${kv["spend"]}")
    }
    tmp.deleteRecursively()
}
