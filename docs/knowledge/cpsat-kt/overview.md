# cpsat-kt — Idiomatic Kotlin DSL over OR-Tools CP-SAT

**Status:** design locked in [`docs/plan.md`](../../plan.md) v0.2; implementation starts in Chapter 3. Published artifact eventually: `io.vanja:cpsat-kt:X.Y.Z` on Maven Central.

## Why this library exists

OR-Tools ships first-party bindings for C++, Python, Java, C#, and Go. **There is no official Kotlin binding**, and no maintained third-party one. The standard advice is "just call the Java API from Kotlin" — and you can, but the ergonomics are terrible:

- No operator overloading → `x.add(y.mul(3))` instead of `x + y * 3`.
- No sealed-class result types → you check status codes with `if` chains.
- No coroutines → blocking `solver.solve()` ties up threads.
- No DSL → model construction is a wall of `model.add...` calls.
- Null-platform types from Java bleed in everywhere.

`cpsat-kt` fixes this. It is a **thin idiomatic Kotlin wrapper** that exposes every CP-SAT feature through a DSL, operator-overloaded expression builder, sealed `SolveResult` type, and coroutine-native solver. Every wrapper object has a `.toJava()` escape hatch for the rare case where the Java API is needed directly.

## Design principles

1. **Zero magic.** Every DSL call maps 1:1 to an underlying OR-Tools Java call. No reflection, no codegen, no annotation processing.
2. **No hidden state.** All state lives on an explicit `CpModel` instance. No singletons, no thread-locals, no global config.
3. **Full type safety.** Generics where useful, sealed classes for result branching, no unchecked casts in user-facing code.
4. **Coroutines-first.** `solve()` is a `suspend fun`; blocking variants are opt-in via `solveBlocking()`.
5. **Ergonomic but escape-hatched.** Every wrapper exposes `.toJava()`. Power users can drop down any time.
6. **Minimal dependencies.** Only `com.google.ortools:ortools-java:<pinned>` and its transitives. No Arrow, no Koin, no opinionated framework pulls.
7. **Stable API before completeness.** Cover the 80% of CP-SAT that NSP needs first; advanced features (custom search strategies, decision strategies, raw protobuf edits) come later.

## Scope

### In scope (v0.x)
- **Model construction:** `CpModel`, `IntVar`, `BoolVar`, `IntervalVar`, `LinearExpr`
- **All constraint families:** linear, boolean combinators, global (`AllDifferent`, `Circuit`, `Cumulative`, `NoOverlap`, `Table`, `Automaton`, `Element`, `Inverse`, `LexicographicLessEqual`, `Reservoir`)
- **Objectives:** `minimize`, `maximize`, lexicographic patterns
- **Solver:** `CpSolver`, `SolverParameters`, status enum, objective + bound access
- **Callbacks:** `SolutionCallback` wrapped as a Kotlin `Flow<Solution>` for streaming
- **Reification / enforcement:** `enforceIf(...)` blocks
- **Helpers:** common idioms like `exactlyOne`, `atMostOne`, `channel(bool, intExpr, value)`, sliding-window helpers, automaton builder
- **Serialization:** save/load `CpModelProto` for debugging
- **Testing helpers:** parity test harness (run same model in raw Java + DSL, assert equality)

### Out of scope (for now)
- OR-Tools' routing solver (`RoutingModel`)
- OR-Tools' LP / MPSolver
- Other solvers (CP-SAT only)
- GPU / distributed solving

## The DSL at a glance

### Minimal hello-world

```kotlin
import io.vanja.cpsat.*

val result = cpModel {
    val x = intVar("x", 0..10)
    val y = intVar("y", 0..10)

    constraint { 3 * x + 2 * y eq 12 }
    constraint { x + y le 5 }

    maximize { x + y }
}.solve()

when (result) {
    is SolveResult.Optimal -> println("x=${result[x]}, y=${result[y]}, obj=${result.objective}")
    is SolveResult.Feasible -> println("suboptimal; gap=${result.gap}")
    is SolveResult.Infeasible -> println("no solution")
    is SolveResult.Unknown -> println("time up")
    is SolveResult.ModelInvalid -> error(result.message)
}
```

### N-Queens via DSL

```kotlin
fun nQueens(n: Int) = cpModel {
    val queens = List(n) { i -> intVar("q$i", 0 until n) }

    allDifferent(queens)
    allDifferent(queens.mapIndexed { i, q -> q + i })   // diagonals \
    allDifferent(queens.mapIndexed { i, q -> q - i })   // diagonals /
}.solve()
```

