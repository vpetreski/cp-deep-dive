package io.vanja.cpsat.ch06

import io.vanja.cpsat.*
import kotlin.math.hypot
import kotlin.math.roundToLong

/**
 * Circuit — Hamiltonian tour. We model a tiny 8-city TSP.
 *
 * For every ordered pair (i -> j) we introduce a boolean `arc[i,j]`. CP-SAT's
 * Circuit constraint takes a bag of (tail, head, literal) triples and enforces
 * that the selected arcs (literals set to 1) form exactly one simple cycle
 * visiting every node.
 *
 * The objective is `sum_{i,j} dist[i,j] * arc[i,j]`, minimized — classic TSP.
 *
 * Known eight-city instance: a regular octagon on a unit-radius circle. The
 * optimal tour is the perimeter (all 8 sides), total distance ≈ 8 * 0.765
 * ≈ 6.12 * 1000 = 6122 (integer, scaled by 1000).
 */
public data class City(val name: String, val x: Double, val y: Double)

/** Default eight-city instance — regular octagon. */
public val OCTAGON_CITIES: List<City> = run {
    val names = listOf("A", "B", "C", "D", "E", "F", "G", "H")
    val n = names.size
    names.mapIndexed { i, name ->
        val theta = 2.0 * Math.PI * i / n
        City(name, Math.cos(theta), Math.sin(theta))
    }
}

public data class TspResult(
    val order: List<Int>,            // visiting order (indices into cities), starts at 0
    val totalDistanceScaled: Long,   // solver-side objective
    val totalDistance: Double,       // real-world distance
)

/**
 * Solve TSP with CP-SAT's Circuit constraint.
 *
 * @param cities list of cities.
 * @param scale multiplier used to convert Euclidean distances to integers
 * for the solver (CP-SAT is integer-only). Defaults to 1000.
 */
public fun solveTsp(
    cities: List<City> = OCTAGON_CITIES,
    scale: Int = 1000,
    seed: Int = 42,
    timeLimitS: Double = 30.0,
): TspResult {
    val n = cities.size
    require(n >= 3) { "TSP needs at least 3 cities; got $n" }

    // Precompute integer distances.
    val dist = Array(n) { i ->
        LongArray(n) { j ->
            if (i == j) 0L
            else (hypot(cities[i].x - cities[j].x, cities[i].y - cities[j].y) * scale).roundToLong()
        }
    }

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

        // Objective: minimize total scaled distance.
        val flatArcs = (0 until n).flatMap { i ->
            (0 until n).mapNotNull { j -> if (i == j) null else arcs[i][j]!! }
        }
        val flatDist = (0 until n).flatMap { i ->
            (0 until n).mapNotNull { j -> if (i == j) null else dist[i][j] }
        }
        minimize { weightedSum(flatArcs.map { it as IntVar }, flatDist) }
    }

    val res = model.solveBlocking {
        randomSeed = seed
        maxTimeInSeconds = timeLimitS
        numSearchWorkers = 4
    }

    val (assignment, scaledObjective) = when (res) {
        is SolveResult.Optimal -> res.values to res.objective
        is SolveResult.Feasible -> res.values to res.objective
        else -> error("TSP did not produce a tour: $res")
    }

    // Reconstruct the cycle starting at 0.
    val successor = IntArray(n) { -1 }
    for (i in 0 until n) {
        for (j in 0 until n) {
            if (i == j) continue
            if (assignment[arcs[i][j]!!]) {
                successor[i] = j
            }
        }
    }
    val order = mutableListOf<Int>()
    var cur = 0
    do {
        order.add(cur)
        cur = successor[cur]
        check(cur >= 0) { "Circuit reconstruction failed at node ${order.last()}" }
    } while (cur != 0)

    return TspResult(
        order = order,
        totalDistanceScaled = scaledObjective,
        totalDistance = scaledObjective / scale.toDouble(),
    )
}

/** Length of an explicit tour (sanity helper). */
public fun tourDistance(cities: List<City>, order: List<Int>): Double {
    var total = 0.0
    for (i in order.indices) {
        val a = cities[order[i]]
        val b = cities[order[(i + 1) % order.size]]
        total += hypot(a.x - b.x, a.y - b.y)
    }
    return total
}
