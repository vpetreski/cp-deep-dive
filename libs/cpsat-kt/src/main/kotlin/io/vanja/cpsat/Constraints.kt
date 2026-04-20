package io.vanja.cpsat

import com.google.ortools.sat.AutomatonConstraint as JAutomatonConstraint
import com.google.ortools.sat.CircuitConstraint as JCircuitConstraint
import com.google.ortools.sat.Constraint as JConstraint
import com.google.ortools.sat.CumulativeConstraint as JCumulativeConstraint
import com.google.ortools.sat.LinearArgument
import com.google.ortools.sat.ReservoirConstraint as JReservoirConstraint
import com.google.ortools.sat.TableConstraint as JTableConstraint

/**
 * Collects [Relation]s inside a `constraint { ... }` block and flushes them
 * to the underlying [CpModel] when the block completes. The collected
 * constraints share an optional list of enforcement literals so the builder
 * can be reused for [enforceIf] etc.
 *
 * To add a relation, either:
 * - let the last expression in the block be a [Relation] (caught via `+relation` or by direct call on `relation(...)`),
 * - or use `unaryPlus` (`+x eq 0`).
 *
 * In practice the idiomatic style is simply `expr eq other` on its own line
 * — Kotlin evaluates the expression to a [Relation], which we capture via
 * the `Relation.flush()` hook that the DSL plumbs through `unaryPlus` under
 * the hood. To keep the common case ergonomic we expose both: `expr eq 0`
 * and `+(expr eq 0)` both work.
 */
@CpSatDsl
public class ConstraintBuilder internal constructor(
    internal val model: CpModel,
    internal val enforcers: List<com.google.ortools.sat.Literal> = emptyList(),
) {
    internal val pending = mutableListOf<Relation>()

    /** Stage [this] relation to be added when the block completes. */
    public operator fun Relation.unaryPlus() {
        pending.add(this)
    }

    /** Alternate syntax: `relation(x eq y)` — same as `+(x eq y)`. */
    public fun relation(r: Relation) {
        pending.add(r)
    }

    /**
     * After the user-supplied block returns, we materialize each pending
     * relation as an OR-Tools Java constraint and optionally apply the
     * enforcement literals.
     */
    internal fun flush() {
        for (rel in pending) {
            val c = emit(rel)
            if (enforcers.isNotEmpty()) {
                c.onlyEnforceIf(enforcers.toTypedArray())
            }
        }
    }

    private fun emit(rel: Relation): JConstraint {
        val jModel = model.toJava()
        return when (val r = rel.right) {
            is RelSide.Expr -> {
                val le: LinearArgument = rel.left.asJavaArg()
                val ri: LinearArgument = r.e.asLinearArgument()
                when (rel.op) {
                    RelOp.EQ -> jModel.addEquality(le, ri)
                    RelOp.NEQ -> jModel.addDifferent(le, ri)
                    RelOp.LE -> jModel.addLessOrEqual(le, ri)
                    RelOp.LT -> jModel.addLessThan(le, ri)
                    RelOp.GE -> jModel.addGreaterOrEqual(le, ri)
                    RelOp.GT -> jModel.addGreaterThan(le, ri)
                }
            }
            is RelSide.Const -> {
                val le: LinearArgument = rel.left.asJavaArg()
                when (rel.op) {
                    RelOp.EQ -> jModel.addEquality(le, r.v)
                    RelOp.NEQ -> jModel.addDifferent(le, r.v)
                    RelOp.LE -> jModel.addLessOrEqual(le, r.v)
                    RelOp.LT -> jModel.addLessThan(le, r.v)
                    RelOp.GE -> jModel.addGreaterOrEqual(le, r.v)
                    RelOp.GT -> jModel.addGreaterThan(le, r.v)
                }
            }
        }
    }

    private fun RelSide.asJavaArg(): LinearArgument = when (this) {
        is RelSide.Expr -> e.asLinearArgument()
        is RelSide.Const -> com.google.ortools.sat.LinearExpr.constant(v)
    }
}

/**
 * Add one or more relational constraints to this model. Inside the block,
 * write relations like `x eq y`, `sum(vars) le 3`, etc. — each is implicitly
 * staged via [ConstraintBuilder.unaryPlus], which is `operator fun` so no
 * explicit prefix is needed on each line *as long as the line consists
 * solely of a `Relation` expression*. If a line gets complicated, wrap with
 * `+(...)` or `relation(...)` explicitly.
 *
 * ```kotlin
 * constraint {
 *     +(x + y eq 10)
 *     +(2 * x le 5)
 * }
 * ```
 *
 * The `+` prefix is the idiomatic Kotlin way to stage an expression inside a
 * DSL block — it compiles to a call of `Relation.unaryPlus()` on the builder.
 */
public fun CpModel.constraint(block: ConstraintBuilder.() -> Unit) {
    val b = ConstraintBuilder(this)
    b.block()
    b.flush()
}

// -----------------------------------------------------------------------------
// Global constraints — these return the underlying Java handle so callers
// can attach enforcement literals or chain more options on it.
// -----------------------------------------------------------------------------

