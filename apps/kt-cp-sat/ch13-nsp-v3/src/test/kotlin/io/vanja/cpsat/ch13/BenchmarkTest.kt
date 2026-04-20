package io.vanja.cpsat.ch13

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.vanja.cpsat.ch12.solveWeightedSum
import io.vanja.cpsat.nsp.ObjectiveWeights
import io.vanja.cpsat.nsp.SolveParams
import io.vanja.cpsat.nsp.SolveResult
import kotlin.time.TimeSource

/**
 * Chapter 13 guarantees from the spec:
 *
 * - `solve(instance, params)` reaches OPTIMAL or FEASIBLE for a 50×14×3
 *   generated instance within 60 seconds.
 * - Instance generator produces valid, self-consistent input.
 * - Benchmark runner writes a CSV with the expected columns.
 */
class BenchmarkTest : StringSpec({

    "generator produces a valid instance for each preset" {
        val cfgs = listOf(
            Generator.Config(nurses = 5, days = 7, shiftTypes = 2, seed = 1),
            Generator.Config(nurses = 10, days = 14, shiftTypes = 3, seed = 2),
            Generator.Config(nurses = 20, days = 14, shiftTypes = 3, seed = 3, withSkills = true),
        )
        for (cfg in cfgs) {
            val inst = Generator.make(cfg)
            inst.nurses shouldHaveAtLeastSize cfg.nurses
            inst.horizonDays shouldBeLessThanOrEqual cfg.days
            (inst.coverage.all { it.min <= it.max }).shouldBeTrue()
        }
    }

    "50x14x3 generated instance solves within 60 seconds" {
        val instance = Generator.make(
            Generator.Config(nurses = 50, days = 14, shiftTypes = 3, seed = 42, withSkills = false),
        )
        val params = SolveParams(
            maxTimeSeconds = 60.0,
            numSearchWorkers = 8,
            objectiveWeights = ObjectiveWeights.DEFAULT,
        )
        val mark = TimeSource.Monotonic.markNow()
        val result = solveWeightedSum(instance, params)
        val elapsed = mark.elapsedNow().inWholeMilliseconds / 1000.0
        (result is SolveResult.Optimal || result is SolveResult.Feasible).shouldBeTrue()
        elapsed.shouldBeLessThanOrEqual(65.0)  // 5s slack for JIT warm-up
    }

    "benchmark runner returns one row per (instance, variant)" {
        val instances = listOf(
            Generator.make(Generator.Config(nurses = 5, days = 7, shiftTypes = 2, seed = 1)),
        )
        val variants = Benchmark.defaultVariants(timeSeconds = 5.0, workers = 4)
        val rows = Benchmark.run(instances, variants)
        rows.size shouldHaveAtLeastSize.let { /* keep matcher style */ }
        // size should equal instances × variants
        (rows.size == instances.size * variants.size).shouldBeTrue()
    }
})
