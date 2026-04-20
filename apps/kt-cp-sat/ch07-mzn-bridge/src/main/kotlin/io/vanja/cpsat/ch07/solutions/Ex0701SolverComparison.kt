package io.vanja.cpsat.ch07.solutions

import io.vanja.cpsat.ch07.MiniZincRunner
import io.vanja.cpsat.ch07.MzStatus
import io.vanja.cpsat.ch07.findMznDir
import java.io.File

/**
 * Exercise 7.1 — try a few MiniZinc backends on the same model.
 *
 * We run the toy NSP with CP-SAT and Gecode; each is an independent CLI call.
 * Because the `--solver` string depends on local install, we pass a few
 * common labels and keep the ones that actually succeed.
 *
 * If MiniZinc isn't installed, the exercise is a no-op with a friendly note.
 */
fun main() {
    val candidates = listOf("cp-sat", "gecode", "chuffed")
    val mznDir = findMznDir()
    if (mznDir == null) {
        println("apps/mzn/ not found — run this from the repo root.")
        return
    }
    val model = File(mznDir, "toy-nsp.mzn")
    val data = File(mznDir, "toy-nsp.dzn")

    for (solver in candidates) {
        val runner = MiniZincRunner(solver = solver, timeLimitMs = 10_000L)
        if (!runner.isAvailable()) {
            println("[$solver] minizinc not on PATH — skipping.")
            continue
        }
        val r = runner.run(model, data)
        when (r.status) {
            MzStatus.UNAVAILABLE -> println("[$solver] binary launch failed: ${r.raw.lineSequence().firstOrNull()}")
            MzStatus.ERROR -> println("[$solver] non-zero exit — solver probably not configured for MiniZinc.")
            else -> println("[$solver] status=${r.status}  wall=${r.wallMs}ms  solutions=${r.solutions.size}")
        }
    }
}
