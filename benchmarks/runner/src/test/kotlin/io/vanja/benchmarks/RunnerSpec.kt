package io.vanja.benchmarks

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import java.nio.file.Paths

/**
 * Smoke tests for CLI argument parsing and glob expansion. Does not invoke
 * any solver — the end-to-end path is exercised by the baseline run script.
 */
class RunnerSpec : StringSpec({

    "parseArgs accepts the core flags" {
        val cli = parseArgs(
            arrayOf(
                "--solvers", "cpsat,choco",
                "--instances", "data/nsp/toy-01.json,data/nsp/toy-02.json",
                "--time-limit", "15",
                "--out", "benchmarks/results/",
                "--project-root", ".",
            ),
        )
        cli shouldNotBe null
        cli!!.solvers shouldBe listOf("cpsat", "choco")
        cli.timeLimitSeconds shouldBe 15
        cli.instances shouldBe listOf("data/nsp/toy-01.json", "data/nsp/toy-02.json")
    }

    "parseArgs returns null on missing --instances" {
        val cli = parseArgs(
            arrayOf(
                "--solvers", "cpsat",
                "--time-limit", "10",
            ),
        )
        cli shouldBe null
    }

    "expandInstances resolves glob patterns relative to project root" {
        // Find the repo root from this test by walking up.
        var d = File(".").absoluteFile.canonicalFile
        while (d != null && !File(d, "CLAUDE.md").exists()) {
            d = d.parentFile
        }
        d shouldNotBe null
        val matches = expandInstances(listOf("data/nsp/toy-*.json"), d!!)
        (matches.size >= 2) shouldBe true
        matches.all { it.toString().endsWith(".json") } shouldBe true
    }

    "expandInstances handles plain paths" {
        val root = File(".").absoluteFile
        val matches = expandInstances(listOf("nonexistent/toy.json"), root)
        matches.size shouldBe 1
        matches[0].toString().endsWith("toy.json") shouldBe true
    }
})
