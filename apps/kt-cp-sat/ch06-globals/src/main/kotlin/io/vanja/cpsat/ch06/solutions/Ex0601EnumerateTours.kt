package io.vanja.cpsat.ch06.solutions

import io.vanja.cpsat.ch06.*
import io.vanja.cpsat.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

/**
 * Exercise 6.1 — enumerate many near-optimal TSP tours.
 *
 * Uses `solveFlow { maxSolutions = 5; rawProto { enumerateAllSolutions = true } }`
 * to stream solutions as the solver finds them. Because the objective is
 * present, CP-SAT streams the incumbents in improving order.
 *
 * This demonstrates two DSL features:
 *   - enumerateAllSolutions (raw proto escape hatch)
 *   - solveFlow (Kotlin Flow<Incumbent>)
 */
fun main() {
    val cities = OCTAGON_CITIES
    val n = cities.size

    lateinit var arcs: Array<Array<BoolVar?>>
    val model = cpModel {
        arcs = Array(n) { arrayOfNulls<BoolVar>(n) }
        val triples = mutableListOf<Triple<Int, Int, BoolVar>>()
        for (i in 0 until n) {
            for (j in 0 until n) {
                if (i == j) continue
                val lit = boolVar("arc_${i}_${j}")
                arcs[i][j] = lit
                triples.add(Triple(i, j, lit))
            }
        }
        circuit(triples)

        val flatArcs = (0 until n).flatMap { i ->
            (0 until n).mapNotNull { j -> if (i == j) null else arcs[i][j]!! }
        }
        val flatDist = (0 until n).flatMap { i ->
            (0 until n).mapNotNull { j ->
                if (i == j) null
                else (kotlin.math.hypot(
                    cities[i].x - cities[j].x,
                    cities[i].y - cities[j].y,
                ) * 1000).toLong()
            }
        }
        minimize { weightedSum(flatArcs.map { it as IntVar }, flatDist) }
    }

    runBlocking {
        println("Streaming incumbents for 8-city TSP...")
        var count = 0
        model.solveFlow {
            maxSolutions = 8
            randomSeed = 42
            maxTimeInSeconds = 15.0
            numSearchWorkers = 1
        }.collect { incumbent ->
            count += 1
            println("  incumbent #$count: objective=${incumbent.objective}  bound=${incumbent.bound}")
        }
        println("Done. Streamed $count incumbents.")
    }
}
