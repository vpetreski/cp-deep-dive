package io.vanja.cpsat.ch05

import io.vanja.cpsat.*

/**
 * Bin Packing: assign items to the fewest possible bins under capacity.
 *
 *   decide: assign[i] ∈ {0,...,m-1}  (which bin)
 *   and:    use[b]   ∈ {0,1}         (is bin b used?)
 *   subject to: for each bin b, Σ_{i: assign[i]==b} size[i] ≤ capacity
 *   minimize Σ use[b]
 *
 * Encoded with `inBin[i,b]` booleans for clean linearization:
 *   exactlyOne(inBin[i,*])
 *   Σ_i size[i] * inBin[i,b] ≤ capacity * use[b]
 *   use[b] = 1 iff any inBin[*,b] is true (linked via ≥ and ≤)
 */

public data class BinAssignment(val item: String, val bin: Int)

public data class BinPackingResult(
    val status: String,
    val binsUsed: Int?,
    val assignments: List<BinAssignment>,
    val bound: Long?,
)

public fun solveBinPacking(
    sizes: List<Pair<String, Long>>,
    capacity: Long,
    maxBins: Int = sizes.size, // worst case: one item per bin
    seed: Int = 42,
    timeLimitS: Double = 10.0,
): BinPackingResult {
    require(sizes.all { it.second in 1..capacity }) {
        "item size must be in [1, capacity=$capacity]"
    }
    val n = sizes.size
    val m = maxBins
    val labels = sizes.map { it.first }
    val weights = sizes.map { it.second }

    val inBin = Array(n) { i -> Array(m) { b -> null as BoolVar? } }
    lateinit var use: List<BoolVar>

    val model = cpModel {
        for (i in 0 until n) for (b in 0 until m) {
            inBin[i][b] = boolVar("in_${i}_$b")
        }
        use = boolVarList("use", m)

        // Each item goes to exactly one bin.
        for (i in 0 until n) exactlyOne((0 until m).map { b -> inBin[i][b]!! })

        // Capacity per bin: sum of sizes ≤ capacity * use[b].
        for (b in 0 until m) {
            val column = (0 until n).map { i -> inBin[i][b]!! as IntVar }
            val sizeSum = weightedSum(column, weights)
            constraint { +(sizeSum le capacity * use[b]) }
        }

        // use[b] must be true iff at least one item is in bin b. Already implied by
        // the ≤ capacity*use constraint (if any item is assigned, capacity*0=0 < size,
        // forcing use=1). We add the other direction for cleaner propagation:
        // use[b] ⇒ at least one item there. We don't actually need this to optimize
        // correctly, but it tightens the LP relaxation.
        for (b in 0 until m) {
            val anyInBin = (0 until n).map { i -> inBin[i][b]!! as IntVar }
            // use[b] ≤ sum(anyInBin) — if nobody is in bin b, can't be used.
            constraint { +(use[b] as IntVar le weightedSum(anyInBin, List(n) { 1L })) }
        }

        // Symmetry breaking: bins fill left-to-right.
        for (b in 0 until m - 1) constraint { +(use[b] ge use[b + 1]) }

        minimize { weightedSum(use.map { it as IntVar }, List(m) { 1L }) }
    }

    return when (val res = model.solveBlocking {
        randomSeed = seed
        maxTimeInSeconds = timeLimitS
        numSearchWorkers = 4
    }) {
        is SolveResult.Optimal, is SolveResult.Feasible -> {
            val a = if (res is SolveResult.Optimal) res.values else (res as SolveResult.Feasible).values
            val assigns = (0 until n).map { i ->
                val bin = (0 until m).first { b -> a[inBin[i][b]!!] }
                BinAssignment(labels[i], bin)
            }
            val usedCount = (0 until m).count { b -> a[use[b]] }
            val objValue = if (res is SolveResult.Optimal) res.objective else (res as SolveResult.Feasible).objective
            val bound = if (res is SolveResult.Feasible) res.bound else objValue
            BinPackingResult(
                status = if (res is SolveResult.Optimal) "OPTIMAL" else "FEASIBLE",
                binsUsed = usedCount,
                assignments = assigns,
                bound = bound,
            )
        }
        SolveResult.Infeasible -> BinPackingResult("INFEASIBLE", null, emptyList(), null)
        SolveResult.Unknown -> BinPackingResult("UNKNOWN", null, emptyList(), null)
        is SolveResult.ModelInvalid -> BinPackingResult("MODEL_INVALID:${res.message}", null, emptyList(), null)
    }
}

public val DEMO_BIN_ITEMS: List<Pair<String, Long>> = listOf(
    "a" to 4L, "b" to 8L, "c" to 1L, "d" to 4L,
    "e" to 2L, "f" to 1L, "g" to 9L, "h" to 5L,
)

public const val DEMO_BIN_CAPACITY: Long = 10L
