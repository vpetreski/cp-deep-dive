package io.vanja.cpsat.ch05

import kotlin.system.measureTimeMillis
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

/**
 * Chapter 5 demo: the three flavors of optimization we meet in this chapter.
 *   1. 0/1 Knapsack — the canonical "pick the best subset" COP.
 *   2. Bin Packing  — an integer-decision COP with bounds and symmetry.
 *   3. solveFlow enumeration — the streaming incumbent callback.
 *
 * Run with:
 *   ./gradlew :ch05-optimization:run
 */
fun main() {
    section("1. Knapsack (15 items, capacity=$DEMO_CAPACITY)") {
        val ms = measureTimeMillis {
            val r = solveKnapsack(DEMO_ITEMS, DEMO_CAPACITY)
            println("  status=${r.status}  value=${r.value}  bound=${r.bound}")
            println("  chosen: ${r.chosen.joinToString(", ")}")
        }
        println("  (solved in ${ms}ms)")
    }
    println()

    section("2. Bin Packing (8 items, capacity=$DEMO_BIN_CAPACITY)") {
        val ms = measureTimeMillis {
            val r = solveBinPacking(DEMO_BIN_ITEMS, DEMO_BIN_CAPACITY)
            println("  status=${r.status}  bins=${r.binsUsed}")
            r.assignments.groupBy { it.bin }.toSortedMap().forEach { (b, items) ->
                val names = items.joinToString(",") { it.item }
                println("    bin $b -> [$names]")
            }
        }
        println("  (solved in ${ms}ms)")
    }
    println()

    section("3. Streaming knapsack incumbents (solveFlow)") {
        runBlocking {
            val ms = measureTimeMillis {
                val incumbents = streamKnapsack(DEMO_ITEMS, DEMO_CAPACITY).toList()
                println("  emitted ${incumbents.size} incumbents (best -> worst gap):")
                for ((i, inc) in incumbents.withIndex()) {
                    val gap = (inc.bound - inc.objective).coerceAtLeast(0)
                    println("    #$i  t=${"%.3f".format(inc.wallTimeS)}s  obj=${inc.objective}  bound=${inc.bound}  gap=$gap")
                }
            }
            println("  (streamed in ${ms}ms)")
        }
    }
}

private inline fun section(title: String, block: () -> Unit) {
    println("=== $title ===")
    block()
}