### Scheduling with intervals

```kotlin
cpModel {
    val tasks = jobs.map { job ->
        interval(name = "task-${job.id}") {
            start = intVar("start-${job.id}", 0..horizon)
            size = job.duration
            end = start + size
        }
    }
    noOverlap(tasks)
    minimize { tasks.maxOf { it.end } }   // minimize makespan
}.solve()
```

### Reified / optional constraints

```kotlin
val rainy = boolVar("rainy")
enforceIf(rainy) {
    umbrella eq 1
    outdoor eq 0
}
```

### Streaming solutions via Flow

```kotlin
cpModel {
    // ...
}.solveFlow(params = { numSearchWorkers = 8 }).collect { sol ->
    println("intermediate: obj=${sol.objective}, bound=${sol.bound}")
}
```

## Public API surface (v0.x outline)

- **Builder entry point:** `fun cpModel(block: CpModel.() -> Unit): CpModel`
- **Variables:**
  - `CpModel.intVar(name: String, domain: IntRange): IntVar`
  - `CpModel.intVar(name: String, domain: Iterable<Long>): IntVar` (sparse domains)
  - `CpModel.boolVar(name: String): BoolVar`
  - `CpModel.constant(value: Long): IntVar`
- **Intervals:** `CpModel.interval(name: String, block: IntervalBuilder.() -> Unit): IntervalVar`, with `optional = true` variant
- **Expressions:** `IntVar`/`BoolVar` implement `LinearExpr`; operators `+`, `-`, `*` (with `Long`), `unaryMinus`; infix `eq`, `neq`, `le`, `lt`, `ge`, `gt`; `sum(vars)`, `weightedSum(vars, coefs)`
- **Constraints:**
  - `CpModel.constraint(block: ConstraintBuilder.() -> Unit)` for inline linear/boolean constraints
  - Global constraint top-levels: `allDifferent(vars)`, `exactlyOne(bools)`, `atMostOne(bools)`, `atLeastOne(bools)`, `circuit(arcs)`, `table(vars, tuples)`, `automaton(vars, startState, transitions, finalStates)`, `element(index, values, target)`, `inverse(f, g)`, `noOverlap(intervals)`, `cumulative(intervals, demands, capacity)`, `reservoir(times, demands, min, max)`, `lexLeq(vars1, vars2)`
- **Enforcement:** `enforceIf(bool: BoolVar, block: ConstraintBuilder.() -> Unit)`, `enforceIfAll(bools, block)`, `enforceIfAny(bools, block)`
- **Objective:** `CpModel.minimize(block: ExprBuilder.() -> LinearExpr)`, `maximize(block)`; `lexicographic { ...primary...; ...secondary... }` for lex objectives
- **Solver:**
  - `CpModel.solve(params: SolverParams.() -> Unit = {}): SolveResult` (suspend)
  - `CpModel.solveBlocking(params): SolveResult` (blocking)
  - `CpModel.solveFlow(params): Flow<Solution>` (streaming incumbents)
- **Parameters:** `class SolverParams` with props matching `SatParameters` proto (`maxTimeInSeconds`, `numSearchWorkers`, `randomSeed`, `logSearchProgress`, `cpModelPresolve`, `linearizationLevel`, `searchBranching`, …)
- **Results:**
  ```kotlin
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
      operator fun get(v: IntervalVar): IntervalAssignment
  }
  ```
- **Escape hatch:** every wrapper exposes `.toJava()`, e.g. `model.toJava(): com.google.ortools.sat.CpModel`

## Package layout

```
libs/cpsat-kt/
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── gradle/libs.versions.toml         <- version catalog (ortools-java, kotest, etc.)
├── README.md                         <- quickstart + publishing info
├── CHANGELOG.md
├── LICENSE                           <- Apache 2.0 (matches OR-Tools)
├── src/
│   ├── main/kotlin/io/vanja/cpsat/
│   │   ├── Model.kt                  <- cpModel { }, CpModel class
│   │   ├── Variables.kt              <- IntVar, BoolVar, constant
│   │   ├── Intervals.kt              <- IntervalVar, IntervalBuilder
│   │   ├── Expressions.kt            <- LinearExpr, operators, infix predicates
│   │   ├── Constraints.kt            <- constraint{}, global constraint top-levels
│   │   ├── Enforcement.kt            <- enforceIf, enforceIfAll, enforceIfAny
│   │   ├── Objectives.kt             <- minimize/maximize/lexicographic
│   │   ├── Solver.kt                 <- solve, solveBlocking, solveFlow, SolveResult, SolverParams
│   │   ├── Callbacks.kt              <- SolutionCallback → Flow<Solution> adapter
│   │   ├── Natives.kt                <- Loader.loadNativeLibraries() handling + platform check
│   │   └── internal/                 <- implementation details not exposed
│   └── test/kotlin/io/vanja/cpsat/
│       ├── VariablesSpec.kt
│       ├── ConstraintsSpec.kt
│       ├── SolverSpec.kt
│       ├── ParityTests.kt            <- run same model via raw Java + DSL, assert equality
│       └── examples/                 <- N-Queens, SEND+MORE, knapsack, job-shop, NSP-tiny
└── examples/                         <- standalone runnable examples (not test classpath)
```

