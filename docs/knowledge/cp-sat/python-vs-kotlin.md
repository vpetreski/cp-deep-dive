# Python vs Kotlin for CP-SAT

A practical side-by-side guide for building OR-Tools CP-SAT models in both languages. Targeted at April 2026 state of the ecosystem.

> **Scope note for this project.** The Java/Kotlin code blocks below show how the raw Java OR-Tools API works from Kotlin — useful as motivation and for understanding the underlying machinery. In `cp-deep-dive` itself, actual Kotlin chapter/app code **always** uses our own idiomatic DSL, **[`cpsat-kt`](../cpsat-kt/overview.md)**, never `com.google.ortools.sat.*` directly (one exception: Chapter 2 shows the raw-Java pain point once to motivate the wrapper). The Python sections are the real API you'll use; the Kotlin sections below are the "what we're wrapping" reference.

## 1. The reality: the solver is C++

CP-SAT's core is a C++ solver. Everything you write in Python, Java, or Kotlin is a **thin model-building layer** that emits a protobuf (`CpModelProto`) which gets handed to the native solver through JNI (JVM) or pybind11 (Python). Once that proto crosses the native boundary, the language you built it in is irrelevant — solving is 100% C++, multi-threaded, and identical whether the caller is a Jupyter notebook or a Kotlin micro-service.

Concretely:

- Building a model with 10,000 variables + 50,000 constraints takes **tens of milliseconds** in either language. The proto transfers in microseconds.
- Laurent Perron (CP-SAT tech lead) has consistently pointed out in his INFORMS/CPAIOR talks that end-to-end runtime differences between language wrappers are in the noise for any non-trivial model. If you are solving for 30 seconds, the 5 ms difference in model-building is irrelevant.
- There is **no separate Kotlin SDK**. Kotlin uses the Java bindings (`com.google.ortools:ortools-java`) via full JVM interop. Idiomatic Kotlin over the Java API is your own layer — extension functions, operator overloading, DSL builders.

Upshot: **pick the language that fits your team, not the solver.** This guide is about developer experience, not performance.

## 2. Install — Python

