package io.vanja.cpsat.ch07

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File

/**
 * Live-runner tests — they invoke the `minizinc` CLI. Every test no-ops
 * (silently passes) when the binary isn't available, so CI on machines
 * without MiniZinc still works. To force the runs, install MiniZinc and put
 * it on PATH.
 */
class MiniZincRunnerSpec : StringSpec({
    val runner = MiniZincRunner(timeLimitMs = 5_000L)

    "isAvailable returns a boolean without crashing" {
        // Just make sure the probe returns and the value is sane.
        val present = runner.isAvailable()
        (present == true || present == false) shouldBe true
    }

    "isAvailable returns false for a bogus binary path" {
        val fake = MiniZincRunner(binary = "__definitely_not_a_binary__")
        fake.isAvailable() shouldBe false
    }

    "running a model via a fake binary returns UNAVAILABLE" {
        val fake = MiniZincRunner(binary = "__definitely_not_a_binary__")
        val mznDir = findMznDirFromTest()
        if (mznDir != null) {
            val result = fake.run(File(mznDir, "nqueens.mzn"), File(mznDir, "nqueens.dzn"))
            result.status shouldBe MzStatus.UNAVAILABLE
        }
    }

    "nqueens.mzn on minizinc returns at least one solution" {
        val mznDir = findMznDirFromTest()
        if (runner.isAvailable() && mznDir != null) {
            val res = runner.run(File(mznDir, "nqueens.mzn"), File(mznDir, "nqueens.dzn"))
            res.status shouldNotBe MzStatus.UNAVAILABLE
            res.solutions.isNotEmpty() shouldBe true
        }
        // Otherwise — test passes silently; real coverage is in the live environments.
    }

    "toy-nsp.mzn on minizinc reports a spread value" {
        val mznDir = findMznDirFromTest()
        if (runner.isAvailable() && mznDir != null) {
            val res = runner.run(File(mznDir, "toy-nsp.mzn"), File(mznDir, "toy-nsp.dzn"))
            res.status shouldNotBe MzStatus.UNAVAILABLE
            val last = res.lastSolution
            last shouldNotBe null
            if (last != null) {
                val kv = parseKeyValues(last)
                kv.containsKey("spread") shouldBe true
            }
        }
    }
})

/** Locate `apps/mzn/` relative to Gradle's test working directory. */
internal fun findMznDirFromTest(): File? {
    // When invoked by Gradle, the working dir is the project root for this module.
    // Walk up to the monorepo root and then into apps/mzn.
    var cur: File? = File("").absoluteFile
    repeat(6) {
        val candidate = File(cur, "apps/mzn")
        if (candidate.isDirectory) return candidate
        cur = cur?.parentFile ?: return null
    }
    return null
}