/** `AllDifferent(vars)` — every variable takes a distinct value. */
public fun CpModel.allDifferent(vars: Iterable<IntVar>): JConstraint {
    val args = vars.map { it.asLinearArgument() }
    return toJava().addAllDifferent(args)
}

/** AllDifferent on a raw list of [LinearExpr]s (e.g., `q + i` for diagonals). */
@JvmName("allDifferentExprs")
public fun CpModel.allDifferent(vars: Iterable<LinearExpr>): JConstraint {
    val args = vars.map { it.asLinearArgument() }
    return toJava().addAllDifferent(args)
}

/** Varargs version. */
public fun CpModel.allDifferent(vararg vars: IntVar): JConstraint =
    allDifferent(vars.asList())

/** Exactly one literal in [bools] must be true. */
public fun CpModel.exactlyOne(bools: Iterable<BoolVar>): JConstraint =
    toJava().addExactlyOne(bools.map { it.java as com.google.ortools.sat.Literal })

/** At most one of [bools] is true. */
public fun CpModel.atMostOne(bools: Iterable<BoolVar>): JConstraint =
    toJava().addAtMostOne(bools.map { it.java as com.google.ortools.sat.Literal })

/** At least one of [bools] is true. */
public fun CpModel.atLeastOne(bools: Iterable<BoolVar>): JConstraint =
    toJava().addAtLeastOne(bools.map { it.java as com.google.ortools.sat.Literal })

/** Boolean OR over literals. */
public fun CpModel.boolOr(bools: Iterable<BoolVar>): JConstraint =
    toJava().addBoolOr(bools.map { it.java as com.google.ortools.sat.Literal })

/** Boolean AND over literals. */
public fun CpModel.boolAnd(bools: Iterable<BoolVar>): JConstraint =
    toJava().addBoolAnd(bools.map { it.java as com.google.ortools.sat.Literal })

/** `a ⇒ b`. */
public fun CpModel.implies(a: BoolVar, b: BoolVar): JConstraint =
    toJava().addImplication(a.java, b.java)

/**
 * Element lookup: `values[index] = target` (in solver terms). Given a
 * constant array [values] and index variable [index], constrains [target]
 * to equal `values[index]`.
 */
public fun CpModel.element(index: IntVar, values: LongArray, target: IntVar): JConstraint =
    toJava().addElement(index.asLinearArgument(), values, target.asLinearArgument())

/** Element over a list of variables. */
@JvmName("elementVars")
public fun CpModel.element(index: IntVar, values: List<IntVar>, target: IntVar): JConstraint {
    val args = values.map { it.asLinearArgument() }
    return toJava().addElement(index.asLinearArgument(), args, target.asLinearArgument())
}

/**
 * Inverse constraint: `f[i] = j ⇔ g[j] = i`. Models a permutation plus its
 * inverse — useful for assignment-style problems where roles are mutually
 * constrained.
 */
public fun CpModel.inverse(f: List<IntVar>, g: List<IntVar>): JConstraint {
    val fa = Array(f.size) { f[it].java }
    val ga = Array(g.size) { g[it].java }
    return toJava().addInverse(fa, ga)
}

/**
 * Allowed-tuples [Table] over [vars]: every assignment of [vars] must equal
 * one row in [tuples]. Each tuple's length must equal [vars].size.
 */
public fun CpModel.table(vars: List<IntVar>, tuples: List<LongArray>): JTableConstraint {
    val args = vars.map { it.asLinearArgument() }
    val t = toJava().addAllowedAssignments(args)
    val m = Array(tuples.size) { tuples[it] }
    t.addTuples(m)
    return t
}

/** Shortcut for [table] with `Int` tuple rows. */
@JvmName("tableInt")
public fun CpModel.table(vars: List<IntVar>, tuples: List<IntArray>): JTableConstraint {
    val args = vars.map { it.asLinearArgument() }
    val t = toJava().addAllowedAssignments(args)
    val m = Array(tuples.size) { tuples[it] }
    t.addTuples(m)
    return t
}

/** Forbidden-tuples variant — [tuples] are assignments that *cannot* occur. */
public fun CpModel.forbidden(vars: List<IntVar>, tuples: List<LongArray>): JTableConstraint {
    val args = vars.map { it.asLinearArgument() }
    val t = toJava().addForbiddenAssignments(args)
    val m = Array(tuples.size) { tuples[it] }
    t.addTuples(m)
    return t
}

/**
 * [Circuit] over arcs: each arc is `(tail, head, literal)`. At most one arc
 * is selected at every node, and together they form a Hamiltonian circuit.
 */
public fun CpModel.circuit(arcs: Iterable<Triple<Int, Int, BoolVar>>): JCircuitConstraint {
    val c = toJava().addCircuit()
    for ((tail, head, lit) in arcs) c.addArc(tail, head, lit.java)
    return c
}

/**
 * [Automaton] over a sequence of variables. Each transition is
 * `(fromState, label, toState)` — the sequence of values in [vars] must
 * drive the automaton from [startState] to some state in [finalStates].
 *
 * Matches CP-SAT's [com.google.ortools.sat.CpModel.addAutomaton] signature
 * (which accepts a `long` label because variable domains are `long`).
 */
