package io.vanja.cpsat.ch08

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.vanja.cpsat.ch07.MiniZincRunner
import io.vanja.cpsat.ch07.parseKeyValues
import java.io.File

/**
 * Parity — the Kotlin port and the MiniZinc model must agree on the optimal
 * spread for the demo instance. Skipped silently when `minizinc` is missing.
 */
class ParitySpec : StringSpec({
    "Kotlin port and MZN agree on the spread for the demo instance" {
        val runner = MiniZincRunner(timeLimitMs = 10_000L)
        val mznDir = findMznDirFromCh08()
        val mznSpread: Long? = if (runner.isAvailable() && mznDir != null) {
            val model = File(mznDir, "toy-nsp.mzn")
            val data = File(mznDir, "toy-nsp.dzn")
            val mznResult = runner.run(model, data)
            val last = mznResult.lastSolution
            if (last != null) parseKeyValues(last)["spread"]?.toLongOrNull() else null
        } else null

        // Only check parity when we have an MZN answer to compare against.
        if (mznSpread != null) {
            val ktResult = solveToyNsp(DEMO_TOY_NSP, timeLimitS = 10.0)
            ktResult.spread shouldBe mznSpread
        }
        // Otherwise test passes silently — MZN isn't available on this machine.
    }
})

/** Find `apps/mzn/` relative to the test working directory. */
private fun findMznDirFromCh08(): File? {
    var cur: File? = File("").absoluteFile
    repeat(6) {
        val candidate = File(cur, "apps/mzn")
        if (candidate.isDirectory) return candidate
        cur = cur?.parentFile ?: return null
    }
    return null
}
