package io.vanja.cpsat

import com.google.ortools.sat.LinearArgument
import com.google.ortools.sat.LinearExpr as JLinearExpr

/**
 * Kotlin-side linear expression. A [LinearExpr] is anything that can be
 * added, subtracted, multiplied by a scalar, and compared with `eq`/`le`/etc
 * to produce a [Relation]. Implemented by [IntVar], [BoolVar], and the
 * internal `LinearExprImpl` class.
 */
public sealed interface LinearExpr {
    /**
     * Access the underlying OR-Tools [LinearArgument] for Java interop. This
     * is public because the interface is `sealed` -- only this library can
     * implement it, so exposing the accessor doesn't let users poke around.
     */
    public fun asLinearArgument(): LinearArgument
}

/** Wrap an existing OR-Tools [LinearExpr] for the arithmetic-result chain. */
internal class LinearExprImpl(private val inner: JLinearExpr) : LinearExpr {
    override fun asLinearArgument(): LinearArgument = inner
}

// -----------------------------------------------------------------------------
// Builder helpers (keep construction cheap -- only allocate the Java expr once)
// -----------------------------------------------------------------------------

private fun argOf(e: LinearExpr): LinearArgument = e.asLinearArgument()

private fun build(sum: com.google.ortools.sat.LinearExprBuilder): LinearExpr =
    LinearExprImpl(sum.build())

// -----------------------------------------------------------------------------
// Addition / subtraction / negation
// -----------------------------------------------------------------------------

/** `a + b` for two linear expressions. */
public operator fun LinearExpr.plus(other: LinearExpr): LinearExpr {
    val b = JLinearExpr.newBuilder()
    b.add(argOf(this))
    b.add(argOf(other))
    return build(b)
}

/** `expr + k` -- add a constant. */
public operator fun LinearExpr.plus(other: Long): LinearExpr {
    val b = JLinearExpr.newBuilder()
    b.add(argOf(this))
    b.add(other)
    return build(b)
}

/** `expr + k` -- add a constant. */
public operator fun LinearExpr.plus(other: Int): LinearExpr = this + other.toLong()

/** `k + expr`. */
public operator fun Long.plus(expr: LinearExpr): LinearExpr = expr + this

/** `k + expr`. */
public operator fun Int.plus(expr: LinearExpr): LinearExpr = expr + this.toLong()

/** `a - b`. Implemented via `a + (-1)*b`. */
public operator fun LinearExpr.minus(other: LinearExpr): LinearExpr {
    val b = JLinearExpr.newBuilder()
    b.add(argOf(this))
    b.addTerm(argOf(other), -1L)
    return build(b)
}

/** `expr - k`. */
public operator fun LinearExpr.minus(other: Long): LinearExpr = this + (-other)

/** `expr - k`. */
public operator fun LinearExpr.minus(other: Int): LinearExpr = this + (-other.toLong())

/** `k - expr`. */
public operator fun Long.minus(expr: LinearExpr): LinearExpr {
    val b = JLinearExpr.newBuilder()
    b.add(this)
    b.addTerm(argOf(expr), -1L)
    return build(b)
}

/** `k - expr`. */
public operator fun Int.minus(expr: LinearExpr): LinearExpr = this.toLong() - expr

/** Unary minus: `-expr`. */
public operator fun LinearExpr.unaryMinus(): LinearExpr {
    val b = JLinearExpr.newBuilder()
    b.addTerm(argOf(this), -1L)
    return build(b)
}

// -----------------------------------------------------------------------------
// Scalar multiplication (coefficients are Long -- CP-SAT doesn't support Double vars)
// -----------------------------------------------------------------------------

/** `coef * expr`. */
public operator fun LinearExpr.times(coef: Long): LinearExpr {
    val b = JLinearExpr.newBuilder()
    b.addTerm(argOf(this), coef)
    return build(b)
}

/** `coef * expr`. */
public operator fun LinearExpr.times(coef: Int): LinearExpr = this * coef.toLong()

/** `coef * expr`. */
public operator fun Long.times(expr: LinearExpr): LinearExpr = expr * this

/** `coef * expr`. */
public operator fun Int.times(expr: LinearExpr): LinearExpr = expr * this.toLong()

// -----------------------------------------------------------------------------
// Sum / weighted sum helpers
// -----------------------------------------------------------------------------

/** Sum a collection of linear expressions into one. */
public fun sum(vars: Iterable<LinearExpr>): LinearExpr {
    val b = JLinearExpr.newBuilder()
    for (v in vars) b.add(argOf(v))
    return build(b)
}