## Build tooling

- **Gradle 9.x** with Kotlin DSL
- **Kotlin 2.1+**, JVM target **25** (JDK 25 LTS)
- **Version catalog** (`gradle/libs.versions.toml`) to centralize dep versions
- **Kotest** 5.x for tests
- **Dokka** for API docs
- **Maven Publish plugin** + **jreleaser** for releases (once we're ready to publish)
- Consumed during learning phases via Gradle composite build: `includeBuild("../../libs/cpsat-kt")` in `apps/kt-cp-sat/settings.gradle.kts`

## Testing strategy

1. **Unit tests** (Kotest spec style) for each DSL surface.
2. **Parity tests** — a harness that takes a model written in our DSL and an equivalent model in raw Java OR-Tools, runs both, asserts the same `SolveResult`. This catches wrapper bugs early.
3. **Property-based tests** via Kotest's `arb` — generate random CSPs within a bounded grammar, assert both paths agree on satisfiability and objective value.
4. **Example models as regression tests** — N-Queens for N=4, 8, 12; SEND+MORE; small job-shop; all shipped in `test/kotlin/io/vanja/cpsat/examples/` and expected outputs pinned.
5. **CI matrix** — JDK 25 only (we don't support older JDKs).

## Versioning & publishing

- Semver: `MAJOR.MINOR.PATCH`.
- Pre-1.0 (`0.x.y`) during the learning phases — breaking changes allowed.
- `1.0` when the NSP app ships using a stable DSL surface.
- Published to Maven Central via Sonatype. Coord: `io.vanja:cpsat-kt`. Signing via jreleaser.
- `CHANGELOG.md` follows Keep-a-Changelog.

## Usage inside this repo

During the learning phases, `cpsat-kt` is consumed via composite build (no publish needed):

```kotlin
// apps/kt-cp-sat/settings.gradle.kts
includeBuild("../../libs/cpsat-kt")

// apps/kt-cp-sat/build.gradle.kts
dependencies {
    implementation("io.vanja:cpsat-kt")
}
```

This means local edits to `libs/cpsat-kt/src/**` are instantly available to chapter apps without publish round-trip. Same for `apps/kt-api/`.

## Open questions (pending answers during Chapter 3)

- **Package name:** `io.vanja.cpsat` (Vanja's domain) vs `io.cpsatkt` (neutral) vs `dev.vanja.cpsat`. Default: `io.vanja.cpsat`.
- **Group coordinate:** `io.vanja:cpsat-kt` vs `co.petreski:cpsat-kt`. Default: `io.vanja`.
- **IntRange vs custom `IntDomain`:** Kotlin's `IntRange` is cheap + ergonomic but doesn't express sparse domains. We'll support `IntRange`, `LongRange`, and `Iterable<Long>` overloads.
- **How much of `SatParameters` to surface:** start with the 20 most-used fields explicitly typed; expose `fun rawProto(block: SatParameters.Builder.() -> Unit)` for the rest.
- **Should we return `Flow<Solution>` eagerly or lazily?** Probably eagerly with a bounded internal buffer.

All decisions get written up as ADRs under `docs/adr/` when locked in.

## References

- OR-Tools Java API (ground truth): https://developers.google.com/optimization/reference/java/
- OR-Tools source (javadoc in repo): https://github.com/google/or-tools/tree/stable/ortools/java
- Kotlin DSL design guide: https://kotlinlang.org/docs/type-safe-builders.html
- Gradle composite builds: https://docs.gradle.org/current/userguide/composite_builds.html
- Effective Kotlin (Marcin Moskala) — informs idioms
- [`docs/knowledge/cp-sat/overview.md`](../cp-sat/overview.md) — the underlying solver
- [`docs/knowledge/cp-sat/python-vs-kotlin.md`](../cp-sat/python-vs-kotlin.md) — why the Java→Kotlin path is painful (motivating this library)
