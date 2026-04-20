package io.vanja.cpsat.ch09

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe

class JobShopSpec : StringSpec({
    "demo 3x3 solves to optimum and respects ordering + noOverlap" {
        val r = solveJobShop(DEMO_33_JSSP)
        r.status shouldBe "OPTIMAL"
        val ms = checkNotNull(r.makespan)
        // Lower bounds from the problem structure:
        //   - longest job's total duration is at least ceil of the bottleneck,
        //   - machine m0 has total 3 + 2 = 5 load,
        //   - machine m1 has total 2 + 4 + 4 = 10 load,
        //   - machine m2 has total 2 + 1 + 3 = 6 load.
        // Thus makespan >= 10.
        ms.shouldBeGreaterThanOrEqual(10)

        // Trivial upper bound: serialize all operations => horizon = sum of all durations.
        val horizon = DEMO_33_JSSP.horizon
        ms.shouldBeLessThanOrEqual(horizon)
    }

    "per-job operations are scheduled in order" {
        val r = solveJobShop(DEMO_33_JSSP)
        for (job in DEMO_33_JSSP.jobs) {
            val scheduled = job.map { op -> r.schedule.first { it.op.jobId == op.jobId && it.op.index == op.index } }
            for (k in 1 until scheduled.size) {
                (scheduled[k].start >= scheduled[k - 1].end) shouldBe true
            }
        }
    }

    "no two operations on the same machine overlap" {
        val r = solveJobShop(DEMO_33_JSSP)
        for (m in 0 until DEMO_33_JSSP.nMachines) {
            val onM = r.schedule.filter { it.op.machine == m }.sortedBy { it.start }
            for (k in 1 until onM.size) {
                (onM[k].start >= onM[k - 1].end) shouldBe true
            }
        }
    }

    "makespan matches max(end) of any operation" {
        val r = solveJobShop(DEMO_33_JSSP)
        val maxEnd = r.schedule.maxOf { it.end }
        r.makespan shouldBe maxEnd
    }
})