/** Varargs sum. */
public fun sum(vararg vars: LinearExpr): LinearExpr = sum(vars.asList())

/**
 * Weighted sum: `∑ coefs[i] * vars[i]`. [coefs] and [vars] must have the
 * same length.
 */
public fun weightedSum(vars: List<IntVar>, coefs: List<Long>): LinearExpr {
    require(vars.size == coefs.size) {
        "weightedSum: vars.size=${vars.size} != coefs.size=${coefs.size}"
    }
    val args = Array(vars.size) { vars[it].asLinearArgument() }
    val cs = LongArray(coefs.size) { coefs[it] }
    return LinearExprImpl(JLinearExpr.weightedSum(args, cs))
}

// -----------------------------------------------------------------------------
// Relations -- the comparisons that get added inside `constraint { }` blocks.
// A Relation is just a suspended instruction -- the ConstraintBuilder turns it
// into an actual Java Constraint when the block completes.
// -----------------------------------------------------------------------------

/**
 * One side of a relation. Either a linear expression or a constant long.
 * Public-but-unusable externally: only this library creates [RelSide] values
 * (via the infix operators in this file).
 */
public sealed interface RelSide {
    public data class Expr internal constructor(public val e: LinearExpr) : RelSide
    public data class Const internal constructor(public val v: Long) : RelSide
}

/**
 * A pending comparison -- collected by [ConstraintBuilder] and emitted later.
 * Construct via the infix operators (`eq`, `le`, `ge`, ...).
 */
public class Relation internal constructor(
    public val left: RelSide,
    public val op: RelOp,
    public val right: RelSide,
)

/** Relational operators supported by the DSL. */
public enum class RelOp { EQ, NEQ, LE, LT, GE, GT }

// Infix forms -- expr OP expr
public infix fun LinearExpr.eq(other: LinearExpr): Relation =
    Relation(RelSide.Expr(this), RelOp.EQ, RelSide.Expr(other))

public infix fun LinearExpr.neq(other: LinearExpr): Relation =
    Relation(RelSide.Expr(this), RelOp.NEQ, RelSide.Expr(other))

public infix fun LinearExpr.le(other: LinearExpr): Relation =
    Relation(RelSide.Expr(this), RelOp.LE, RelSide.Expr(other))

public infix fun LinearExpr.lt(other: LinearExpr): Relation =
    Relation(RelSide.Expr(this), RelOp.LT, RelSide.Expr(other))

public infix fun LinearExpr.ge(other: LinearExpr): Relation =
    Relation(RelSide.Expr(this), RelOp.GE, RelSide.Expr(other))

public infix fun LinearExpr.gt(other: LinearExpr): Relation =
    Relation(RelSide.Expr(this), RelOp.GT, RelSide.Expr(other))

// Infix forms -- expr OP Long
public infix fun LinearExpr.eq(other: Long): Relation =
    Relation(RelSide.Expr(this), RelOp.EQ, RelSide.Const(other))

public infix fun LinearExpr.neq(other: Long): Relation =
    Relation(RelSide.Expr(this), RelOp.NEQ, RelSide.Const(other))

public infix fun LinearExpr.le(other: Long): Relation =
    Relation(RelSide.Expr(this), RelOp.LE, RelSide.Const(other))

public infix fun LinearExpr.lt(other: Long): Relation =
    Relation(RelSide.Expr(this), RelOp.LT, RelSide.Const(other))

public infix fun LinearExpr.ge(other: Long): Relation =
    Relation(RelSide.Expr(this), RelOp.GE, RelSide.Const(other))

public infix fun LinearExpr.gt(other: Long): Relation =
    Relation(RelSide.Expr(this), RelOp.GT, RelSide.Const(other))

// Int convenience overloads delegate to the Long overloads (so callers can
// write `x eq 3` instead of `x eq 3L`).
public infix fun LinearExpr.eq(other: Int): Relation = this.eq(other.toLong())
public infix fun LinearExpr.neq(other: Int): Relation = this.neq(other.toLong())
public infix fun LinearExpr.le(other: Int): Relation = this.le(other.toLong())
public infix fun LinearExpr.lt(other: Int): Relation = this.lt(other.toLong())
public infix fun LinearExpr.ge(other: Int): Relation = this.ge(other.toLong())
public infix fun LinearExpr.gt(other: Int): Relation = this.gt(other.toLong())

