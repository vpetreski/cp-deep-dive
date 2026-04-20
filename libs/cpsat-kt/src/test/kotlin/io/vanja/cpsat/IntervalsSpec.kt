package io.vanja.cpsat

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scheduling primitives: intervals + noOverlap + cumulative + reservoir.
 *
 * These tests use tiny instances where the packing is obvious by hand.
 */
class IntervalsSpec : StringSpec({

    "noOverlap forces three intervals to sequence" {
        val horizon = 20L
        lateinit var starts: List<IntVar>
        lateinit var intervals: List<IntervalVar>
        val model = cpModel {
            starts = intVarList("s", 3, 0..horizon.toInt())
            intervals = starts.mapIndexed { i, s ->
                interval("t$i") {
                    start = s
                    size = 5L
                }
            }
            noOverlap(intervals)
            // Minimize makespan = end of latest interval.
            val makespan = intVar("makespan", 0..horizon.toInt())
            constraint {
                for (it in intervals) {
                    +(it.end le makespan)
                }
            }
            minimize { makespan }
        }

        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        // Three intervals of size 5, back-to-back from 0: end = 15.
        res.objective shouldBe 15L

        val vals = intervals.map { res.values[it] }
        val starts0 = vals.map { it.start }.sorted()
        starts0 shouldBe listOf(0L, 5L, 10L)
    }

    "cumulative capacity 2 packs overlapping demands" {
        val horizon = 20
        lateinit var ia: IntervalVar
        lateinit var ib: IntervalVar
        lateinit var ic: IntervalVar
        val model = cpModel {
            val sa = intVar("sa", 0..horizon)
            val sb = intVar("sb", 0..horizon)
            val sc = intVar("sc", 0..horizon)
            ia = interval("a") { start = sa; size = 4L }
            ib = interval("b") { start = sb; size = 4L }
            ic = interval("c") { start = sc; size = 4L }
            // Capacity 2, each demand 1 — any two can overlap, but not three.
            cumulative(listOf(ia, ib, ic), demands = listOf(1L, 1L, 1L), capacity = 2L)
            val makespan = intVar("makespan", 0..horizon)
            constraint {
                +(ia.end le makespan)
                +(ib.end le makespan)
                +(ic.end le makespan)
            }
            minimize { makespan }
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        // With capacity 2 and three unit-demand jobs: pairs overlap, third goes after.
        // Optimum makespan is 8 (e.g., [0,4] + [0,4] then [4,8]).
        res.objective shouldBe 8L
    }

    "optionalInterval with absence is excluded from noOverlap" {
        val horizon = 10
        lateinit var a: IntervalVar
        lateinit var b: IntervalVar
        lateinit var present: BoolVar
        val model = cpModel {
            val sa = intVar("sa", 0..horizon)
            val sb = intVar("sb", 0..horizon)
            a = interval("a") { start = sa; size = 6L }
            present = boolVar("present_b")
            b = optionalInterval("b", presence = present) {
                start = sb
                size = 6L
            }
            // Two 6-long intervals can't both fit in a horizon of 10 without overlap.
            // noOverlap on present-only intervals: absent `b` does not clash.
            noOverlap(listOf(a, b))
            constraint {
                +(a.end le horizon.toLong())
                +(b.end le horizon.toLong())
                +(present eq 0L)   // force b absent
            }
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
        val bVal = res.values[b]
        bVal.present shouldBe false
        val aVal = res.values[a]
        aVal.present shouldBe true
        (aVal.end - aVal.start) shouldBe 6L
    }

    "reservoir level stays in [min, max]" {
        // Model a mini reservoir: at time 0 we add 5, at time 5 we remove 5.
        // Capacity [0, 10] — always feasible.
        val model = cpModel {
            val t1 = intVar("t1", 0..0)
            val t2 = intVar("t2", 5..5)
            reservoir(listOf(t1, t2), levels = listOf(5L, -5L), min = 0L, max = 10L)
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res.shouldBeInstanceOf<SolveResult.Optimal>()
    }

    "reservoir infeasible when level bounds are violated" {
        // At t=0, level += 5; max = 3. Should be infeasible.
        val model = cpModel {
            val t = intVar("t", 0..0)
            reservoir(listOf(t), levels = listOf(5L), min = 0L, max = 3L)
        }
        val res = model.solveBlocking { randomSeed = 42 }
        res shouldBe SolveResult.Infeasible
    }
})
