# cpsat-kt

[![Kotlin](https://img.shields.io/badge/kotlin-2.3%2B-7F52FF.svg)](https://kotlinlang.org/)
[![JDK](https://img.shields.io/badge/JDK-25-orange.svg)](https://openjdk.org/projects/jdk/25/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](./LICENSE)
[![Status](https://img.shields.io/badge/status-0.1.0%20--%20preview-yellow.svg)](./CHANGELOG.md)

An idiomatic Kotlin DSL over [Google OR-Tools CP-SAT][or-tools].

Build constraint-programming models with operator overloading, sealed types,
coroutine-friendly solve entry points, and a proper Kotlin feel — without
giving up any of CP-SAT's power.

**Status:** v0.1.0 — 41/41 tests pass; API stable enough for learning and
prototyping. Breaking changes may land before v1.0; see
[CHANGELOG.md](./CHANGELOG.md).

```kotlin
import io.vanja.cpsat.*

val model = cpModel {
    val x = intVar("x", 0..10)
    val y = intVar("y", 0..10)

    constraint {
        +(3 * x + 2 * y eq 12)
        +(x + y le 5)
    }

    minimize { x + y }
}

when (val res = model.solveBlocking { randomSeed = 42 }) {
    is SolveResult.Optimal  -> println("optimum = ${res.objective}")
    is SolveResult.Feasible -> println("best so far = ${res.objective}")
    SolveResult.Infeasible  -> println("no solution")
    SolveResult.Unknown     -> println("time out")
    is SolveResult.ModelInvalid -> println("broken model: ${res.message}")
}
```

## Why another wrapper?

OR-Tools' Java API is serviceable, but calling it from Kotlin feels like
calling Java from Java:

- `LinearExpr.newBuilder().add(...).addTerm(..., coef).build()` rather than
  `3*x + 2*y`.
- Raw `IntVar[]` arrays for global constraints.
- Opaque `CpSolverStatus` + polled `solver.value(...)` after each solve.
- No coroutine / flow support for streaming solutions.

`cpsat-kt` wraps all of that with:

- **Operator overloading** -- `+ - * unaryMinus`, infix `eq / neq / le / lt / ge / gt`.
- **Type-safe DSL** -- `@DslMarker`-scoped builders for models, constraints, and intervals.
- **Sealed `SolveResult`** -- exhaustive `when`, no nulls, clear status.
- **Coroutine aware** -- `suspend fun solve(...)` runs on `Dispatchers.Default`,
  `solveFlow(...)` streams solutions as a `Flow<Solution>`.
- **Global constraints** -- `allDifferent`, `element`, `inverse`, `table`,
  `automaton`, `circuit`, `noOverlap`, `cumulative`, `reservoir`, `lexLeq`.
- **Intervals + scheduling** -- `interval { }` and `optionalInterval { }`
  builders with automatic `start + size == end` handling.
- **Lexicographic objectives** -- `lexicographic { primary { ... }; secondary { ... } }`,
  solved stage-by-stage in `solveLexicographic(...)`.
- **Escape hatches** -- every wrapper exposes `toJava()` / `asLinearArgument()`
  so you can drop down to the native API whenever the DSL doesn't cover
  something exotic.

## Install

The library is published to local Maven via the standard `maven-publish`
plugin.

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.vanja:cpsat-kt:0.1.0")
}
```

Or include it as a composite build:

```kotlin
// settings.gradle.kts
includeBuild("../../libs/cpsat-kt")
```

The library pulls in:

- `com.google.ortools:ortools-java:9.15.6755` (classifier JARs auto-resolve
  per platform -- `ortools-darwin-aarch64`, `ortools-linux-x86-64`, etc.).
- `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2`.

## Requirements

- **JDK 25** -- the library is built with Kotlin 2.3.20 and targets JVM 25.
  Consumers must run on JDK 25+.
- **Kotlin 2.3+** -- uses K2-only constructs.
- **Gradle 9.x** -- for JVM 25 toolchain support.

## Core concepts

### Model

Build a model with `cpModel { }`. The block receiver is `CpModel`.

```kotlin
val model = cpModel {
    val x = intVar("x", 0..10)     // IntRange domain
    val y = intVar("y", 0L..10L)    // LongRange domain
    val z = intVar("z", listOf(1L, 3L, 5L, 7L))  // sparse domain
    val b = boolVar("b")

    constraint {
        +(x + y eq z)
        +(b implies (x eq 3))       // see Enforcement.kt
    }
}
```

### Variables

- `intVar(name, IntRange | LongRange | Iterable<Long>)` -- bounded integer.
- `boolVar(name)` -- boolean, doubles as a 0/1 integer.
- `intVarList(prefix, count, domain)` / `boolVarList(prefix, count)` -- batches.
- `constant(k)` -- a fixed-value linear expression.

### Expressions and relations

Use familiar operators. Everything returns `LinearExpr`.

```kotlin
val expr = 3 * x + 2 * y - 5         // LinearExpr
val sumExpr = sum(listOf(a, b, c))   // or sum(a, b, c)
val weighted = weightedSum(vars, coefs)  // Σ coefs[i] * vars[i]

// Inside constraint { }
+(expr le 10)
+(sumExpr eq 7)
+(x ne y)           // use `neq` alias if `ne` is shadowed
```

### Global constraints

All live on `ConstraintBuilder` (inside a `constraint { }` block):

```kotlin
constraint {
    allDifferent(xs)
    element(index, listOf(10L, 20L, 30L), target)   // target = data[index]
    inverse(f, g)                                    // g[f[i]] == i
    table(listOf(x, y), listOf(longArrayOf(1, 2), longArrayOf(3, 4)))
    circuit(listOf(Triple(0, 1, edgeAB), Triple(1, 0, edgeBA)))
    noOverlap(listOf(int1, int2))
    cumulative(intervals, demands = listOf(2, 3, 1), capacity = 5)
    reservoir(times = listOf(arrival, departure), levels = listOf(1, -1), min = 0, max = 3)
    lexLeq(listOf(a1, a2), listOf(b1, b2))
}
```

### Reification / enforcement

```kotlin
enforceIf(b) {
    +(x eq 5)
    +(y ge 3)
}

enforceIfAll(listOf(b1, b2)) {
    +(z le 10)
}

enforceIfAny(listOf(b1, b2)) {   // builds an OR via auxiliary boolean
    +(z le 10)
}
```

### Intervals + scheduling

```kotlin
val t1 = interval("t1") {
    start = intVar("s1", 0..horizon)
    size = 3L
}

val t2 = optionalInterval("t2", presence = boolVar("p2")) {
    start = intVar("s2", 0..horizon)
    sizeExpr = durationVar
}

constraint {
    noOverlap(listOf(t1, t2))
    cumulative(listOf(t1, t2), demands = listOf(1, 2), capacity = 2)
}
```

### Objectives

```kotlin
// Single objective:
minimize { 3 * cost + 2 * delay }
maximize { profit }

// Lexicographic (stage-by-stage):
val stages = lexicographic {
    primary(Sense.MINIMIZE) { coverageViolations }
    secondary(Sense.MINIMIZE) { preferenceViolations }
}
val result = solveLexicographic(stages) {
    randomSeed = 42
    maxTimeInSeconds = 30.0
}
```

### Solving

```kotlin
// Blocking:
val res = model.solveBlocking {
    maxTimeInSeconds = 10.0
    numSearchWorkers = 4
    randomSeed = 42
    logSearchProgress = false
}

// Suspending (runs on Dispatchers.Default):
val res = model.solve {
    randomSeed = 42
}

// Streaming intermediate solutions:
model.solveFlow { maxSolutions = 10 }.collect { sol ->
    println("found obj=${sol.objective}  x=${sol[x]}")
}

// Raw proto escape hatch:
model.solveBlocking {
    rawProto {
        boolean_encoding_level = 2
        // ... any SatParameters field ...
    }
}
```

### Reading the solution

```kotlin
when (val r = model.solveBlocking()) {
    is SolveResult.Optimal -> {
        val xVal: Long    = r.values[x]
        val bVal: Boolean = r.values[bFlag]
        val ivalVal       = r.values[interval]  // IntervalValue(start, size, end, present)
        val exprVal       = r.values.valueOf(3 * x + 2 * y)
    }
    // ...
}
```

## Native libraries

OR-Tools ships per-platform native libraries (e.g. `libjniortools.dylib` on
macOS). The library handles loading automatically on first use via
`Loader.loadNativeLibraries()` -- you don't need to call anything explicit.

If you bundle `cpsat-kt` into a fat JAR, remember to include the correct
`ortools-<os>-<arch>` classifier JAR for your deployment target; the native
libraries are packaged inside those.

## Roadmap

See `docs/knowledge/cpsat-kt/overview.md` in the parent repository. The
0.1.x series is scoped to cover the CP-SAT surface exercised by chapters
2-13 of the [cp-deep-dive learning project][project].

## Contributing

This library lives inside the [cp-deep-dive][project] learning project.
Bug reports, design feedback, and PRs are welcome — see
[CONTRIBUTING.md](../../CONTRIBUTING.md) in the parent repo for the dev
loop, commit conventions, and Developer Certificate of Origin sign-off.

## Citation

If you use `cpsat-kt` in academic or public work, please cite the parent
project via [CITATION.cff](../../CITATION.cff).

## License

Apache 2.0 — see [LICENSE](./LICENSE).

[or-tools]: https://developers.google.com/optimization/cp/cp_solver
[project]: ../../
