package io.vanja.cpsat

import com.google.ortools.sat.IntervalVar as JIntervalVar
import com.google.ortools.sat.LinearArgument

/**
 * Interval variable: a scheduling primitive with `start`, `size`, `end`
 * constrained so `start + size == end`. May be *optional*, meaning its
 * presence is controlled by a [BoolVar] ("presence literal").
 *
 * Created via [CpModel.interval] or [CpModel.optionalInterval]. Consumed by
 * [noOverlap], [cumulative], [reservoir], and other scheduling constraints.
 */
@CpSatDsl
public class IntervalVar internal constructor(
    internal val model: CpModel,
    internal val java: JIntervalVar,
    internal val startExpr: LinearExpr,
    internal val sizeExpr: LinearExpr,
    internal val endExpr: LinearExpr,
    internal val presence: BoolVar?,
) {
    /** The variable's name in the CP-SAT model. */
    public val name: String get() = java.name

    /** Start time expression. */
    public val start: LinearExpr get() = startExpr

    /** Size/duration expression. */
    public val size: LinearExpr get() = sizeExpr

    /** End time expression. */
    public val end: LinearExpr get() = endExpr

    /** Optional-interval presence literal (null if always present). */
    public val presenceLiteral: BoolVar? get() = presence

    /** Return the underlying Java [com.google.ortools.sat.IntervalVar]. */
    public fun toJava(): JIntervalVar = java

    override fun toString(): String =
        "IntervalVar($name, start=$startExpr, size=$sizeExpr, end=$endExpr${if (presence == null) "" else ", presence=${presence.name}"})"
}

/**
 * Builder for [IntervalVar]. Fill in `start`, then either `size` or `end`
 * (we'll compute the third). A `size` can be a constant [Long] or a
 * [LinearExpr]. The builder validates that at least two of the three are
 * provided before `interval { }` returns.
 */
@CpSatDsl
public class IntervalBuilder internal constructor(internal val model: CpModel) {
    /** Required: the start time variable or expression. */
    public var start: LinearExpr? = null

    /** Required: the duration, as a constant or expression. Mutually exclusive with [sizeExpr]. */
    public var size: Long? = null

    /** Alternative to [size]: a [LinearExpr] duration (use when size is variable). */
    public var sizeExpr: LinearExpr? = null

    /** Optional: the end time. If omitted, it's allocated as `start + size`. */
    public var end: LinearExpr? = null

    /** Optional: presence literal (for optional intervals). */
    public var presence: BoolVar? = null
}

/**
 * Create a fixed-or-variable-sized interval named [name]. Examples:
 *
 * ```kotlin
 * val t = interval("t1") {
 *     start = intVar("s1", 0..horizon)
 *     size = 3L          // constant duration
 * }
 *
 * val t2 = interval("t2") {
 *     start = intVar("s2", 0..horizon)
 *     sizeExpr = durationVar   // variable duration
 * }
 * ```
 */
public fun CpModel.interval(name: String, block: IntervalBuilder.() -> Unit): IntervalVar {
    val b = IntervalBuilder(this).apply(block)
    val startE: LinearExpr = b.start ?: error("interval '$name': start required")
    val startLA: LinearArgument = startE.asLinearArgument()
    val sizeLong = b.size
    val sizeE = b.sizeExpr
    val endE = b.end

    return when {
        sizeLong != null && sizeE == null -> {
            // Fixed-size interval. Java method computes end automatically.
            val java = toJava().newFixedSizeIntervalVar(startLA, sizeLong, name)
            val sizeConstE = constant(sizeLong)
            val endConstE: LinearExpr = startE + sizeConstE
            IntervalVar(this, java, startE, sizeConstE, endConstE, b.presence)
        }
        sizeE != null -> {
            val sizeLA = sizeE.asLinearArgument()
            val computedEnd: LinearExpr = endE ?: (startE + sizeE)
            val java = toJava().newIntervalVar(startLA, sizeLA, computedEnd.asLinearArgument(), name)
            IntervalVar(this, java, startE, sizeE, computedEnd, b.presence)
        }
        endE != null -> {
            // No size at all, but we have an end. Size = end - start.
            val diffE: LinearExpr = endE - startE
            val java = toJava().newIntervalVar(startLA, diffE.asLinearArgument(), endE.asLinearArgument(), name)
            IntervalVar(this, java, startE, diffE, endE, b.presence)
        }
        else -> error("interval '$name': supply either size, sizeExpr, or end")
    }
}

/**
 * Create an *optional* interval, controlled by [presence]. When `presence`
 * is false, the interval is treated as if it doesn't exist (it contributes
 * nothing to `noOverlap`, `cumulative`, etc.).
 */
public fun CpModel.optionalInterval(
    name: String,
    presence: BoolVar,
    block: IntervalBuilder.() -> Unit,
): IntervalVar {
    val b = IntervalBuilder(this).apply(block)
    b.presence = presence
    val startE: LinearExpr = b.start ?: error("optionalInterval '$name': start required")
    val startLA: LinearArgument = startE.asLinearArgument()
    val sizeLong = b.size
    val sizeE = b.sizeExpr
    val endE = b.end

    return when {
        sizeLong != null && sizeE == null -> {
            val java = toJava().newOptionalFixedSizeIntervalVar(startLA, sizeLong, presence.java, name)
            val sizeConstE = constant(sizeLong)
            val endConstE: LinearExpr = startE + sizeConstE
            IntervalVar(this, java, startE, sizeConstE, endConstE, presence)
        }
        sizeE != null -> {
            val sizeLA = sizeE.asLinearArgument()
            val computedEnd: LinearExpr = endE ?: (startE + sizeE)
            val java = toJava().newOptionalIntervalVar(startLA, sizeLA, computedEnd.asLinearArgument(), presence.java, name)
            IntervalVar(this, java, startE, sizeE, computedEnd, presence)
        }
        endE != null -> {
            val diffE: LinearExpr = endE - startE
            val java = toJava().newOptionalIntervalVar(startLA, diffE.asLinearArgument(), endE.asLinearArgument(), presence.java, name)
            IntervalVar(this, java, startE, diffE, endE, presence)
        }
        else -> error("optionalInterval '$name': supply either size, sizeExpr, or end")
    }
}
