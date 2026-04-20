package io.vanja.cpsat

import com.google.ortools.sat.Literal

/**
 * Run [block] inside a [ConstraintBuilder] whose constraints are only
 * active if [bool] is true. Every relation added to the block will be
 * emitted with `onlyEnforceIf(bool)` on the underlying OR-Tools constraint.
 *
 * ```kotlin
 * val rainy = boolVar("rainy")
 * enforceIf(rainy) {
 *     +(umbrella eq 1)
 *     +(outdoor eq 0)
 * }
 * ```
 *
 * Note: CP-SAT only allows enforcement on a subset of constraints
 * (mostly linear / boolean). If a constraint in the block doesn't support
 * enforcement, the native model will refuse it at validate time.
 */
public fun CpModel.enforceIf(bool: BoolVar, block: ConstraintBuilder.() -> Unit) {
    val b = ConstraintBuilder(this, enforcers = listOf(bool.java as Literal))
    b.block()
    b.flush()
}

/**
 * Like [enforceIf] but requires *all* of [bools] to be true for the
 * constraints in [block] to be active.
 *
 * Under the hood, `onlyEnforceIf(lit1, lit2, ...)` means "the constraint
 * holds only if every listed literal is true", which is exactly the
 * semantics of *all*.
 */
public fun CpModel.enforceIfAll(bools: Iterable<BoolVar>, block: ConstraintBuilder.() -> Unit) {
    val list = bools.map { it.java as Literal }
    val b = ConstraintBuilder(this, enforcers = list)
    b.block()
    b.flush()
}

/**
 * Enforce [block]'s constraints when *any* of [bools] is true. Implemented
 * via a fresh "disjunction" boolean that's `true` iff at least one literal
 * in [bools] is true — the block is then enforced on that disjunction.
 *
 * This adds one auxiliary [BoolVar] and a few linking constraints. The
 * auxiliary name follows pattern `__or<N>` for debuggability.
 */
public fun CpModel.enforceIfAny(bools: Iterable<BoolVar>, block: ConstraintBuilder.() -> Unit) {
    val list = bools.toList()
    require(list.isNotEmpty()) { "enforceIfAny: bools must be non-empty" }
    val disj = boolVar("__or_${nextAuxId()}")
    val j = toJava()
    // disj == OR(bools): disj is true iff at least one literal is true.
    // Encode via: (disj → sum(bools) ≥ 1) and (!disj → sum(bools) == 0).
    val sum = com.google.ortools.sat.LinearExpr.sum(list.map { it.java as com.google.ortools.sat.LinearArgument }.toTypedArray())
    j.addGreaterOrEqual(sum, 1L).onlyEnforceIf(disj.java)
    j.addEquality(sum, 0L).onlyEnforceIf(disj.notLiteral())
    enforceIf(disj, block)
}

// Simple monotonic counter for auxiliary variable names within a model.
// Uses an IdentityHashMap to avoid colliding across models.
private val auxCounters = java.util.IdentityHashMap<Any, Int>()

private fun CpModel.nextAuxId(): Int {
    synchronized(auxCounters) {
        val v = (auxCounters[this] ?: 0) + 1
        auxCounters[this] = v
        return v
    }
}