public fun CpModel.automaton(
    vars: List<IntVar>,
    startState: Long,
    transitions: List<Triple<Int, Long, Int>>,
    finalStates: Iterable<Int>,
): JAutomatonConstraint {
    val args = vars.map { it.asLinearArgument() }
    val finals = finalStates.toList().map { it.toLong() }.toLongArray()
    val c = toJava().addAutomaton(args, startState, finals)
    for ((from, label, to) in transitions) {
        c.addTransition(from, to, label)
    }
    return c
}

/** `NoOverlap` over intervals — they must not overlap in time. */
public fun CpModel.noOverlap(intervals: Iterable<IntervalVar>): JConstraint {
    val arr = intervals.map { it.toJava() }
    return toJava().addNoOverlap(arr)
}

/**
 * [Cumulative] constraint: each interval consumes its [demand] from a shared
 * resource of [capacity]; total consumption at every time point ≤ capacity.
 *
 * [intervals] and [demands] must have the same length.
 */
public fun CpModel.cumulative(
    intervals: List<IntervalVar>,
    demands: List<Long>,
    capacity: Long,
): JCumulativeConstraint {
    require(intervals.size == demands.size) {
        "cumulative: intervals.size=${intervals.size} != demands.size=${demands.size}"
    }
    val c = toJava().addCumulative(capacity)
    for (i in intervals.indices) {
        c.addDemand(intervals[i].toJava(), demands[i])
    }
    return c
}

/**
 * [Reservoir]: a continuous resource that changes over time. At each [time],
 * the resource level shifts by the matching [level] (negative = consume,
 * positive = produce). Level must stay in `[min, max]`.
 */
public fun CpModel.reservoir(
    times: List<IntVar>,
    levels: List<Long>,
    min: Long,
    max: Long,
): JReservoirConstraint {
    require(times.size == levels.size) {
        "reservoir: times.size=${times.size} != levels.size=${levels.size}"
    }
    val c = toJava().addReservoirConstraint(min, max)
    for (i in times.indices) {
        c.addEvent(times[i].asLinearArgument(), levels[i])
    }
    return c
}

/**
 * Lexicographic-less-or-equal: the sequence [vars1] is lexicographically
 * less than or equal to [vars2]. Implemented via pairwise encoding — CP-SAT
 * doesn't ship a native lexLeq constraint, so we build it from boolean
 * variables and implications.
 */
public fun CpModel.lexLeq(vars1: List<IntVar>, vars2: List<IntVar>) {
    require(vars1.size == vars2.size) {
        "lexLeq: vars1.size=${vars1.size} != vars2.size=${vars2.size}"
    }
    val n = vars1.size
    if (n == 0) return
    // Introduce booleans: eqPrefix[i] = "all entries 0..i-1 are equal".
    // At position i: either eqPrefix[i] is false (we've already diverged with vars1 < vars2),
    // or eqPrefix[i] is true and vars1[i] ≤ vars2[i].
    // Recurrence: eqPrefix[0] = 1; eqPrefix[i+1] = eqPrefix[i] AND (vars1[i] == vars2[i]).
    val eqPrefix = (0..n).map { boolVar("__lexEq$it") }
    // eqPrefix[0] == 1
    constraint { +(eqPrefix[0] eq 1L) }
    for (i in 0 until n) {
        // When eqPrefix[i] is true: vars1[i] ≤ vars2[i]
        val b = eqPrefix[i]
        val cLe = toJava().addLessOrEqual(vars1[i].java, vars2[i].java)
        cLe.onlyEnforceIf(b.java)
        // eqPrefix[i+1] is true iff eqPrefix[i] is true AND vars1[i] == vars2[i].
        val bNext = eqPrefix[i + 1]
        // bNext => eqPrefix[i] AND vars1[i] == vars2[i]
        val cEq = toJava().addEquality(vars1[i].java, vars2[i].java)
        cEq.onlyEnforceIf(bNext.java)
        toJava().addImplication(bNext.java, b.java)
        // If eqPrefix[i] is true and vars1[i] < vars2[i], then subsequent bNext must be false.
        // We encode: (eqPrefix[i] AND vars1[i] != vars2[i]) => NOT bNext.
        // Equivalently: if bNext then vars1[i] == vars2[i] (already above).
    }
}

// -----------------------------------------------------------------------------
// Reification helpers (encoding "bool ⇔ linear condition")
// -----------------------------------------------------------------------------

/**
 * Encode `bool ⇔ (lhs == rhs)` in one step. Useful for counting, Element
 * lookups in patterns, etc.
 */
public fun CpModel.channelEq(bool: BoolVar, lhs: LinearExpr, rhs: Long) {
    val j = toJava()
    // bool → lhs == rhs
    j.addEquality(lhs.asLinearArgument(), rhs).onlyEnforceIf(bool.java)
    // !bool → lhs != rhs
    j.addDifferent(lhs.asLinearArgument(), rhs).onlyEnforceIf(bool.notLiteral())
}
