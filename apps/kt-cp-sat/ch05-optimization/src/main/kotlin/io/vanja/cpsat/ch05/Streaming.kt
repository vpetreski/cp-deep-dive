package io.vanja.cpsat.ch05

import io.vanja.cpsat.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Demo of `solveFlow`: streams every incumbent as the solver improves it.
 * Each emission is a snapshot of `(wallTime, objective, bound, chosen)`.
 */

public data class Incumbent(
    val wallTimeS: Double,
    val objective: Long,
    val bound: Long,
    val chosen: List<String>,
)

public fun streamKnapsack(
    items: List<Item>,
    capacity: Long,
    maxSolutions: Int = 50,
    seed: Int = 42,
    timeLimitS: Double = 10.0,
): Flow<Incumbent> {
    lateinit var xs: List<BoolVar>
    val model = cpModel {
        xs = boolVarList("x", items.size)
        constraint {
            +(weightedSum(xs.map { it as IntVar }, items.map { it.weight }) le capacity)
        }
        maximize { weightedSum(xs.map { it as IntVar }, items.map { it.value }) }
    }
    return model.solveFlow {
        randomSeed = seed
        this.maxSolutions = maxSolutions
        maxTimeInSeconds = timeLimitS
        // A small trick: we want every improved incumbent. CP-SAT's log callback
        // reports each. No extra proto flag needed for maximize problems.
    }.map { sol ->
        Incumbent(
            wallTimeS = sol.wallTime,
            objective = sol.objective,
            bound = sol.bound,
            chosen = items.zip(xs).filter { (_, x) -> sol[x] }.map { it.first.name },
        )
    }
}
