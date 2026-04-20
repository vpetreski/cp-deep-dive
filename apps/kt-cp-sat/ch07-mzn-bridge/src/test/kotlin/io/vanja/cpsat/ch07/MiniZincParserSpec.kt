package io.vanja.cpsat.ch07

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class MiniZincParserSpec : StringSpec({
    val runner = MiniZincRunner()

    "parses a single-solution satisfaction output" {
        val output = """
            q = [1, 5, 8, 6, 3, 7, 2, 4]
            ----------
        """.trimIndent()
        val (status, blocks) = runner.parse(output)
        status shouldBe MzStatus.SATISFIED
        blocks shouldHaveSize 1
        blocks[0] shouldBe "q = [1, 5, 8, 6, 3, 7, 2, 4]"
    }

    "parses an optimal-proved run with two incumbents and =====" {
        val output = """
            value = 10
            ----------
            value = 12
            ----------
            ==========
        """.trimIndent()
        val (status, blocks) = runner.parse(output)
        status shouldBe MzStatus.OPTIMAL
        blocks shouldHaveSize 2
        blocks[0] shouldBe "value = 10"
        blocks[1] shouldBe "value = 12"
    }

    "parses unsatisfiable" {
        val output = "=====UNSATISFIABLE====="
        val (status, blocks) = runner.parse(output)
        status shouldBe MzStatus.UNSATISFIABLE
        blocks shouldHaveSize 0
    }

    "parses unknown / timeout" {
        val output = "=====UNKNOWN====="
        val (status, _) = runner.parse(output)
        status shouldBe MzStatus.UNKNOWN
    }

    "parseKeyValues pulls fields from a block" {
        val block = """
            spread=2
            totals=[4, 5, 5]
            work=...
        """.trimIndent()
        val kv = parseKeyValues(block)
        kv["spread"] shouldBe "2"
        kv["totals"] shouldBe "[4, 5, 5]"
        kv["work"] shouldBe "..."
    }

    "parseKeyValues ignores lines without an equals sign" {
        val block = """
            header
            x=1
            tail
        """.trimIndent()
        val kv = parseKeyValues(block)
        kv shouldBe mapOf("x" to "1")
    }
})