**Package:** `ortools` on PyPI ([pypi.org/project/ortools](https://pypi.org/project/ortools/))
**Latest as of April 2026:** `9.15.6755` (released January 2026; matches the [v9.15 GitHub release](https://github.com/google/or-tools/releases/tag/v9.15))
**Minimum Python:** `>=3.9` (declared in the wheel metadata)

```bash
# with uv (recommended, see §7)
uv add ortools

# with plain pip
pip install ortools
```

**Apple Silicon:** yes — native `cp3{9,10,11,12,13,14}-macosx_11_0_arm64` wheels are published for every supported Python. No Rosetta, no compilation from source. Same for Linux aarch64 (`manylinux_2_28_aarch64`) and Windows x86_64. Free-threaded builds (`cp313t`, `cp314t`) are published for Linux aarch64 only — on macOS you still need the GIL build.

**Gotchas:**

- **Cold import is ~150–300 ms** on an M-series Mac. The shared library is ~40 MB and pulls in abseil + protobuf symbols. Not an issue for scripts, but noticeable in short CLI tools.
- **`import ortools.sat.python.cp_model`** is the canonical path. There is no top-level `import ortools` convenience alias — a bare `import ortools` succeeds but exports nothing useful.
- **Don't mix Python/protobuf versions.** `ortools` pins `protobuf>=5,<7`. If another dep pins a conflicting version, `pip` will let it through and you'll get cryptic `DescriptorPool` errors at import. `uv` catches this at resolve time.
- **`absl-py`** is a transitive dep. Some CP-SAT code that uses callbacks expects absl logging to be initialized. If logs silently vanish, call `absl.logging.set_verbosity(absl.logging.INFO)` once.

Install docs: https://developers.google.com/optimization/install/python

## 3. Install — Kotlin / Gradle

**Maven coordinates:** `com.google.ortools` group, multiple artifacts.
**Latest on Maven Central as of April 2026:** `9.14.6206` (Java release cycle trails Python by one minor; the v9.15 artifacts are on GitHub but not yet promoted to Maven Central at the time of writing — check [search.maven.org](https://central.sonatype.com/search?q=com.google.ortools) before pinning).

You need **two** artifacts: the Java API jar, plus a native-classifier jar for your platform.

| Artifact | Purpose |
|---|---|
| `com.google.ortools:ortools-java:9.14.6206` | Pure-Java API classes (CpModel, IntVar, etc.) |
| `com.google.ortools:ortools-darwin-aarch64:9.14.6206` | Native `.dylib` for Apple Silicon |
| `com.google.ortools:ortools-darwin-x86-64:9.14.6206` | Native `.dylib` for Intel Mac |
| `com.google.ortools:ortools-linux-x86-64:9.14.6206` | Native `.so` for glibc x86_64 |
| `com.google.ortools:ortools-linux-aarch64:9.14.6206` | Native `.so` for Linux arm64 |
| `com.google.ortools:ortools-win32-x86-64:9.14.6206` | Native `.dll` for Windows |

The `ortools-java` POM declares the native jars as **optional** dependencies with `<classifier>` suffixes, so Gradle won't pull them in automatically. You add the one matching your target runtime. For cross-platform distributions (a CLI you ship to users on any OS), depend on all five.

**Recommended build (`build.gradle.kts`):**

```kotlin
plugins {
    kotlin("jvm") version "2.1.20"          // Kotlin 2.x is the 2026 default; 2.3.20 is latest
    application
}

repositories { mavenCentral() }

val ortoolsVersion = "9.14.6206"
val osName = System.getProperty("os.name").lowercase()
val osArch = System.getProperty("os.arch").lowercase()
val nativeClassifier = when {
    "mac" in osName && "aarch64" in osArch -> "ortools-darwin-aarch64"
    "mac" in osName                        -> "ortools-darwin-x86-64"
    "linux" in osName && "aarch64" in osArch -> "ortools-linux-aarch64"
    "linux" in osName                        -> "ortools-linux-x86-64"
    "windows" in osName                      -> "ortools-win32-x86-64"
    else -> error("Unsupported platform: $osName/$osArch")
}

dependencies {
    implementation("com.google.ortools:ortools-java:$ortoolsVersion")
    implementation("com.google.ortools:$nativeClassifier:$ortoolsVersion")
}

kotlin { jvmToolchain(21) }                 // JDK 17 minimum; 21 is the sweet spot in 2026
```

**Gradle version:** 8.10+ is fine; 9.x (latest `9.4.1`, March 2026) is recommended for Kotlin 2.x support. Use the Gradle wrapper (`./gradlew`), not a system install.

**Why the native classifier:** the Java API classes are JVM bytecode, but `CpModel.solve()` ultimately calls JNI into `libortools.dylib`/`.so`/`.dll`. The loader extracts that native library from the classpath jar and `System.load`s it. **Without the classifier jar on the classpath, you get `UnsatisfiedLinkError` at runtime** — not at compile time. This is the single biggest onboarding gotcha (see §11).

Install docs: https://developers.google.com/optimization/install/java

## 4. Hello world — N-Queens (N=8)

Both examples produce the 92 solutions of the 8-queens problem. Source of the stable samples:

- Python: https://github.com/google/or-tools/blob/stable/ortools/sat/samples/nqueens_sat.py
- Java: https://github.com/google/or-tools/blob/stable/ortools/sat/samples/NQueensSat.java

### Python (current idiomatic, v9.15)

```python
from ortools.sat.python import cp_model

def solve_n_queens(n: int = 8) -> None:
    model = cp_model.CpModel()

    # one int var per column; value = the row the queen occupies
    queens = [model.new_int_var(0, n - 1, f"q{i}") for i in range(n)]

    # rows are distinct, and both diagonals are distinct
    model.add_all_different(queens)
    model.add_all_different(queens[i] + i for i in range(n))
    model.add_all_different(queens[i] - i for i in range(n))

    solver = cp_model.CpSolver()
    solver.parameters.enumerate_all_solutions = True

    class Printer(cp_model.CpSolverSolutionCallback):
        def __init__(self) -> None:
            super().__init__()
            self.count = 0
        def on_solution_callback(self) -> None:
            self.count += 1

    printer = Printer()
    solver.solve(model, printer)
    print(f"{printer.count} solutions in {solver.wall_time:.3f}s")

if __name__ == "__main__":
    solve_n_queens()
```

### Kotlin (raw Java API, no DSL)

```kotlin
import com.google.ortools.Loader
import com.google.ortools.sat.CpModel
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverSolutionCallback
import com.google.ortools.sat.IntVar
import com.google.ortools.sat.LinearExpr

fun solveNQueens(n: Int = 8) {
    Loader.loadNativeLibraries()                       // must be called once before any CP-SAT call
    val model = CpModel()

    val queens: Array<IntVar> = Array(n) { i -> model.newIntVar(0, (n - 1).toLong(), "q$i") }

    model.addAllDifferent(queens)
    model.addAllDifferent(Array(n) { i -> LinearExpr.newBuilder().add(queens[i]).add(i.toLong()).build() })
    model.addAllDifferent(Array(n) { i -> LinearExpr.newBuilder().add(queens[i]).add((-i).toLong()).build() })

    val solver = CpSolver()
    solver.parameters.enumerateAllSolutions = true

    var count = 0
    val printer = object : CpSolverSolutionCallback() {
        override fun onSolutionCallback() { count++ }
    }

    solver.solve(model, printer)
    println("$count solutions in ${"%.3f".format(solver.wallTime())}s")
}

fun main() = solveNQueens()
```

The Python version is about 30% shorter and reads like algebra because generators flow directly into `add_all_different`. In Kotlin you pay for the lack of operator overloading on `IntVar` — diagonal offsets need the explicit `LinearExpr.newBuilder()` dance. A thin DSL (§6) erases most of that.

## 5. API diff table — Python ↔ Java/Kotlin

The Python wrapper is snake_case and heavily overloaded; the Java wrapper is camelCase and needs `LinearExpr` scaffolding for arithmetic. These are the 30 most common calls.

| Purpose | Python | Java / Kotlin |
|---|---|---|
| Create a model | `cp_model.CpModel()` | `CpModel()` |
| Boolean var | `model.new_bool_var("x")` | `model.newBoolVar("x")` |
| Int var | `model.new_int_var(0, 10, "x")` | `model.newIntVar(0, 10, "x")` |
| Int var from domain | `model.new_int_var_from_domain(d, "x")` | `model.newIntVarFromDomain(d, "x")` |
| Constant | `model.new_constant(5)` | `model.newConstant(5)` |
| Linear constraint (== ≤ ≥) | `model.add(x + y <= 10)` | `model.addLinearConstraint(LinearExpr.sum(arrayOf(x, y)), 0, 10)` |
| AllDifferent | `model.add_all_different(xs)` | `model.addAllDifferent(xs)` |
| Element | `model.add_element(i, xs, target)` | `model.addElement(i, xs, target)` |
| Circuit / TSP | `model.add_circuit(arcs)` | `model.addCircuit(arcs)` |
| Inverse | `model.add_inverse(f, g)` | `model.addInverse(f, g)` |
| MaxEquality | `model.add_max_equality(t, xs)` | `model.addMaxEquality(t, xs)` |
| MinEquality | `model.add_min_equality(t, xs)` | `model.addMinEquality(t, xs)` |
| Abs | `model.add_abs_equality(t, x)` | `model.addAbsEquality(t, x)` |
| Division | `model.add_division_equality(t, a, b)` | `model.addDivisionEquality(t, a, b)` |
| Modulo | `model.add_modulo_equality(t, a, b)` | `model.addModuloEquality(t, a, b)` |
| Multiplication | `model.add_multiplication_equality(t, xs)` | `model.addMultiplicationEquality(t, xs)` |
| BoolOr / BoolAnd | `model.add_bool_or(xs)` / `add_bool_and` | `model.addBoolOr(xs)` / `addBoolAnd` |
| Implication | `model.add_implication(a, b)` | `model.addImplication(a, b)` |
| Reified constraint | `.only_enforce_if(lit)` | `.onlyEnforceIf(lit)` |
| Reservoir | `model.add_reservoir_constraint(...)` | `model.addReservoirConstraint(...)` |
| NoOverlap (1D) | `model.add_no_overlap(intervals)` | `model.addNoOverlap(intervals)` |
| NoOverlap 2D | `model.add_no_overlap_2d(xs, ys)` | `model.addNoOverlap2D()` (builder) |
| Cumulative | `model.add_cumulative(intervals, demands, capacity)` | `model.addCumulative(capacity).addDemand(...)` |
| Interval var | `model.new_interval_var(s, size, e, "i")` | `model.newIntervalVar(s, size, e, "i")` |
| Optional interval | `model.new_optional_interval_var(...)` | `model.newOptionalIntervalVar(...)` |
| Minimize | `model.minimize(expr)` | `model.minimize(LinearExpr.newBuilder()...build())` |
| Maximize | `model.maximize(expr)` | `model.maximize(expr)` |
| Create solver | `cp_model.CpSolver()` | `CpSolver()` |
| Solve | `solver.solve(model)` | `solver.solve(model)` |
| Solve with callback | `solver.solve(model, cb)` | `solver.solve(model, cb)` |
| Get solution value | `solver.value(x)` / `solver.boolean_value(b)` | `solver.value(x)` / `solver.booleanValue(b)` |
| Status | `solver.status_name()` | `solver.cpSolverResponse.status` |
| Wall time | `solver.wall_time` | `solver.wallTime()` |
| Parameters (workers) | `solver.parameters.num_search_workers = 8` | `solver.parameters.numSearchWorkers = 8` |
| Set time limit | `solver.parameters.max_time_in_seconds = 10.0` | `solver.parameters.maxTimeInSeconds = 10.0` |
| Enumerate all | `solver.parameters.enumerate_all_solutions = True` | `solver.parameters.enumerateAllSolutions = true` |

**Naming rule:** almost always a mechanical `snake_case` ↔ `camelCase` swap, with `model.add(<expr>)` in Python expanding to either `addLinearConstraint` or `addEquality`/`addGreaterOrEqual` in Java depending on the operator. Python's `__add__`/`__mul__`/`__le__` overloading on `IntVar` is what makes it so much terser.

## 6. Kotlin-specific ergonomics: DSL, operators, extension functions

The Java API is functional but verbose from Kotlin. Three concrete improvements give you ~80% of Python's terseness:

### 6.1 Operator overloading on `LinearExpr`

```kotlin
// file: CpSatOps.kt
import com.google.ortools.sat.IntVar
import com.google.ortools.sat.LinearExpr

operator fun IntVar.plus(other: IntVar): LinearExpr =
    LinearExpr.newBuilder().add(this).add(other).build()

operator fun IntVar.plus(k: Int): LinearExpr =
    LinearExpr.newBuilder().add(this).add(k.toLong()).build()

operator fun IntVar.minus(k: Int): LinearExpr = this + (-k)

operator fun IntVar.times(coeff: Int): LinearExpr =
    LinearExpr.newBuilder().addTerm(this, coeff.toLong()).build()

operator fun LinearExpr.plus(k: Int): LinearExpr =
    LinearExpr.newBuilder().add(this).add(k.toLong()).build()
```

### 6.2 `infix` for comparison constraints

```kotlin
import com.google.ortools.sat.CpModel
import com.google.ortools.sat.Constraint

infix fun LinearExpr.leq(k: Int): (CpModel) -> Constraint = { it.addLessOrEqual(this, k.toLong()) }
infix fun LinearExpr.eq(k: Int):  (CpModel) -> Constraint = { it.addEquality(this, k.toLong()) }
infix fun LinearExpr.geq(k: Int): (CpModel) -> Constraint = { it.addGreaterOrEqual(this, k.toLong()) }
```

### 6.3 DSL builder

```kotlin
class CpSatScope(val model: CpModel) {
    fun intVar(range: IntRange, name: String) =
        model.newIntVar(range.first.toLong(), range.last.toLong(), name)

    fun boolVar(name: String) = model.newBoolVar(name)

    operator fun ((CpModel) -> Constraint).unaryPlus() { this(model) }

    fun allDifferent(vars: Iterable<IntVar>) = model.addAllDifferent(vars.toList().toTypedArray())
}

fun cpModel(block: CpSatScope.() -> Unit): CpModel {
    com.google.ortools.Loader.loadNativeLibraries()
    val m = CpModel()
    CpSatScope(m).block()
    return m
}
```

Which turns the N-queens body into:

```kotlin
val queens = Array(8) { i -> intVar(0..7, "q$i") }
allDifferent(queens.toList())
allDifferent(queens.mapIndexed { i, q -> q + i })
allDifferent(queens.mapIndexed { i, q -> q - i })
```

Very close to the Python original.

**Community wrappers** exist but are small and unmaintained:

- [`holgerbrandl/kortools`](https://github.com/holgerbrandl/kortools) — thin Kotlin API
- [`andrei-filonenko/kpsat`](https://github.com/andrei-filonenko/kpsat) — DSL + soft constraints
- [`bartlomiejkrawczyk/optimization-kotlin-dsl`](https://github.com/bartlomiejkrawczyk/optimization-kotlin-dsl) — LP/MIP, not CP-SAT
- [`pintowar/opta-router`](https://github.com/pintowar/opta-router) — production CVRP app mixing Kotlin + OR-Tools + Timefold

**Recommendation for a teaching repo:** write thin extension functions yourself (two files, ~80 lines). Don't depend on community libs — none has critical mass and they lag OR-Tools releases. Keep a `Ops.kt` with operators and an optional `Dsl.kt` builder. Show the raw Java API first, then the DSL version, so students see what the sugar is hiding.

Using the raw Java API from Kotlin is *not painful* — autocomplete works, nullability annotations are honored, and the only real friction is arithmetic on `IntVar`. If you're writing a few toy models for learning, you can stop after `LinearExpr.newBuilder().add(x).add(y).build()` and still be productive.

## 7. Build tooling — 2026 choices

### Python: use `uv`

In 2026, `uv` (Astral) has won. PyPA itself endorses it for new projects. Compared to alternatives:

| Tool | Verdict |
|---|---|
| `uv` | **Use this.** 10–100× faster than pip, native lockfile, handles Python versions, drop-in for pip/pipx/venv/pyenv, monorepo-friendly workspace mode. |
| `poetry` | Fine but slow; `pyproject.toml`-native. Use if you're already on it. |
| `hatch` | Solid for library authors; `hatchling` is the default build backend many projects pair with `uv`. |
| `pdm` | Niche. |
| `venv` + `pip` + `requirements.txt` | Works, but you're leaving speed and reproducibility on the table. |
| `conda` | Avoid unless you need non-PyPI scientific deps — CP-SAT doesn't. |

**Teaching repo setup:**

```bash
uv init cp-sat-python
cd cp-sat-python
uv add ortools pytest
uv run python examples/nqueens.py
```

Lockfile is `uv.lock`; `.python-version` pins the interpreter. `pyproject.toml` stays minimal.

### Kotlin: Gradle with the Kotlin DSL

Maven is still common in legacy JVM shops, but for a new teaching repo use **Gradle 9.x with `build.gradle.kts`**. Reasons:

- Type-safe build scripts — IntelliJ autocompletes tasks and config.
- Kotlin 2.x and Gradle 9 have coordinated releases; the Kotlin Gradle plugin K2 compiler is stable.
- Version catalogs (`gradle/libs.versions.toml`) make dependency bumps trivial.
- Multi-module projects (`examples/`, `benchmarks/`, `shared/`) are first-class.

Keep `settings.gradle.kts` minimal, put OR-Tools coordinates in `libs.versions.toml`:

```toml
[versions]
ortools = "9.14.6206"
kotlin  = "2.1.20"

[libraries]
ortools-java          = { module = "com.google.ortools:ortools-java", version.ref = "ortools" }
ortools-darwin-arm64  = { module = "com.google.ortools:ortools-darwin-aarch64", version.ref = "ortools" }
ortools-linux-x86-64  = { module = "com.google.ortools:ortools-linux-x86-64", version.ref = "ortools" }
```

## 8. Testing

| Language | Pick | Why |
|---|---|---|
| Python | **`pytest`** | Parametrization, fixtures, community tooling. Don't bother with `unittest`. |
| Kotlin | **Kotest** for new code; **JUnit 5** if you need enterprise tooling | Kotest's `shouldBe`/`shouldHaveSize`/property-testing is more Kotlin-native and less ceremonial. JUnit 5 + AssertJ is fine and plays nicer with mixed Java/Kotlin modules. |

A CP-SAT test typically looks like "build model, solve, assert feasible + assert objective bounds". Both frameworks handle this trivially. One CP-SAT-specific tip: fix `solver.parameters.random_seed` (Python) / `setRandomSeed` (Kotlin) so tie-breaking is deterministic across runs.

## 9. Developer experience comparison

| Dimension | Python | Kotlin |
|---|---|---|
| IDE | PyCharm (commercial) or VS Code + Pylance | IntelliJ IDEA Community is free and the canonical Kotlin IDE |
| Type safety | Duck-typed; `cp_model.pyi` stubs give decent inference but `model.add(x + y <= 10)` returns `Constraint \| NotImplemented`-ish — runtime errors on bad expressions are common | Static. `IntVar`, `LinearExpr`, `Constraint` are real types. The compiler catches misuse. |
| Completion quality on CP-SAT | Good in PyCharm (type-stubs bundled); mediocre in VS Code unless Pylance is strict-mode | Excellent. IntelliJ indexes the jar, shows Javadoc (wrapper docs are thin but present), highlights deprecated methods. |
| Debugging | `pdb`/`ipdb` inline; Jupyter introspection on any `IntVar` | IntelliJ breakpoints, JVM thread inspector, works with CP-SAT callbacks running on solver threads (mind the JNI boundary — don't block in `onSolutionCallback`). |
| Iteration speed | Edit → Run is instant. REPL/notebook excellent for exploring constraints. | Edit → compile → run is a few seconds on a warm daemon. Gradle's continuous build (`./gradlew run -t`) helps. No good REPL for CP-SAT (the Kotlin JShell-alike doesn't load native libs cleanly). |
| Error messages | `TypeError: unsupported operand for <=` when you try to compare two unbuilt expressions. Usually clear, sometimes cryptic stack-through-SWIG. | Compile-time for type errors; runtime CP-SAT errors come out of JNI with a C++ stack trace header — a bit scary but usually has the real cause at the bottom. |

## 10. When to prefer each in production

### Prefer Python when

- Your inputs come from pandas/polars, numpy, DuckDB, arrow — no serialization cost.
- You're prototyping, experimenting with model formulations, or running in Jupyter.
- The ops team is already running Python services and doesn't want another runtime.
- Onboarding data scientists / OR researchers who know Python.
- The solver runs longer than a minute — language overhead is invisible anyway.

### Prefer Kotlin when

- The optimizer lives inside an existing JVM service (Spring Boot, Ktor, Dropwizard) and model I/O is already Kotlin data classes.
- You want compile-time guarantees: no more `int vs float vs IntVar` surprises.
- You're on a JVM team that resists adding a second runtime (Python means another Docker layer, another CVE surface, another build pipeline).
- You need to share model code with an Android client or a Kotlin Multiplatform server — though note: CP-SAT itself cannot run on Android (no native bindings for ARM Android), so this is about model-building code only, with the solver behind an RPC.
- You want Gradle's multi-module / version catalog ergonomics for a large codebase of models.

**Mixed teams:** common pattern is Python for research/prototyping, Kotlin (or Java) for the production service once the model is stable. Since the model is a proto, you can literally `model.ExportToFile("model.pbtxt")` from Python and `CpModelProto.parseFrom(...)` in Kotlin.

## 11. Kotlin-specific gotchas

### 11.1 `Loader.loadNativeLibraries()` must run first — and exactly once

Calling *any* CP-SAT class before `Loader.loadNativeLibraries()` throws `UnsatisfiedLinkError`. Safe pattern:

```kotlin
object CpSat {
    private val loaded = lazy { com.google.ortools.Loader.loadNativeLibraries() }
    fun init() { loaded.value }
}
```

Call `CpSat.init()` once at program start. Kotlin `object` + `lazy` makes it thread-safe.

### 11.2 Wrong native classifier = runtime crash, not compile error

If you deploy a jar built on Apple Silicon to a Linux x86_64 container and only bundled `ortools-darwin-aarch64`, the app starts fine and crashes on first `Loader.loadNativeLibraries()` with "no native library on path". For multi-platform distribution:

```kotlin
dependencies {
    implementation("com.google.ortools:ortools-java:$ortoolsVersion")
    runtimeOnly("com.google.ortools:ortools-darwin-aarch64:$ortoolsVersion")
    runtimeOnly("com.google.ortools:ortools-darwin-x86-64:$ortoolsVersion")
    runtimeOnly("com.google.ortools:ortools-linux-x86-64:$ortoolsVersion")
    runtimeOnly("com.google.ortools:ortools-linux-aarch64:$ortoolsVersion")
    runtimeOnly("com.google.ortools:ortools-win32-x86-64:$ortoolsVersion")
}
```

Each native jar is ~30–60 MB, so an all-platforms fat jar is ~200 MB. For Docker, depend only on the target-image classifier.

### 11.3 IntelliJ "Cannot resolve symbol CpModel"

If IntelliJ can't find `com.google.ortools.sat.CpModel` after a fresh import, it's usually because:

1. The Gradle sync failed silently (look for the red banner). Re-run sync.
2. The IDE JDK and Gradle JDK disagree. Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JVM — set to the same JDK as the Kotlin toolchain.
3. The native classifier dependency fetched but the ortools-java jar didn't. Check `~/.gradle/caches/modules-2/files-2.1/com.google.ortools/`.

### 11.4 `LinearExpr` vs `IntVar` is not automatic

`model.addEquality(x, 5)` works (overload takes `IntVar`). `model.addEquality(x + 1, 5)` does not compile — `+` isn't overloaded. Until you add your own extension operators (§6), you write `model.addEquality(LinearExpr.newBuilder().add(x).add(1).build(), 5L)`. **Note the `5L`** — `Long`, not `Int`, for all bounds. Forgetting the `L` gives a head-scratching "cannot find method" error because Kotlin won't widen to match the Java `long` overload in the presence of `int` overloads.

### 11.5 Callbacks run on solver worker threads

`CpSolverSolutionCallback.onSolutionCallback()` fires from a solver thread (one of `num_search_workers`). If you write to shared state, use `AtomicInteger`/`ConcurrentHashMap`. Don't `println` under load — stdout contention serializes solver threads. Buffer and print after `solve()` returns.

### 11.6 Protobuf version conflicts

`ortools-java` depends on `protobuf-java` at a specific minor. If your app pulls in gRPC or another library with a different `protobuf-java`, Gradle picks the highest — which may be incompatible with the OR-Tools native side. Symptom: `NoSuchMethodError` in `CpModelProto$Builder`. Fix: pin protobuf to the version in `ortools-java`'s POM, or isolate via `shadowJar` relocation.

---

## Summary for the repo

- **Versions to target:** OR-Tools `9.15.6755` (Python, Jan 2026), `9.14.6206` (Java/Kotlin on Maven Central; upgrade to 9.15 once promoted). Kotlin `2.1.20`+. Gradle `9.x`. Python `3.12` or `3.13`.
- **Build tooling:** `uv` for Python, Gradle Kotlin DSL for Kotlin. Both get you to `hello world` in under a minute on a fresh laptop.
- **Same model, two styles:** Python reads like math; Kotlin reads like engineering. Both compile to the same proto, both hit the same solver, both finish at the same time.
- **For teaching:** write the raw Java API version in Kotlin first, then show the 80-line DSL that closes the gap. Students should understand what the sugar does before depending on it.

**References (inline):**

- OR-Tools install docs: https://developers.google.com/optimization/install
- OR-Tools latest release: https://github.com/google/or-tools/releases/tag/v9.15
- Python package: https://pypi.org/project/ortools/
- Java artifacts: https://central.sonatype.com/search?q=com.google.ortools
- Python samples: https://github.com/google/or-tools/tree/stable/ortools/sat/samples (filter for `*.py`)
- Java samples: https://github.com/google/or-tools/tree/stable/ortools/sat/samples (filter for `*.java`)
- Community Kotlin wrappers: [`kortools`](https://github.com/holgerbrandl/kortools), [`kpsat`](https://github.com/andrei-filonenko/kpsat), [`opta-router`](https://github.com/pintowar/opta-router)
