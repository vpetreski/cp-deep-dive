package io.vanja.cpsat.ch06

import io.vanja.cpsat.*

/**
 * Element — "pick a value from a constant array using a variable index."
 *
 * Classic shape:
 *   values = [10, 20, 30, 40, 50]  (constant)
 *   idx    in 0..4                 (variable)
 *   target = values[idx]           (variable)
 *
 * The CP-SAT wrapper `element(index, values, target)` links all three so the
 * solver treats `target = values[index]` as a first-class constraint.
 *
 * Here we bias the model with a linear constraint and optimize the target:
 *   target > 20 AND minimize(target) → picks the smallest element strictly above 20.
 */
public data class ElementSolution(
    val pickedIndex: Int,
    val pickedValue: Long,
)

private val MENU: LongArray = longArrayOf(10, 20, 30, 40, 50)

public fun solveElementDemo(
    menu: LongArray = MENU,
    minValue: Long = 21,
    seed: Int = 42,
    timeLimitS: Double = 5.0,
): ElementSolution? {
    require(menu.isNotEmpty()) { "menu must not be empty" }

    lateinit var idx: IntVar
    lateinit var target: IntVar
    val model = cpModel {
        idx = intVar("idx", 0..(menu.size - 1))
        val lo = menu.min()
        val hi = menu.max()
        target = intVar("target", lo..hi)
        element(idx, menu, target)
        constraint { +(target ge minValue) }
        minimize { target }
    }
    val res = model.solveBlocking {
        randomSeed = seed
        maxTimeInSeconds = timeLimitS
    }
    return when (res) {
        is SolveResult.Optimal -> ElementSolution(res.values[idx].toInt(), res.values[target])
        is SolveResult.Feasible -> ElementSolution(res.values[idx].toInt(), res.values[target])
        else -> null
    }
}
