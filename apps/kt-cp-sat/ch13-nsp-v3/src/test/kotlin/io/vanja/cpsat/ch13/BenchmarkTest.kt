package io.vanja.cpsat.ch13

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual as intShouldBeLessThanOrEqual
import io.vanja.cpsat.ch12.solveWeightedSum
import io.vanja.cpsat.nsp.ObjectiveWeights
import io.vanja.cpsat.nsp.SolveParams
import io.vanja.cpsat.nsp.SolveResult
import kotlin.time.TimeSource

/**
 * Chapter 13 sanity tests: the generator produces valid instances, the
 * benchmark runner wires up correctly, and a small generated instance
 * solves without error. The full 50×14×3 spec target is exercised by the
 * standalone benchmark CLI (`./gradlew :ch13-nsp-v3:run`), not here —
 * large-instance solve times are too variable across CI runners to gate
 * the unit-test suite on.
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
            (inst.nurses.size >= cfg.nurses).shouldBeTrue()
            inst.horizonDays intShouldBeLessThanOrEqual cfg.days
            (inst.coverage.all { it.min <= it.max }).shouldBeTrue()
        }
    }

    "small generated instance solves within the per-run time budget" {
        val instance = Generator.make(
            Generator.Config(nurses = 10, days = 7, shiftTypes = 2, seed = 42),
        )
        val params = SolveParams(
            maxTimeSeconds = 30.0,
            numSearchWorkers = 4,
            objectiveWeights = ObjectiveWeights.DEFAULT,
        )
        val mark = TimeSource.Monotonic.markNow()
        val result = solveWeightedSum(instance, params)
        val elapsed = mark.elapsedNow().inWholeMilliseconds / 1000.0
        (result is SolveResult.Optimal || result is SolveResult.Feasible).shouldBeTrue()
        elapsed.shouldBeLessThanOrEqual(35.0)  // 5s slack over the solver budget
    }

    "benchmark runner returns one row per (instance, variant)" {
        val instances = listOf(
            Generator.make(Generator.Config(nurses = 5, days = 7, shiftTypes = 2, seed = 1)),
        )
        val variants = Benchmark.defaultVariants(timeSeconds = 5.0, workers = 4)
        val rows = Benchmark.run(instances, variants)
        (rows.size == instances.size * variants.size).shouldBeTrue()
    }
})
