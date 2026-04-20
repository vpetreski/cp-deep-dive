package io.vanja.cpsat.ch12

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldNotBe
import io.vanja.cpsat.nsp.Instance
import io.vanja.cpsat.nsp.InstanceIo
import io.vanja.cpsat.nsp.ObjectiveWeights
import io.vanja.cpsat.nsp.SolveParams
import io.vanja.cpsat.nsp.SolveResult
import io.vanja.cpsat.nsp.scheduleOrNull
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Chapter 12 guarantees:
 * - Weighted-sum solve reaches OPTIMAL or FEASIBLE on toy instances.
 * - Lexicographic solve reaches OPTIMAL or FEASIBLE on toy instances.
 * - Objective is ≥ 0 (penalty encoding is non-negative by construction).
 */
class SolveTest : StringSpec({

    "weighted-sum solves toy-01 with default weights" {
        val result = solveWeightedSum(loadToy("toy-01.json"), SolveParams(maxTimeSeconds = 15.0))
        (result is SolveResult.Optimal || result is SolveResult.Feasible).shouldBeTrue()
        result.scheduleOrNull() shouldNotBe null
        val obj = (result as? SolveResult.Optimal)?.objective
            ?: (result as? SolveResult.Feasible)?.objective
            ?: 0L
        obj shouldBeGreaterThanOrEqual 0L
    }

    "weighted-sum solves toy-02 within the time limit" {
        val result = solveWeightedSum(loadToy("toy-02.json"), SolveParams(maxTimeSeconds = 30.0))
        (result is SolveResult.Optimal || result is SolveResult.Feasible).shouldBeTrue()
        result.scheduleOrNull() shouldNotBe null
    }

    "lexicographic solves toy-01 (SC-1..SC-5 staged)" {
        val result = solveLex(loadToy("toy-01.json"), SolveParams(maxTimeSeconds = 30.0))
        (result is SolveResult.Optimal || result is SolveResult.Feasible).shouldBeTrue()
    }

    "zero weights degrade to plain feasibility" {
        val params = SolveParams(
            maxTimeSeconds = 15.0,
            objectiveWeights = ObjectiveWeights.ZERO,
        )
        val result = solveWeightedSum(loadToy("toy-01.json"), params)
        (result is SolveResult.Optimal || result is SolveResult.Feasible).shouldBeTrue()
    }
})

internal fun loadToy(name: String): Instance {
    var dir: Path? = Path.of("").toAbsolutePath()
    while (dir != null) {
        val candidate = dir.resolve("data/nsp/$name")
        if (candidate.exists()) return InstanceIo.load(candidate)
        dir = dir.parent
    }
    error("Could not locate data/nsp/$name above CWD")
}
