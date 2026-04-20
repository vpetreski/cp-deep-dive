package io.vanja.cpsat

import com.google.ortools.sat.BoolVar as JBoolVar
import com.google.ortools.sat.IntVar as JIntVar
import com.google.ortools.util.Domain

/**
 * Wrapper around [com.google.ortools.sat.IntVar]. Also serves as a
 * [LinearExpr] so it can be composed with `+`, `-`, `*` (see
 * [Expressions.kt]).
 *
 * An `IntVar` must come from a specific [CpModel] — the model owns its
 * lifecycle. Use [toJava] to drop down to the raw OR-Tools object.
 */
@CpSatDsl
public open class IntVar internal constructor(
    internal val model: CpModel,
    internal open val java: JIntVar,
) : LinearExpr {

    /** The variable's display name, as recorded in the CP-SAT model. */
    public val name: String get() = java.name

    /** The [Domain] (set of allowed values) the solver must respect. */
    public val domain: Domain get() = java.domain

    /** Return the underlying Java [com.google.ortools.sat.IntVar]. */
    public open fun toJava(): JIntVar = java

    override fun asLinearArgument(): com.google.ortools.sat.LinearArgument = java

    override fun toString(): String = "IntVar($name, $domain)"
}

/**
 * Wrapper around [com.google.ortools.sat.BoolVar]. Every `BoolVar` is also
 * an `IntVar` with domain `{0, 1}` — the Java hierarchy reflects this and so
 * does ours.
 */
@CpSatDsl
public class BoolVar internal constructor(
    model: CpModel,
    override val java: JBoolVar,
) : IntVar(model, java) {

    /** Return the underlying Java [com.google.ortools.sat.BoolVar]. */
    override fun toJava(): JBoolVar = java

    /**
     * The Java-side [com.google.ortools.sat.Literal] that represents the
     * boolean *negation* of this variable. Used internally for reification
     * and passed to `onlyEnforceIf`.
     */
    internal fun notLiteral(): com.google.ortools.sat.Literal = java.not()

    override fun toString(): String = "BoolVar($name)"
}

// -----------------------------------------------------------------------------
// Variable factories
// -----------------------------------------------------------------------------

/**
 * Create an [IntVar] named [name] with integer domain [domain] (inclusive on
 * both ends). Example: `intVar("x", 0..10)` creates a variable `x ∈ [0,10]`.
 *
 * Note: CP-SAT variables are 64-bit signed integers; use the [LongRange]
 * overload for ranges that don't fit into [IntRange].
 */
public fun CpModel.intVar(name: String, domain: IntRange): IntVar =
    IntVar(this, java.newIntVar(domain.first.toLong(), domain.last.toLong(), name))

/** Create an [IntVar] with a [LongRange] domain. */
public fun CpModel.intVar(name: String, domain: LongRange): IntVar =
    IntVar(this, java.newIntVar(domain.first, domain.last, name))

/**
 * Create an [IntVar] with a *sparse* domain — only the listed [values] are
 * allowed. Useful when modeling a variable over a disjoint set (e.g. allowed
 * shift start times `{8, 12, 16, 20}`).
 */
public fun CpModel.intVar(name: String, values: Iterable<Long>): IntVar {
    val arr = values.toList().toLongArray()
    val dom = Domain.fromValues(arr)
    return IntVar(this, java.newIntVarFromDomain(dom, name))
}

/** Create an [IntVar] whose domain is the single value [value]. */
public fun CpModel.constant(value: Long): IntVar =
    IntVar(this, java.newConstant(value))

/** Create a [BoolVar] named [name]. */
public fun CpModel.boolVar(name: String): BoolVar =
    BoolVar(this, java.newBoolVar(name))

/**
 * Create a list of [IntVar]s with names `{prefix}0`, `{prefix}1`, ... — handy
 * for building indexed collections like `val queens = intVarList("q", n, 0 until n)`.
 */
public fun CpModel.intVarList(prefix: String, count: Int, domain: IntRange): List<IntVar> =
    List(count) { i -> intVar("$prefix$i", domain) }

/** List version of [boolVar]: names `{prefix}0`, `{prefix}1`, ..., `{prefix}{count-1}`. */
public fun CpModel.boolVarList(prefix: String, count: Int): List<BoolVar> =
    List(count) { i -> boolVar("$prefix$i") }
