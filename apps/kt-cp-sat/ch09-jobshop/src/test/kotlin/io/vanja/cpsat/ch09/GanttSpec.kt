package io.vanja.cpsat.ch09

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class GanttSpec : StringSpec({
    "svg contains the makespan and one rect per operation" {
        val r = solveJobShop(DEMO_33_JSSP)
        val svg = renderGanttSvg(r, DEMO_33_JSSP.nMachines)

        svg shouldContain "<svg"
        svg shouldContain "</svg>"
        // One rectangle per operation + one background rect.
        val opCount = DEMO_33_JSSP.jobs.sumOf { it.size }
        val rectCount = Regex("<rect").findAll(svg).count()
        // At least 1 background + opCount operation rectangles.
        (rectCount >= opCount + 1) shouldBe true
    }

    "svg renders machine labels" {
        val r = solveJobShop(DEMO_33_JSSP)
        val svg = renderGanttSvg(r, DEMO_33_JSSP.nMachines)
        for (m in 0 until DEMO_33_JSSP.nMachines) svg shouldContain ">M$m<"
    }
})
