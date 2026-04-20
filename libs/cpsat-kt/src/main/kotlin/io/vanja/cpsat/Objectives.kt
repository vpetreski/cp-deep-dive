package io.vanja.cpsat

/**
 * Set the minimization objective. [block] is evaluated with the [CpModel]
 * as receiver and must return a [LinearExpr].
 */
public fun CpModel.minimize(block: CpModel.() -> LinearExpr) {
    val expr = this.block()
    toJava().minimize(expr.asLinearArgument())
}

/**
 * Set the maximization objective. [block] is evaluated with the [CpModel]
 * as receiver and must return a [LinearExpr].
 */
public fun CpModel.maximize(block: CpModel.() -> LinearExpr) {
    val expr = this.block()
    toJava().maximize(expr.asLinearArgument())
}

/**
 * Builder used by [lexicographic].
 */
@CpSatDsl
public class LexicographicBuilder internal constructor(internal val model: CpModel) {
    internal val stages = mutableListOf<LexStage>()

    /**
     * Add a primary objective (the tightest one — will dominate when tied).
     * Call first. Order matters: each subsequent [stage] is tie-broken only
     * within the optimum of the previous stages.
     */
    public fun primary(sense: Sense = Sense.MINIMIZE, block: CpModel.() -> LinearExpr) {
        stages.add(LexStage(sense, block))
    }

    /** Alias for [primary] — usable at any layer, we just treat it as another stage. */
    public fun stage(sense: Sense = Sense.MINIMIZE, block: CpModel.() -> LinearExpr) {
        stages.add(LexStage(sense, block))
    }

    /** Shortcut for the classic two-stage case. */
    public fun secondary(sense: Sense = Sense.MINIMIZE, block: CpModel.() -> LinearExpr) {
        stages.add(LexStage(sense, block))
    }
}

/** Direction of an objective term. */
public enum class Sense { MINIMIZE, MAXIMIZE }

/**
 * One step of a lexicographic objective. Emitted by [lexicographic] and
 * consumed by [solveLexicographic] in [Solver.kt].
 */
public class LexStage internal constructor(
    public val sense: Sense,
    internal val block: CpModel.() -> LinearExpr,
)

/**
 * Build a list of [LexStage]s for the model. This doesn't invoke the solver —
 * it just records the staged objectives. Actual lexicographic solving
 * happens in [solveLexicographic] (see [Solver.kt]).
 *
 * ```kotlin
 * val stages = cpModel {
 *     // ... variables + constraints ...
 * }.lexicographic {
 *     primary { coverageViolations }       // step 1: min coverage violations
 *     secondary { preferenceViolations }   // step 2: then min preference violations
 * }
 * ```
 */
public fun CpModel.lexicographic(block: LexicographicBuilder.() -> Unit): List<LexStage> {
    val b = LexicographicBuilder(this).apply(block)
    require(b.stages.isNotEmpty()) { "lexicographic { } must define at least one stage" }
    return b.stages
}
