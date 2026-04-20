package io.vanja.nspapi

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import kotlin.time.Duration.Companion.minutes

/**
 * Smoke test: invoke the adapter directly (no Ktor) to prove the solver
 * actually produces a terminal status in a few seconds. If this passes and
 * SolveLifecycleSpec fails, the bug is in the Ktor wiring, not the solver.
 */
class SolverAdapterSmokeTest : StringSpec({

    beforeSpec { OrtoolsNativesReady }

    "adapter.solve(toy-01) terminates with OPTIMAL or FEASIBLE".config(
        timeout = 3.minutes,
        invocationTimeout = 3.minutes,
    ) {
        val toy = loadToyInstance("toy-01.json")
        val adapter = SolverAdapter()
        val spec = SolverAdapter.SolverSpec(
            maxTimeSeconds = 30.0,
            numSearchWorkers = 4,
            randomSeed = 1,
            logSearchProgress = false,
        )
        val t0 = System.currentTimeMillis()
        val outcome = adapter.solve(toy, spec)
        println("SolverAdapterSmokeTest: elapsed=${System.currentTimeMillis() - t0}ms status=${outcome.status} obj=${outcome.objective}")
        (outcome.status in setOf(JobStatus.FEASIBLE, JobStatus.OPTIMAL)) shouldBe true

        // Also exercise the wire files so we are sure they exist.
        Files.exists(locateRepoFile("data/nsp/toy-01.json")) shouldBe true
    }
})
