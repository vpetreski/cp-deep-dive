package io.vanja.cpsat.ch05

import io.vanja.cpsat.*

/**
 * 0/1 Knapsack: pick a subset maximizing total value under a weight cap.
 *
 *   maximize Σ value[i] * x[i]
 *   subject to Σ weight[i] * x[i] ≤ capacity
 *   x[i] ∈ {0, 1}
 */

public data class Item(val name: String, val weight: Long, val value: Long)

public data class KnapsackResult(
    val status: String,
    val value: Long?,
    val bound: Long?,
    val chosen: List<String>,
    val gap: Double?,
)

public fun solveKnapsack(
    items: List<Item>,
    capacity: Long,
    seed: Int = 42,
    timeLimitS: Double = 10.0,
): KnapsackResult {
    lateinit var xs: List<BoolVar>
    val model = cpModel {
        xs = boolVarList("x", items.size)
        constraint {
            +(weightedSum(xs.map { it as IntVar }, items.map { it.weight }) le capacity)
        }
        maximize { weightedSum(xs.map { it as IntVar }, items.map { it.value }) }
    }

    return when (val res = model.solveBlocking {
        randomSeed = seed
        maxTimeInSeconds = timeLimitS
        numSearchWorkers = 4
    }) {
        is SolveResult.Optimal -> KnapsackResult(
            status = "OPTIMAL",
            value = res.objective,
            bound = res.objective,
            chosen = items.zip(xs).filter { (_, x) -> res.values[x] }.map { it.first.name },
            gap = 0.0,
        )
        is SolveResult.Feasible -> KnapsackResult(
            status = "FEASIBLE",
            value = res.objective,
            bound = res.bound,
            chosen = items.zip(xs).filter { (_, x) -> res.values[x] }.map { it.first.name },
            gap = res.gap,
        )
        SolveResult.Infeasible -> KnapsackResult("INFEASIBLE", null, null, emptyList(), null)
        SolveResult.Unknown -> KnapsackResult("UNKNOWN", null, null, emptyList(), null)
        is SolveResult.ModelInvalid -> KnapsackResult("MODEL_INVALID:${res.message}", null, null, emptyList(), null)
    }
}

/** A 15-item demo instance — big enough to see the bound/incumbent dance. */
public val DEMO_ITEMS: List<Item> = listOf(
    Item("gold-ring", 2, 7),
    Item("silver-cup", 3, 5),
    Item("bronze-bust", 4, 3),
    Item("emerald", 5, 12),
    Item("ruby", 5, 10),
    Item("pearl-necklace", 3, 8),
    Item("brass-clock", 6, 4),
    Item("diamond", 7, 20),
    Item("sapphire", 4, 9),
    Item("opal", 3, 6),
    Item("platinum-bar", 8, 18),
    Item("jade-figurine", 5, 8),
    Item("amber-stone", 2, 4),
    Item("ivory-carving", 6, 11),
    Item("onyx-ring", 3, 7),
)

public const val DEMO_CAPACITY: Long = 30L
