package io.vanja.cpsat

import com.google.ortools.sat.CpModel as JCpModel

/**
 * DSL marker for [CpModel], [ConstraintBuilder], and [IntervalBuilder]
 * receivers. Prevents accidental nesting of `cpModel { cpModel { ... } }` and
 * similar foot-guns where an implicit receiver from an outer scope bleeds
 * into an inner lambda.
 */
@DslMarker
public annotation class CpSatDsl

/**
 * Kotlin wrapper around [com.google.ortools.sat.CpModel]. Hold it like a
 * builder: add variables and constraints, optionally set an objective,
 * then pass it to [solve], [solveBlocking], or [solveFlow].
 *
 * Construct via the [cpModel] top-level function:
 *
 * ```kotlin
 * val model = cpModel {
 *     val x = intVar("x", 0..10)
 *     constraint { x ge 3 }
 *     minimize { x }
 * }
 * val result = model.solveBlocking()
 * ```
 *
 * Use [toJava] to escape to the raw OR-Tools Java API when the DSL doesn't
 * cover a feature yet.
 */
@CpSatDsl
public class CpModel internal constructor(internal val java: JCpModel = JCpModel()) {

    /** Return the underlying Java [com.google.ortools.sat.CpModel]. */
    public fun toJava(): JCpModel = java

    /** Short human-readable stats emitted by OR-Tools (`#vars=`, `#constraints=`, ...). */
    public fun modelStats(): String = java.modelStats()

    /**
     * Validate the model. Returns an empty string on success, or an error
     * message from the native validator on failure. We use this internally
     * to construct [SolveResult.ModelInvalid] when appropriate.
     */
    public fun validate(): String = java.validate()
}

/**
 * Build a [CpModel] by evaluating [block] with the model as the receiver.
 * This is the canonical entry point for the DSL.
 *
 * ```kotlin
 * val model = cpModel {
 *     val x = intVar("x", 0..10)
 *     val y = intVar("y", 0..10)
 *     constraint { x + y eq 7 }
 * }
 * ```
 */
public fun cpModel(block: CpModel.() -> Unit): CpModel {
    ensureNativesLoaded()
    val model = CpModel()
    model.block()
    return model
}
