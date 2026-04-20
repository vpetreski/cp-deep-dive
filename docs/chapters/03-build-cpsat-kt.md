# Chapter 03 — Build cpsat-kt v0.1 (the Kotlin DSL library)

> **Phase 2: CP-SAT basics + build cpsat-kt** · Estimated: ~4h · Status: ready-to-start · Last updated: 2026-04-19

## Goal

Design and implement the core of `cpsat-kt` — the idiomatic Kotlin DSL wrapper over OR-Tools CP-SAT. At chapter end, Chapter 2's raw-Kotlin model is rewritten in clean DSL Kotlin, and every subsequent Kotlin chapter uses this library. You come out with a publishable Kotlin artifact (not just "helper code"), Gradle composite-build plumbing, Kotest parity tests, and a mental model of Kotlin DSL patterns you'll reuse.

## Before you start

- **Prerequisites:** Chapter 2 — you felt the raw Kotlin pain; you understand `Loader.loadNativeLibraries`, `LinearExpr`, `CpSolverStatus`.
- **Required reading:**
  - [`knowledge/cpsat-kt/overview.md`](../knowledge/cpsat-kt/overview.md) — the design doc in full (it's short). This is the source of truth for package layout, API surface, and versioning.
  - [`knowledge/cp-sat/python-vs-kotlin.md §6`](../knowledge/cp-sat/python-vs-kotlin.md) — the sketch of the operators, infix, and DSL builder idea we'll formalize here.
  - Kotlin docs, [*Type-safe builders*](https://kotlinlang.org/docs/type-safe-builders.html) — the canonical explanation of the DSL pattern. 10 minutes, worth every second.
- **Environment:**
  - JDK 25 verified.
  - Gradle 9 via `./gradlew` (use the wrapper — never a system-installed Gradle).
  - IntelliJ IDEA Community (or Ultimate) recommended; VS Code + Kotlin extension works but loses some IDE niceties.
  - No new install beyond Chapter 2's.

## Concepts introduced this chapter

- **`@DslMarker` annotation** — Kotlin mechanism that prevents implicit-receiver leakage across nested DSL scopes. Essential for safe DSLs.
- **Receiver lambdas** (`CpModel.() -> Unit`) — a function whose `this` is a specific type, so calls inside it resolve as if they were methods.
- **Operator overloading** — Kotlin lets `+`, `-`, `*`, `unaryMinus` map to named `operator fun`s on any class. We use this to make arithmetic on our `LinearExpr` wrapper read like algebra.
- **Infix functions** — `infix fun eq(rhs: ...)` lets you write `x eq 3` without dots or parens. Used for comparison operators where Kotlin forbids overloading `==` meaningfully.
- **Sealed class / sealed interface** — a closed set of subtypes the compiler knows about. Used for `SolveResult` so `when` branches are exhaustive.
- **Wrapper vs typealias** — why we define `class IntVar(val java: com.google.ortools.sat.IntVar)` instead of `typealias IntVar = com.google.ortools.sat.IntVar`.
- **Kotlin `Flow`** — cold reactive stream, perfect for streaming incumbent solutions from a `CpSolverSolutionCallback` without blocking.
- **Gradle composite build** — `includeBuild("../../libs/cpsat-kt")` lets chapter apps consume the library without publish round-trips.
- **Gradle version catalog** — `gradle/libs.versions.toml` centralizes dep versions across the multi-project build.
- **ADR (Architecture Decision Record)** — short markdown note locking a decision with context + consequences. You'll write one for the library's package name and coords.

## 1. Intuition — turn Chapter 2's pain into Chapter 2's pleasure

The Kotlin code from Chapter 2:

```kotlin
Loader.loadNativeLibraries()
val model = CpModel()
val x = model.newIntVar(0L, 10L, "x")
val y = model.newIntVar(0L, 10L, "y")
model.addEquality(LinearExpr.newBuilder().addTerm(x, 3L).addTerm(y, 2L).build(), 12L)
model.addLessOrEqual(LinearExpr.newBuilder().add(x).add(y).build(), 5L)
model.maximize(LinearExpr.newBuilder().add(x).add(y).build())
val solver = CpSolver()
val status = solver.solve(model)
```

What you want:

```kotlin
val result = cpModel {
    val x = intVar("x", 0..10)
    val y = intVar("y", 0..10)
    constraint { 3 * x + 2 * y eq 12 }
    constraint { x + y le 5 }
    maximize { x + y }
}.solve()

when (result) {
    is SolveResult.Optimal -> println("x=${result[x]} y=${result[y]} obj=${result.objective}")
    is SolveResult.Infeasible -> println("no solution")
    else -> println("did not finish cleanly: $result")
}
```

Same proto crosses into the same C++ solver core. Same answer. The model-building code is *smaller than the Python version*, and the result handling is more type-safe. That's the target. This chapter designs and implements the library that makes it real.

**A different way to read Chapter 3.** Usually "hands-on" in this repo means *I'll write code for my app*. This time it means *I'll write a library*. The "worked example" is the library itself — you'll read its source file-by-file, extend it where it's thin, and use it in the rest of the chapter to rewrite Chapter 2.

## 2. Formal definition — the v0.1 API surface

The v0.1 API is a curated subset. What's in:

```kotlin
// Entry point
fun cpModel(block: CpModel.() -> Unit): CpModel

// Variables
fun CpModel.intVar(name: String, domain: IntRange): IntVar
fun CpModel.intVar(name: String, domain: LongRange): IntVar
fun CpModel.boolVar(name: String): BoolVar
fun CpModel.constant(value: Long): IntVar

// Expressions (LinearExpr)
operator fun IntVar.plus(other: IntVar): LinearExpr
operator fun IntVar.plus(k: Long): LinearExpr
operator fun IntVar.minus(other: IntVar): LinearExpr
operator fun IntVar.minus(k: Long): LinearExpr
operator fun IntVar.times(coeff: Long): LinearExpr
operator fun LinearExpr.unaryMinus(): LinearExpr
operator fun Long.times(v: IntVar): LinearExpr   // 3 * x, not just x * 3

// Constraints (inside a constraint { } block)
infix fun LinearExpr.eq(rhs: Long): Constraint
infix fun LinearExpr.neq(rhs: Long): Constraint
infix fun LinearExpr.le(rhs: Long): Constraint
infix fun LinearExpr.lt(rhs: Long): Constraint
infix fun LinearExpr.ge(rhs: Long): Constraint
infix fun LinearExpr.gt(rhs: Long): Constraint

fun CpModel.constraint(block: ConstraintBuilder.() -> Unit)

// Global constraints (top-level because they're statements)
fun CpModel.allDifferent(vars: Iterable<IntVar>)
fun CpModel.exactlyOne(bools: Iterable<BoolVar>)
fun CpModel.atMostOne(bools: Iterable<BoolVar>)

// Objective
fun CpModel.minimize(block: ExprBuilder.() -> LinearExpr)
fun CpModel.maximize(block: ExprBuilder.() -> LinearExpr)

// Solve
suspend fun CpModel.solve(params: SolverParams.() -> Unit = {}): SolveResult
fun CpModel.solveBlocking(params: SolverParams.() -> Unit = {}): SolveResult

// Result
sealed interface SolveResult {
    data class Optimal(val values: Assignment, val objective: Long) : SolveResult
    data class Feasible(val values: Assignment, val objective: Long, val bound: Long, val gap: Double) : SolveResult
    object Infeasible : SolveResult
    object Unknown : SolveResult
    data class ModelInvalid(val message: String) : SolveResult
}

class Assignment internal constructor(...) {
    operator fun get(v: IntVar): Long
    operator fun get(v: BoolVar): Boolean
}

// Escape hatch
fun CpModel.toJava(): com.google.ortools.sat.CpModel
```

What's *not* in v0.1:

- Intervals (`IntervalVar`, `noOverlap`, `cumulative`) — added in Chapter 9.
- Circuit, Automaton, Table, Element, Inverse, Reservoir, LexLeq — added in Chapter 6.
- Half-reification (`enforceIf`) — added in Chapter 4 once you need it for N-Queens variants.
- Multi-objective / lexicographic — added in Chapter 12.
- `solveFlow` (streaming incumbents) — added in Chapter 5 when you need it for optimization.

This staging is deliberate. You'll see `cpsat-kt` grow chapter by chapter, which is both pedagogy ("learn the DSL technique by building it") and realism ("this is how real libraries evolve").

### Package layout (as in the design doc)

```
libs/cpsat-kt/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/libs.versions.toml
├── src/main/kotlin/io/vanja/cpsat/
│   ├── Natives.kt         ← Loader.loadNativeLibraries() once-and-done
│   ├── Model.kt           ← cpModel { }, CpModel wrapper
│   ├── Variables.kt       ← IntVar, BoolVar wrappers
│   ├── Expressions.kt     ← LinearExpr, operators, infix predicates
│   ├── Constraints.kt     ← constraint{}, allDifferent, exactlyOne, atMostOne
│   ├── Objectives.kt      ← minimize / maximize
│   └── Solver.kt          ← SolveResult, Assignment, solve, solveBlocking
└── src/test/kotlin/io/vanja/cpsat/
    ├── NativesSpec.kt
    ├── VariablesSpec.kt
    ├── ExpressionsSpec.kt
    ├── ConstraintsSpec.kt
    ├── SolverSpec.kt
    └── ParityTests.kt     ← run same model via raw Java + DSL, assert equal outcome
```

### Composite-build plumbing

The chapter apps consume `cpsat-kt` as a composite build:

```kotlin
// apps/kt-cp-sat/settings.gradle.kts
rootProject.name = "kt-cp-sat"
includeBuild("../../libs/cpsat-kt")

// apps/kt-cp-sat/ch03-build-cpsat-kt/build.gradle.kts (etc.)
dependencies {
    implementation("io.vanja:cpsat-kt")   // resolves to the composite build
}
```

Local edits to `libs/cpsat-kt/src/**` are picked up on the next `./gradlew run` with zero publish round-trip. Huge for the learning phase.

## 3. Worked example by hand — what every helper does to the CpModelProto

For the expression `3 * x + 2 * y eq 12` inside a `constraint { }` block:

1. `3 * x` — invokes `operator fun Long.times(v: IntVar): LinearExpr`. Creates a new `LinearExpr` wrapping `com.google.ortools.sat.LinearExpr.term(x.java, 3L)`.
2. `2 * y` — similar; creates a `LinearExpr` for `term(y.java, 2L)`.
3. `3 * x + 2 * y` — invokes `operator fun LinearExpr.plus(other: LinearExpr): LinearExpr`. Builds `LinearExpr.newBuilder().add(lhs.java).add(rhs.java).build()`.
4. `... eq 12` — invokes `infix fun LinearExpr.eq(rhs: Long): Constraint`. Posts `model.addEquality(lhs.java, 12L)`.
5. The `constraint { }` block captures a `ConstraintBuilder` whose members are the infix predicates; `this` in the block resolves to the builder, so `3 * x + 2 * y eq 12` is `this.(3 * x + 2 * y).eq(12)` under the hood, but the `@DslMarker` machinery hides the receiver.

Everything we do in `cpsat-kt` maps 1:1 to an underlying Java call. No reflection, no codegen, no hidden state. **If you trace any DSL line through the source, you should land on a `com.google.ortools.sat.*` method call within 2–3 hops.** That's the design principle ("zero magic") made concrete.

## 4. Python implementation

N/A — this chapter is Kotlin-focused. `cpsat-kt` is the Kotlin-side artifact; Python already has a first-class DSL-via-operator-overloading experience via `ortools.sat.python.cp_model`. Skip to §5.

(Note for the curious: an analogous Python project would be building type stubs and ergonomic wrappers around `cp_model` for large-team codebases. That's out of scope — our Python experience is good enough out of the box.)

## 5. Kotlin implementation — read the library file-by-file, then rewrite Ch 2

### 5.1 `Natives.kt` — one-shot library loading

```kotlin
package io.vanja.cpsat

import com.google.ortools.Loader

internal object Natives {
    private val loaded: Boolean by lazy {
        Loader.loadNativeLibraries()
        true
    }
    fun ensureLoaded() { loaded }
}
```

**Why this shape?** `Loader.loadNativeLibraries()` must run *exactly once* per JVM. If called twice, OR-Tools warns and ignores the second; if forgotten, any `com.google.ortools.sat.*` call throws `UnsatisfiedLinkError`. Kotlin's `by lazy` + `object` gives you thread-safe initialization for free. We call `Natives.ensureLoaded()` at the top of `cpModel { }` so the user never thinks about this.

### 5.2 `Model.kt` — the DSL marker and entry point

```kotlin
package io.vanja.cpsat

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class CpSatDsl

@CpSatDsl
class CpModel internal constructor(
    internal val java: com.google.ortools.sat.CpModel
) {
    fun toJava(): com.google.ortools.sat.CpModel = java
}

fun cpModel(block: CpModel.() -> Unit): CpModel {
    Natives.ensureLoaded()
    val m = CpModel(com.google.ortools.sat.CpModel())
    m.block()
    return m
}
```

**Why `@DslMarker`?** Without it, deeply nested DSL blocks can leak receivers — e.g. inside a `constraint { ... }` block you could accidentally call `intVar(...)` from the outer `cpModel` receiver. `@DslMarker` makes the compiler refuse that implicit cross-scope call. You'd have to write `this@cpModel.intVar(...)` to opt in — explicit by design.

**Why a wrapper class, not a typealias?** If `class CpModel` were `typealias CpModel = com.google.ortools.sat.CpModel`, then all the Java methods (`newIntVar`, `addAllDifferent`, …) would leak onto the DSL surface. We'd *also* have to add extension functions. Users would see both `intVar(name, range)` (ours) and `newIntVar(lo, hi, name)` (Java's) in autocomplete. Confusing. The wrapper hides the Java API behind an explicit `.toJava()` escape hatch, keeping the DSL surface clean.

### 5.3 `Variables.kt` — wrappers for IntVar and BoolVar

```kotlin
package io.vanja.cpsat

@CpSatDsl
class IntVar internal constructor(internal val java: com.google.ortools.sat.IntVar) {
    val name: String get() = java.name
    fun toJava(): com.google.ortools.sat.IntVar = java
}

@CpSatDsl
class BoolVar internal constructor(internal val java: com.google.ortools.sat.BoolVar) {
    val name: String get() = java.name
    fun toJava(): com.google.ortools.sat.BoolVar = java
    operator fun not(): BoolVar {
        // Note: Java's `java.not()` returns a Literal, not a BoolVar.
        // For v0.1 we only expose it on the literal side; full negation wrapping comes later.
        TODO("Implement when we need literals — Chapter 4 with enforceIf")
    }
}

fun CpModel.intVar(name: String, domain: IntRange): IntVar =
    IntVar(java.newIntVar(domain.first.toLong(), domain.last.toLong(), name))

fun CpModel.intVar(name: String, domain: LongRange): IntVar =
    IntVar(java.newIntVar(domain.first, domain.last, name))

fun CpModel.boolVar(name: String): BoolVar =
    BoolVar(java.newBoolVar(name))

fun CpModel.constant(value: Long): IntVar =
    IntVar(java.newConstant(value))
```

**Why wrap?** Three reasons: (1) it gives the DSL a stable surface — if OR-Tools renames a Java method, we absorb it once in one wrapper; (2) it lets us attach operator overloads via extension functions without polluting the Java class's methods; (3) it gives us the `@CpSatDsl` marker, which is applied to types, not Java classes we don't own.

The `TODO` on `BoolVar.not()` is intentional — we don't need literal-level negation until Chapter 4's half-reification. The convention in `cpsat-kt`: *don't build what you don't yet use*. When Chapter 4 needs it, extend the library in a separate commit, with a failing test first.

### 5.4 `Expressions.kt` — the operator trick

```kotlin
package io.vanja.cpsat

@CpSatDsl
class LinearExpr internal constructor(internal val java: com.google.ortools.sat.LinearExpr) {
    fun toJava(): com.google.ortools.sat.LinearExpr = java
}

// --- Build LinearExpr from IntVar + primitives ---

operator fun IntVar.plus(other: IntVar): LinearExpr = LinearExpr(
    com.google.ortools.sat.LinearExpr.newBuilder().add(this.java).add(other.java).build()
)

operator fun IntVar.plus(k: Long): LinearExpr = LinearExpr(
    com.google.ortools.sat.LinearExpr.newBuilder().add(this.java).add(k).build()
)

operator fun IntVar.minus(other: IntVar): LinearExpr = LinearExpr(
    com.google.ortools.sat.LinearExpr.newBuilder().add(this.java).addTerm(other.java, -1L).build()
)

operator fun IntVar.minus(k: Long): LinearExpr = this + (-k)

operator fun IntVar.times(coeff: Long): LinearExpr = LinearExpr(
    com.google.ortools.sat.LinearExpr.term(this.java, coeff)
)

operator fun Long.times(v: IntVar): LinearExpr = v * this

// --- LinearExpr combinators ---

operator fun LinearExpr.plus(other: LinearExpr): LinearExpr = LinearExpr(
    com.google.ortools.sat.LinearExpr.newBuilder().add(this.java).add(other.java).build()
)

operator fun LinearExpr.plus(v: IntVar): LinearExpr = LinearExpr(
    com.google.ortools.sat.LinearExpr.newBuilder().add(this.java).add(v.java).build()
)

operator fun LinearExpr.plus(k: Long): LinearExpr = LinearExpr(
    com.google.ortools.sat.LinearExpr.newBuilder().add(this.java).add(k).build()
)

operator fun LinearExpr.unaryMinus(): LinearExpr = this * -1L

operator fun LinearExpr.times(k: Long): LinearExpr = LinearExpr(
    // Scale all existing terms; for v0.1 this is easiest via affine on the expression.
    com.google.ortools.sat.LinearExpr.affine(this.java, k, 0L)
)

// --- sum / weightedSum helpers ---

fun sum(vars: Iterable<IntVar>): LinearExpr = LinearExpr(
    com.google.ortools.sat.LinearExpr.sum(vars.map { it.java }.toTypedArray())
)

fun weightedSum(vars: Iterable<IntVar>, coeffs: Iterable<Long>): LinearExpr = LinearExpr(
    com.google.ortools.sat.LinearExpr.weightedSum(
        vars.map { it.java }.toTypedArray(),
        coeffs.toList().toLongArray()
    )
)
```

**Why `Long` and not `Int` on coefficients?** OR-Tools Java insists on `long` across bounds, coefficients, and constants. If we let `Int` leak through our DSL, users write `3 * x` and the compiler picks the wrong overload, with a confusing message. By forcing `Long` everywhere in our API, we remove an entire class of beginner bug. (We *do* provide `operator fun Int.times(v: IntVar)` in v0.2 for ergonomics, converting internally — that's a later commit.)

### 5.5 `Constraints.kt` — the constraint block

```kotlin
package io.vanja.cpsat

@CpSatDsl
class ConstraintBuilder internal constructor(internal val model: CpModel) {
    infix fun LinearExpr.eq(rhs: Long) { model.java.addEquality(this.java, rhs) }
    infix fun LinearExpr.neq(rhs: Long) { model.java.addDifferent(this.java, rhs) }
    infix fun LinearExpr.le(rhs: Long) { model.java.addLessOrEqual(this.java, rhs) }
    infix fun LinearExpr.lt(rhs: Long) { model.java.addLessOrEqual(this.java, rhs - 1L) }
    infix fun LinearExpr.ge(rhs: Long) { model.java.addGreaterOrEqual(this.java, rhs) }
    infix fun LinearExpr.gt(rhs: Long) { model.java.addGreaterOrEqual(this.java, rhs + 1L) }

    // Variable-to-variable comparisons: promote the RHS IntVar to LinearExpr.
    infix fun LinearExpr.eq(rhs: IntVar) { model.java.addEquality(this.java, rhs.java) }
    infix fun LinearExpr.le(rhs: IntVar) { model.java.addLessOrEqual(this.java, rhs.java) }
    // ... similar for ge/lt/gt
}

fun CpModel.constraint(block: ConstraintBuilder.() -> Unit) {
    ConstraintBuilder(this).block()
}

// --- Top-level global constraints ---

fun CpModel.allDifferent(vars: Iterable<IntVar>) {
    java.addAllDifferent(vars.map { it.java }.toTypedArray())
}

fun CpModel.exactlyOne(bools: Iterable<BoolVar>) {
    java.addExactlyOne(bools.map { it.java }.toTypedArray())
}

fun CpModel.atMostOne(bools: Iterable<BoolVar>) {
    java.addAtMostOne(bools.map { it.java }.toTypedArray())
}
```

**Why a `constraint { }` block, not top-level `infix`?** Two reasons:

1. **Scope hygiene.** Inside the block, the receiver is a `ConstraintBuilder` that *only* exposes the comparison infixes. If `eq`/`le`/etc. were top-level, they'd show up everywhere in your codebase — including outside any CP-SAT model — polluting autocomplete.
2. **Future-proofing.** In Chapter 4 we add `enforceIf`; it naturally reads as `enforceIf(rainy) { umbrella eq 1 }` — a block expanding to multiple reified constraints. A block-based core makes that extension invisible.

### 5.6 `Objectives.kt` — minimize and maximize

```kotlin
package io.vanja.cpsat

@CpSatDsl
class ExprBuilder internal constructor(internal val model: CpModel) {
    // Expose everything needed to build a LinearExpr without polluting the scope
    // — variables are already referenced from the outer scope; we just need the arithmetic.
}

fun CpModel.minimize(block: ExprBuilder.() -> LinearExpr) {
    val expr = ExprBuilder(this).block()
    java.minimize(expr.java)
}

fun CpModel.maximize(block: ExprBuilder.() -> LinearExpr) {
    val expr = ExprBuilder(this).block()
    java.maximize(expr.java)
}
```

Simple for v0.1. Chapter 5 extends with `minimize { ... } subject to { ... }` patterns if needed, and Chapter 12 adds `lexicographic { }`.

### 5.7 `Solver.kt` — the sealed result type and the two solve paths

```kotlin
package io.vanja.cpsat

import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@CpSatDsl
class SolverParams internal constructor() {
    var maxTimeInSeconds: Double? = null
    var numSearchWorkers: Int? = null
    var randomSeed: Int? = null
    var logSearchProgress: Boolean? = null
    var relativeGapLimit: Double? = null
    // … expose more knobs as chapters demand them.

    internal fun applyTo(params: com.google.ortools.sat.SatParameters.Builder) {
        maxTimeInSeconds?.let { params.maxTimeInSeconds = it }
        numSearchWorkers?.let { params.numSearchWorkers = it }
        randomSeed?.let { params.randomSeed = it }
        logSearchProgress?.let { params.logSearchProgress = it }
        relativeGapLimit?.let { params.relativeGapLimit = it }
    }
}

class Assignment internal constructor(
    private val solver: CpSolver,
) {
    operator fun get(v: IntVar): Long = solver.value(v.java)
    operator fun get(v: BoolVar): Boolean = solver.booleanValue(v.java)
}

sealed interface SolveResult {
    data class Optimal(val values: Assignment, val objective: Long) : SolveResult
    data class Feasible(val values: Assignment, val objective: Long, val bound: Long, val gap: Double) : SolveResult
    object Infeasible : SolveResult
    object Unknown : SolveResult
    data class ModelInvalid(val message: String) : SolveResult
}

fun CpModel.solveBlocking(params: SolverParams.() -> Unit = {}): SolveResult {
    val validation = java.validate()
    if (validation.isNotEmpty()) return SolveResult.ModelInvalid(validation)

    val solver = CpSolver()
    SolverParams().apply(params).applyTo(solver.parameters.toBuilder())
    val status = solver.solve(java)
    return when (status) {
        CpSolverStatus.OPTIMAL -> SolveResult.Optimal(Assignment(solver), solver.objectiveValue().toLong())
        CpSolverStatus.FEASIBLE -> SolveResult.Feasible(
            Assignment(solver),
            objective = solver.objectiveValue().toLong(),
            bound = solver.bestObjectiveBound().toLong(),
            gap = computeGap(solver.objectiveValue(), solver.bestObjectiveBound())
        )
        CpSolverStatus.INFEASIBLE -> SolveResult.Infeasible
        CpSolverStatus.MODEL_INVALID -> SolveResult.ModelInvalid("solver reported MODEL_INVALID")
        else -> SolveResult.Unknown
    }
}

suspend fun CpModel.solve(params: SolverParams.() -> Unit = {}): SolveResult =
    withContext(Dispatchers.Default) { solveBlocking(params) }

private fun computeGap(obj: Double, bound: Double): Double =
    if (obj == 0.0) 0.0 else kotlin.math.abs(obj - bound) / kotlin.math.abs(obj)
```

**Why sealed?** Because `when (result)` is now exhaustive — the compiler forces you to handle every branch, and `Optimal`/`Feasible` carry structurally different data (no `bound`/`gap` on `Optimal` because those are irrelevant). This is what sealed types exist for.

**Why suspend `solve` + a blocking twin?** Solving can take minutes. In a coroutine-friendly codebase (Ktor, Compose, tests) you want `suspend fun solve()` so it runs on `Dispatchers.Default` without blocking the caller thread. For scripts or tests that don't care about suspend, `solveBlocking` is convenient. CP-SAT itself blocks (it's C++ via JNI); the coroutine wrapper only frees the caller's thread — we're not doing any magic.

### 5.8 Rewrite Chapter 2 with the DSL

Scaffold: `apps/kt-cp-sat/ch03-build-cpsat-kt/src/main/kotlin/HelloDsl.kt`.

```kotlin
import io.vanja.cpsat.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val model = cpModel {
        val x = intVar("x", 0..10)
        val y = intVar("y", 0..10)
        constraint { 3L * x + 2L * y eq 12L }
        constraint { x + y le 5L }
        maximize { x + y }
    }

    when (val result = model.solve()) {
        is SolveResult.Optimal -> {
            // We need x, y accessible here; either close over them or re-fetch via a name-lookup.
            // For v0.1 simplicity we close over them — see note below.
            println("objective = ${result.objective}")
        }
        is SolveResult.Feasible -> println("feasible obj=${result.objective}, gap=${result.gap}")
        is SolveResult.Infeasible -> println("infeasible")
        is SolveResult.Unknown -> println("time up")
        is SolveResult.ModelInvalid -> println("invalid model: ${result.message}")
    }
}
```

**Closing-over-variables note.** There's a small design wrinkle: `cpModel { ... }` returns a `CpModel`, but `x` and `y` are declared inside the block. To reference them outside, you either (a) return them from the block or (b) restructure so the `solve` and the `when` branches live inside the block. For v0.1 we recommend pattern (b) for teaching clarity; pattern (a) with a `data class Outputs(val x: IntVar, val y: IntVar)` and a bespoke `cpModel<Outputs>` overload comes as a v0.2 ergonomics improvement. Exercise 3.2 explores this.

### 5.9 Parity test — the trust contract

In `libs/cpsat-kt/src/test/kotlin/io/vanja/cpsat/ParityTests.kt`:

```kotlin
package io.vanja.cpsat

import com.google.ortools.Loader
import com.google.ortools.sat.CpModel as JavaModel
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverStatus
import com.google.ortools.sat.LinearExpr as JavaLinearExpr
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ParityTests : StringSpec({
    "DSL and raw Java agree on the Ch 2 model" {
        // DSL version
        val dslModel = cpModel {
            val x = intVar("x", 0..10)
            val y = intVar("y", 0..10)
            constraint { 3L * x + 2L * y eq 12L }
            constraint { x + y le 5L }
            maximize { x + y }
        }
        val dslResult = dslModel.solveBlocking()

        // Raw Java version
        Loader.loadNativeLibraries()
        val javaModel = JavaModel()
        val jx = javaModel.newIntVar(0L, 10L, "x")
        val jy = javaModel.newIntVar(0L, 10L, "y")
        javaModel.addEquality(JavaLinearExpr.newBuilder().addTerm(jx, 3L).addTerm(jy, 2L).build(), 12L)
        javaModel.addLessOrEqual(JavaLinearExpr.newBuilder().add(jx).add(jy).build(), 5L)
        javaModel.maximize(JavaLinearExpr.newBuilder().add(jx).add(jy).build())
        val javaSolver = CpSolver()
        val javaStatus = javaSolver.solve(javaModel)

        javaStatus shouldBe CpSolverStatus.OPTIMAL
        (dslResult as SolveResult.Optimal).objective shouldBe javaSolver.objectiveValue().toLong()
    }
})
```

Parity tests are the library's trust contract. Every nontrivial DSL construct gets one. If a parity test fails, the wrapper has a bug — not the solver.

### 5.10 ADR 0001 — package and coord

Before shipping, write `docs/adr/0001-cpsat-kt.md`:

```markdown
# ADR 0001 — cpsat-kt package and Maven coord

**Status:** Accepted · 2026-04-19

## Context
We need a stable identifier for the Kotlin DSL library. Three options surveyed:
- `io.vanja.cpsat` + `io.vanja:cpsat-kt`
- `io.cpsatkt` + `io.cpsatkt:cpsat-kt`
- `dev.vanja.cpsat` + `dev.vanja:cpsat-kt`

## Decision
Package: `io.vanja.cpsat`. Coord: `io.vanja:cpsat-kt`. The domain is the
maintainer's personal domain (reverse-DNS convention), and the artifactId
matches the repo name.

## Consequences
- Future publish to Maven Central requires domain verification on Sonatype;
  the maintainer owns `vanja.co` and will verify `co.petreski` if/when we
  re-open this.
- Easier to fork / rename later: a pure `io.cpsatkt` coord is available as a v2 break
  if the library ever becomes community-owned.
```

This ADR is the template for every non-obvious project decision going forward.

## 6. MiniZinc implementation (if relevant)

N/A — this is a language-wrapper chapter. MiniZinc doesn't enter until Chapter 7.

## 7. Comparison & takeaways

| Dimension | Raw Java API from Kotlin | `cpsat-kt` DSL |
|---|---|---|
| Lines for Ch 2 model | ~13 | ~6 |
| Arithmetic | `LinearExpr.newBuilder()...build()` | `3 * x + 2 * y` |
| Comparison | `addLessOrEqual(...)` | `x + y le 5` (inside `constraint { }`) |
| Result | `when (status: CpSolverStatus)` | `when (result: SolveResult)` — sealed, data-carrying |
| Setup | Manual `Loader.loadNativeLibraries()` | Implicit in `cpModel { }` |
| Escape hatch | always raw Java | `.toJava()` on every wrapper |
| Test paradigm | JUnit5 ad hoc | Kotest spec style with ParityTests |

**Key insight.** *The DSL is just a thin syntactic layer over the Java API; there is no hidden magic. Every DSL call maps to a Java call within 2-3 hops.* This is the library's contract: if you ever need to see what's happening, `.toJava()` gives you the underlying object. Teach that to your future self — it's what makes the library trustworthy.

**Secondary insight.** *Sealed classes + data classes turn error handling from a ritual into a compile-time obligation.* The Python version has no equivalent; you can silently forget to check `status == OPTIMAL` and silently get the wrong answer. Kotlin's type system won't let you.

**Tertiary insight.** *Library design is library politics.* What goes in v0.1 and what defers to v0.2+ is a product decision. We chose: variables + linear + a few globals. We deferred: intervals, half-reification, streaming. That choice aligns with the chapter plan — by the time Chapter 9 needs intervals, you'll have the muscle memory to extend the library.

## 8. Exercises

### Exercise 3.1 — Add a new reified helper with TDD

**Problem.** Add `xor(b1: BoolVar, b2: BoolVar, result: BoolVar)` to `cpsat-kt` — a constraint enforcing `result ⇔ (b1 XOR b2)`. Write the failing Kotest spec first. Implement until green.

**Expected output:** a new file `Reified.kt` exposing `fun CpModel.xor(b1: BoolVar, b2: BoolVar, result: BoolVar)`. Spec in `ReifiedSpec.kt` covers all four truth-table rows.

**Acceptance criteria:** `./gradlew test` passes. Both directions of the biconditional are enforced (use two `onlyEnforceIf` posts, one per direction, or leverage `addBoolXor` on the Java side).

<details><summary>Hint</summary>

CP-SAT ships `addBoolXor(literals)` which constrains the XOR of all literals to be 1. For `result ⇔ (b1 XOR b2)`, rewrite as: post `addBoolXor([b1, b2, result.not()])`. The `.not()` on `BoolVar` returns a `Literal`, so this is where you'll need the literal-wrapping scaffolding mentioned in §5.3 TODO. Take the smallest step — implement `.not()` on `BoolVar` now (a `Literal` wrapper in Variables.kt) so this exercise works and the library gets closer to Chapter 4's needs.

</details>

### Exercise 3.2 — Make `cpModel` return variables ergonomically

**Problem.** The current `cpModel { }` returns only the `CpModel`; you lose references to the variables after the block. Add a generic `fun <T> cpModel(block: CpModel.() -> T): Pair<CpModel, T>` that returns both the model and whatever the block produces. Rewrite the Ch 2 DSL sample to return the variables cleanly.

**Expected output shape:**

```kotlin
val (model, vars) = cpModel {
    val x = intVar("x", 0..10)
    val y = intVar("y", 0..10)
    constraint { 3L * x + 2L * y eq 12L }
    maximize { x + y }
    x to y
}
val result = model.solve()
if (result is SolveResult.Optimal) println("x=${result.values[vars.first]}, y=${result.values[vars.second]}")
```

**Acceptance criteria:** both overloads coexist; existing tests still pass; new overload has its own Kotest spec.

<details><summary>Hint</summary>

Two overloads of the same name with different return types are fine in Kotlin as long as the block's signature differs. `CpModel.() -> Unit` vs `CpModel.() -> T`. Be careful with overload resolution — annotate with `@JvmName` if the JVM can't disambiguate (it usually can for different generic types).

</details>

### Exercise 3.3 — Add your own N-Queens spec

**Problem.** Add `libs/cpsat-kt/src/test/kotlin/io/vanja/cpsat/examples/NQueensSpec.kt`. Encode N=4, 8, 12 as Kotest tests. Expected solution counts: 2, 92, 14200. Don't over-engineer; this is muscle memory for the DSL.

**Acceptance criteria:** `./gradlew test` runs the spec; each N prints wall-time and matches the expected count. You'll need the `solveFlow` pattern for enumerating — stub it with a simple callback collector for v0.1.

<details><summary>Hint</summary>

For enumeration, either (a) use `solver.parameters.enumerateAllSolutions = true` via a `SolverParams` passthrough, and a callback that increments a counter; or (b) wait until Chapter 5's `solveFlow` and come back. Option (a) is fine — use `solveBlocking` with a parameters block that sets `enumerateAllSolutions`.

</details>

### Exercise 3.4 — Read and extend `ParityTests`

**Problem.** Open `ParityTests.kt`. Add two more parity cases: (a) pure boolean satisfiability (`boolVar` × 3, `exactlyOne`), and (b) a case with `allDifferent` on 4 variables over `0..3` and assert 24 solutions. Compare with raw Java.

**Acceptance criteria:** new test cases pass; if either fails, it's evidence of a wrapper bug and you fix the wrapper before closing the exercise.

<details><summary>Hint</summary>

For the `allDifferent` case over `0..3` (4 values, 4 variables), all solutions are permutations — there should be exactly 24 when you enumerate.

</details>

### Exercise 3.5 — Document the v0.1 public surface

**Problem.** Add KDoc (`/** */`) comments on every public top-level function and class in `cpsat-kt`. Run Dokka (`./gradlew dokkaHtml`) and verify the output is sensible.

**Acceptance criteria:** KDoc covers: what the function does, its parameters, its return, any side effects (e.g. "posts a constraint on the current model"), and one micro-example. Dokka HTML renders without warnings.

<details><summary>Hint</summary>

Dokka is configured in the design doc; the plugin is `org.jetbrains.dokka`. `./gradlew dokkaHtml` outputs to `build/dokka/html/`. Open `index.html` in a browser; it should look like a tiny Javadoc site. Aim for 2-4 sentences per item — not essays.

</details>

Solutions for 3.1, 3.2, 3.3 ship in `libs/cpsat-kt/src/test/kotlin/io/vanja/cpsat/examples/solutions/`. 3.4 and 3.5 are open-ended — compare with `apps/kt-cp-sat/ch03-build-cpsat-kt/solutions/` for one worked version.

## 9. Self-check

**Q1.** What does `@DslMarker` actually prevent, and what would go wrong without it?

<details><summary>Answer</summary>

`@DslMarker` prevents *implicit receiver resolution* across nested DSL scopes with matching annotation. Without it, inside `constraint { ... }` you could accidentally call `intVar(name, range)` — Kotlin would look up the enclosing `CpModel` receiver and find the method there. Now you've silently added a variable to your model in a place that reads like a constraint post. `@DslMarker` forces the inner block to see only the `ConstraintBuilder` members; accessing outer-receiver methods requires explicit `this@cpModel.intVar(...)`. It's a guardrail for the reader, not a limitation.

</details>

**Q2.** Why does `solveBlocking` return a `SolveResult` instead of throwing on infeasibility?

<details><summary>Answer</summary>

Infeasibility is a *valid outcome* of a solve — the solver proved no solution exists. Throwing would conflate "the proof found no solution" with "something went wrong," which is semantically backwards. A sealed `SolveResult` makes the caller acknowledge and handle each outcome in exhaustive `when`, forcing thoughtful error/absence handling at compile time. We reserve exceptions for truly exceptional cases (e.g. the JVM couldn't load the native library at all — which `ensureLoaded()` handles).

</details>

**Q3.** What's a Gradle composite build, and why does it beat `publishToMavenLocal` during library development?

<details><summary>Answer</summary>

A composite build is Gradle's mechanism for consuming one build from another as if it were a published dependency, *without* going through Maven Local. You add `includeBuild("../../libs/cpsat-kt")` to the consumer's `settings.gradle.kts` and then declare a normal `implementation("io.vanja:cpsat-kt")`. Gradle resolves the coord to the composite module at build time. Advantage over `publishToMavenLocal`: no publish round-trip, no cache invalidation confusion, faster iteration, and changes to the library are picked up on the next `./gradlew run` of the consumer. Essentially: one IDE project sees everything.

</details>

**Q4.** Why does `cpsat-kt` use a Kotlin `Flow` for streaming solutions instead of the classic Observer pattern?

<details><summary>Answer</summary>

(Answered in the abstract here — we add `solveFlow` in Chapter 5.) `Flow` is coroutine-native, backpressure-aware, and cancellation-aware. Under a long-running solve you can `launch { model.solveFlow().collect { ... } }` from any coroutine scope; cancellation propagates cleanly to the solver; the consumer can apply `.take(5)` or `.debounce(100.ms)` using standard operators. A classic listener interface would require custom cancellation, custom composition, and manual thread handoff. `Flow` gets you all of that for free.

</details>

**Q5.** Which pieces of OR-Tools CP-SAT are intentionally *out of scope* for `cpsat-kt` v0.1 and when do they land?

<details><summary>Answer</summary>

Out of scope for v0.1: intervals (lands Chapter 9), `enforceIf` / half-reification (Chapter 4), other global constraints — Circuit, Automaton, Table, Element, Inverse, Reservoir, LexLeq — (Chapter 6), `solveFlow` streaming (Chapter 5), lexicographic objectives (Chapter 12), raw protobuf escape-hatch parameters (as needed). The staging matches the plan: each chapter adds exactly what it needs, with tests. The library grows linearly, and you always ship a runnable v-something at the end of every chapter.

</details>

## 10. What this unlocks

You now have a live DSL library. From Chapter 4 onward, every Kotlin model uses `cpsat-kt` — no more raw Java API ceremony. Chapter 4 puts it to work on N-Queens and cryptarithmetic, which stress-test the wrapper and reveal the first missing pieces (half-reification).

## 11. Further reading

- Kotlin team, [*Type-safe builders*](https://kotlinlang.org/docs/type-safe-builders.html) — canonical reference for the DSL pattern.
- Kotlin team, [*Operator overloading*](https://kotlinlang.org/docs/operator-overloading.html) — every `operator fun` in `Expressions.kt` comes from this page.
- Gradle, [*Composite builds*](https://docs.gradle.org/current/userguide/composite_builds.html) — how `includeBuild` works.
- Gradle, [*Version catalogs*](https://docs.gradle.org/current/userguide/platforms.html) — why `libs.versions.toml` is worth the extra file.
- Kotest, [*Spec styles*](https://kotest.io/docs/framework/testing-styles.html) — pick one (we use `StringSpec`) and stick with it; consistency beats cleverness.
- Moskala, [*Effective Kotlin*](https://leanpub.com/effectivekotlin) — especially chapters on API design; many of the choices above come from its advice.
- `docs/knowledge/cpsat-kt/overview.md` — the design doc you'll keep updating as the library grows.
