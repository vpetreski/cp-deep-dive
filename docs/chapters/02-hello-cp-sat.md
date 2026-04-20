# Chapter 02 — Hello, CP-SAT (Python + raw Kotlin)

> **Phase 2: CP-SAT basics + build cpsat-kt** · Estimated: ~3h · Status: ready-to-start · Last updated: 2026-04-19

## Goal

Build and solve your first CP-SAT model end-to-end in both languages. In Python, experience how pleasant the first-party API is. In Kotlin, experience how *painful* the raw Java API is — on purpose — so the motivation for building `cpsat-kt` in Chapter 3 lands hard.

## Before you start

- **Prerequisites:** Chapter 1 (you know CSP/COP, propagation, global constraints by name).
- **Required reading:** [`knowledge/cp-sat/overview.md §1-3`](../knowledge/cp-sat/overview.md) for what CP-SAT is and core API shape. [`knowledge/cp-sat/python-vs-kotlin.md §1-5`](../knowledge/cp-sat/python-vs-kotlin.md) for the install story and side-by-side API diff table.
- **Environment:**
  - **Python 3.12+** via **uv** — install uv (`curl -LsSf https://astral.sh/uv/install.sh | sh`), verify with `uv --version`.
  - **JDK 25** — `brew install openjdk@25` (macOS) or Adoptium equivalent. Verify with `java -version` showing `25`.
  - **Kotlin 2.1+ and Gradle 9** — no install needed beyond the Gradle wrapper; the chapter scaffold ships with `./gradlew`.
  - No MiniZinc yet (that's Chapter 7).

## Concepts introduced this chapter

- **`CpModel`** — the builder. You pile variables and constraints into it; it's a thin typed wrapper over a protobuf.
- **`CpSolver`** — the thing that takes a model and searches. Holds parameters, returns a status and solution.
- **`IntVar`** — an integer decision variable bounded by `[lb, ub]`. In CP-SAT, *all* decision variables are integer or boolean; reals must be scaled to integers by the modeler.
- **`BoolVar`** — a 0/1 integer variable with the extra trick `.not()` (for half-reification).
- **Status codes** — `OPTIMAL`, `FEASIBLE`, `INFEASIBLE`, `MODEL_INVALID`, `UNKNOWN`. The semantics of each (§4.3).
- **`solver.value(x)`** — pull a variable's assigned value after `solve()`.
- **`Loader.loadNativeLibraries()`** — a JVM-only incantation that you *must* call before touching anything in `com.google.ortools.sat.*`, or you get `UnsatisfiedLinkError` at runtime (not compile time).
- **Linear expressions the hard way** — why `model.add(3*x + 2*y == 12)` reads like math in Python but becomes `LinearExpr.newBuilder().addTerm(x, 3L).addTerm(y, 2L).build()` in raw Kotlin.

## 1. Intuition — the same equation in two skins

We're going to solve a textbook-tiny system:

```
    3x + 2y = 12
    x + y   ≤ 5
    x, y    ∈ [0, 10]        (integer)
Maximize    x + y
```

Two variables, two constraints, an objective — the smallest model that's honestly a COP. Think about it for a moment: `x + y ≤ 5` caps the objective at 5; `3x + 2y = 12` is the tight constraint. What maximizes `x + y` while satisfying both?

Pen-and-paper: trial values. `(x, y) = (4, 0)` gives `3·4 + 2·0 = 12` ✓, `4 + 0 = 4 ≤ 5` ✓, objective = 4. `(x, y) = (2, 3)` gives `3·2 + 2·3 = 12` ✓, `2 + 3 = 5` ✓, objective = 5. `(x, y) = (0, 6)` gives `3·0 + 2·6 = 12` ✓ but `0 + 6 = 6 > 5` ✗. So `(2, 3)` looks optimal at objective 5.

The solver will find the same thing — much faster — without your hand-tracing. Here's the punchline: **exact same logic**, two languages, same protobuf crossing into the same C++ solver core. But the *writing experience* will be night and day.

## 2. Formal definition — `CpModel` as builder, `CpSolver` as oracle

CP-SAT uses a **model-builder** pattern. You never mutate a running solver. You build a `CpModel`, call `solver.solve(model)`, and read back the results. Under the hood the `CpModel` is a thin typed wrapper over a protobuf (`CpModelProto`) — this is why the model is freely cloneable, serializable, and inspectable.

| Concept | Python | Java (called from Kotlin) |
|---|---|---|
| Problem container | `cp_model.CpModel()` | `CpModel()` |
| Solver | `cp_model.CpSolver()` | `CpSolver()` |
| Integer var in `[0, 10]` | `model.new_int_var(0, 10, "x")` | `model.newIntVar(0L, 10L, "x")` |
| Boolean var | `model.new_bool_var("b")` | `model.newBoolVar("b")` |
| Linear constraint | `model.add(3*x + 2*y == 12)` | `model.addEquality(LinearExpr.weightedSum(arr, coeffs), 12L)` |
| Maximize | `model.maximize(x + y)` | `model.maximize(LinearExpr.sum(arr))` |
| Solve | `status = solver.solve(model)` | `val status = solver.solve(model)` |
| Get value | `solver.value(x)` | `solver.value(x)` |

In Python, `IntVar` overloads `__add__`, `__mul__`, `__le__`, `__eq__`, etc. — arithmetic expressions *are* linear-expression objects, and `model.add(<expr> <op> <rhs>)` figures out which constraint kind to post. In raw Java (therefore raw Kotlin), you must explicitly build `LinearExpr` objects: `LinearExpr.newBuilder().addTerm(x, 3L).addTerm(y, 2L).build()`. No operator overloading, no `==` on `IntVar`, and — crucially for beginners — no compile-time help: if you try to write `3 * x + 2 * y` in Kotlin with raw OR-Tools classes, the compiler rejects it.

### Status codes

After `solve()`, the returned status tells you one of five things:

| Status | Meaning |
|---|---|
| `OPTIMAL` | For a COP: proved this objective value is best. For a CSP: solver didn't even have an objective and found something feasible — which, semantically, is "optimal" (every feasible solution is equally good). |
| `FEASIBLE` | A feasible solution exists and is in hand, but the search didn't prove optimality (usually hit a time limit). |
| `INFEASIBLE` | The solver *proved* no solution exists. Model is unsatisfiable. |
| `MODEL_INVALID` | You wrote a broken model. Call `model.validate()` for the reason. Common: out-of-range constants, a `LinearExpr` with overflow risk. |
| `UNKNOWN` | Nothing proved, no solution in hand, time ran out. Rare on small models — if you see this on the Chapter 2 model, something is very wrong. |

## 3. Worked example by hand

For our toy:

- `x + y ≤ 5` bounds the objective from above by 5.
- `3x + 2y = 12` is a line in the integer lattice. Integer points on it with `x, y ∈ [0, 10]`: `(0, 6), (2, 3), (4, 0)` (and, looking further, `(-2, 9)` is out of range). Which of these satisfy `x + y ≤ 5`?
  - `(0, 6)`: `0 + 6 = 6 > 5` → infeasible
  - `(2, 3)`: `2 + 3 = 5 ≤ 5` → feasible, objective = 5
  - `(4, 0)`: `4 + 0 = 4 ≤ 5` → feasible, objective = 4

Optimal: `(x, y) = (2, 3)` with objective `5`. The solver will return exactly this; any discrepancy is a model bug.

## 4. Python implementation

Scaffold at `apps/py-cp-sat/ch02-hello/`. Structure:

```
apps/py-cp-sat/ch02-hello/
├── pyproject.toml
├── .python-version          (3.12)
├── uv.lock
├── src/
│   └── main.py
├── tests/
│   └── test_main.py
└── README.md
```

`pyproject.toml`:

```toml
[project]
name = "ch02-hello"
version = "0.1.0"
requires-python = ">=3.12"
dependencies = [
    "ortools>=9.15,<10",
]

[dependency-groups]
dev = ["pytest>=8"]

[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"
```

`src/main.py`:

```python
"""Chapter 2 — Hello, CP-SAT (Python).

Solves:
    3x + 2y == 12
    x + y   <= 5
    x, y    in [0, 10] (integer)
    Maximize x + y
"""
from ortools.sat.python import cp_model


def solve() -> tuple[str, int | None, tuple[int, int] | None]:
    """Build + solve the model. Returns (status_name, objective, (x, y))."""
    model = cp_model.CpModel()

    # 1. Variables: integers in [0, 10].
    x = model.new_int_var(0, 10, "x")
    y = model.new_int_var(0, 10, "y")

    # 2. Constraints: the Python API uses operator overloading — this reads like algebra.
    model.add(3 * x + 2 * y == 12)
    model.add(x + y <= 5)

    # 3. Objective: maximize x + y.
    model.maximize(x + y)

    # 4. Solve.
    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = 5.0
    status = solver.solve(model)

    if status == cp_model.OPTIMAL:
        return "OPTIMAL", int(solver.objective_value), (solver.value(x), solver.value(y))
    if status == cp_model.FEASIBLE:
        return "FEASIBLE", int(solver.objective_value), (solver.value(x), solver.value(y))
    return solver.status_name(status), None, None


def main() -> None:
    status, obj, xy = solve()
    print(f"status   = {status}")
    if xy is not None:
        x, y = xy
        print(f"objective = {obj}")
        print(f"x, y     = {x}, {y}")


if __name__ == "__main__":
    main()
```

`tests/test_main.py`:

```python
from src.main import solve


def test_optimal() -> None:
    status, obj, xy = solve()
    assert status == "OPTIMAL"
    assert obj == 5
    assert xy == (2, 3)
```

Run it:

```bash
cd apps/py-cp-sat/ch02-hello
uv sync
uv run python -m src.main
# status    = OPTIMAL
# objective = 5
# x, y      = 2, 3

uv run pytest -q
```

**Walkthrough of each block:**

1. `model.new_int_var(0, 10, "x")` — create an integer decision variable with domain `[0, 10]` and debug name `"x"`. Names are cosmetic (shown in logs and exports), but always name your variables: the presolve log quickly becomes unreadable otherwise.
2. `model.add(3*x + 2*y == 12)` — the magic is in `IntVar.__mul__`, `__add__`, `__eq__` returning a linear constraint object that `model.add` recognizes. This is Python operator overloading doing heavy lifting.
3. `model.maximize(x + y)` — sets the objective. CP-SAT supports exactly one objective; calling `minimize`/`maximize` again replaces it.
4. `solver.parameters.max_time_in_seconds = 5.0` — always set a time limit, even on toy models. Production models must.
5. `solver.solve(model)` — returns a status code. Branch on it. Never blindly call `solver.value(x)` before checking the status — you'll read garbage.

## 5. Kotlin implementation (raw Java API — on purpose, the painful way)

Scaffold at `apps/kt-cp-sat/ch02-hello/`. Structure:

```
apps/kt-cp-sat/ch02-hello/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/...
├── gradlew, gradlew.bat
├── src/
│   ├── main/kotlin/Main.kt
│   └── test/kotlin/MainTest.kt
└── README.md
```

`gradle/libs.versions.toml`:

```toml
[versions]
kotlin = "2.1.20"
ortools = "9.15.6755"
kotest = "5.9.1"

[libraries]
ortools-java = { module = "com.google.ortools:ortools-java", version.ref = "ortools" }
kotest-runner = { module = "io.kotest:kotest-runner-junit5", version.ref = "kotest" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

`build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

repositories { mavenCentral() }

dependencies {
    implementation(libs.ortools.java)
    testImplementation(libs.kotest.runner)
}

kotlin { jvmToolchain(25) }

application {
    mainClass = "MainKt"
}

tasks.test { useJUnitPlatform() }
```

`src/main/kotlin/Main.kt` — **raw Java API through Kotlin, deliberately unsugared**:

```kotlin
import com.google.ortools.Loader
import com.google.ortools.sat.CpModel
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverStatus
import com.google.ortools.sat.IntVar
import com.google.ortools.sat.LinearExpr

fun main() {
    // 1. Boot the native library. WITHOUT THIS CALL YOU GET UnsatisfiedLinkError AT RUNTIME.
    //    Not at compile time — runtime. Worst kind of error for a beginner. Do it first, always.
    Loader.loadNativeLibraries()

    val model = CpModel()

    // 2. Variables. Note the mandatory Long bounds (0L, 10L) — Int literals here cause
    //    overload ambiguity and a cryptic "cannot find method" error at compile time.
    val x: IntVar = model.newIntVar(0L, 10L, "x")
    val y: IntVar = model.newIntVar(0L, 10L, "y")

    // 3. Linear expressions the painful way. There is NO operator overloading on IntVar;
    //    3 * x does not compile. You must build a LinearExpr explicitly.
    val threeXplusTwoY: LinearExpr = LinearExpr.newBuilder()
        .addTerm(x, 3L)
        .addTerm(y, 2L)
        .build()
    model.addEquality(threeXplusTwoY, 12L)

    val xPlusY: LinearExpr = LinearExpr.newBuilder()
        .add(x)
        .add(y)
        .build()
    model.addLessOrEqual(xPlusY, 5L)

    // 4. Objective. Again, a LinearExpr — we rebuild it because each call consumes the expression.
    model.maximize(
        LinearExpr.newBuilder().add(x).add(y).build()
    )

    // 5. Solve.
    val solver = CpSolver()
    solver.parameters.maxTimeInSeconds = 5.0
    val status: CpSolverStatus = solver.solve(model)

    // 6. Status handling — an if/else ladder because there's no sealed result type.
    when (status) {
        CpSolverStatus.OPTIMAL, CpSolverStatus.FEASIBLE -> {
            println("status    = $status")
            println("objective = ${solver.objectiveValue()}")
            println("x, y      = ${solver.value(x)}, ${solver.value(y)}")
        }
        CpSolverStatus.INFEASIBLE -> println("status = INFEASIBLE")
        CpSolverStatus.MODEL_INVALID -> println("status = MODEL_INVALID: ${model.validate()}")
        CpSolverStatus.UNKNOWN -> println("status = UNKNOWN (time up)")
        else -> println("status = $status")
    }
}
```

`src/test/kotlin/MainTest.kt` (minimal Kotest):

```kotlin
import com.google.ortools.Loader
import com.google.ortools.sat.CpModel
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverStatus
import com.google.ortools.sat.LinearExpr
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class MainTest : StringSpec({
    "solves 3x+2y=12, x+y<=5, max x+y → (2,3) obj=5" {
        Loader.loadNativeLibraries()
        val model = CpModel()
        val x = model.newIntVar(0L, 10L, "x")
        val y = model.newIntVar(0L, 10L, "y")
        model.addEquality(LinearExpr.newBuilder().addTerm(x, 3L).addTerm(y, 2L).build(), 12L)
        model.addLessOrEqual(LinearExpr.newBuilder().add(x).add(y).build(), 5L)
        model.maximize(LinearExpr.newBuilder().add(x).add(y).build())

        val solver = CpSolver()
        val status = solver.solve(model)

        status shouldBe CpSolverStatus.OPTIMAL
        solver.value(x) shouldBe 2L
        solver.value(y) shouldBe 3L
        solver.objectiveValue() shouldBe 5.0
    }
})
```

Run:

```bash
cd apps/kt-cp-sat/ch02-hello
./gradlew run
# status    = OPTIMAL
# objective = 5.0
# x, y      = 2, 3

./gradlew test
```

**Now feel the pain.** Look at the Python code: 3 lines of model-building. Look at the Kotlin: 10+ lines, every arithmetic expression an explicit `LinearExpr.newBuilder()` dance, every numeric literal a `Long` (forget the `L` suffix and the compiler emits a "no such method" error that takes 20 minutes to debug the first time), every status check a ceremony because there's no sealed result type. The IDE helps — autocomplete works, Javadoc pops up — but *the code is not idiomatic Kotlin*. It reads like Java transcribed with colons.

This is by design, for this chapter only. Next chapter you build `cpsat-kt` and rewrite this in five lines of pleasant DSL.

### Five Kotlin friction points to document in your own words

As the chapter deliverable (`docs/chapters/02-hello.md` short writeup, not this file), list them. Suggestions:

1. **No operator overloading on `IntVar`.** Every `+`, `*`, `==` between variables requires a `LinearExpr` builder.
2. **Nullable platform types.** `solver.parameters` returns a `SatParameters?` from Kotlin's view — defensible but noisy.
3. **No sealed `SolveResult`.** Forced if/else on `CpSolverStatus` enum; no `when`-is-exhaustive guarantee tied to success/failure data.
4. **`Long`-everywhere for bounds.** Kotlin's number-literal coercion doesn't kick in against Java's overloaded `long`; forget `L` and it doesn't compile with a confusing message.
5. **`Loader.loadNativeLibraries()` is a hidden requirement.** Call it? Fine. Forget it? `UnsatisfiedLinkError` at runtime. Nothing in the type system nudges you.

## 6. MiniZinc implementation (optional — skip unless curious)

You'll write models like this in MiniZinc in Chapter 7. For the curious:

```minizinc
var 0..10: x;
var 0..10: y;
constraint 3*x + 2*y = 12;
constraint x + y <= 5;
solve maximize x + y;
output ["x = \(x), y = \(y), obj = \(x + y)"];
```

The point of this tour: notice that MiniZinc reads like math too, but without Python's install ceremony or Kotlin's API verbosity. We'll meet it properly in Phase 3.

## 7. Comparison & takeaways

| Aspect | Python | Kotlin (raw Java API) |
|---|---|---|
| Lines to model | ~7 | ~15 |
| Linear expressions | `3*x + 2*y == 12` | `LinearExpr.newBuilder().addTerm(...).addTerm(...).build()` + `addEquality` |
| Status handling | `if status == OPTIMAL: ...` | `when (status) { CpSolverStatus.OPTIMAL -> ...; else -> ... }` — no sealed result |
| Setup friction | `uv add ortools` | Gradle + version catalog + `Loader.loadNativeLibraries()` + Long-suffix discipline |
| Runtime | ~10ms | ~10ms (identical solver underneath) |
| Iteration speed | edit + run instantly | edit + Gradle incremental compile (seconds) |
| Compile-time safety | weak; type-stubs help but `x + 1` returns a runtime-typed object | strong; the compiler catches many errors the Python one discovers at runtime |

**Key insight.** *Same solver, same proto, same answer — different developer experience. Python feels like algebra; raw Kotlin feels like filing taxes.* The C++ solver core runs for tens of milliseconds in both cases; 99% of the runtime is the search, not the model-building. The gap is entirely about ergonomics, and ergonomics is what Chapter 3 fixes.

**Secondary insight.** The C++ solver doesn't care which language called it. A `CpModel` serialized in Python and deserialized in Kotlin solves identically. Later (Ch 8, Ch 18) you'll exploit this for prototype-in-one, deploy-in-another workflows.

## 8. Exercises

### Exercise 2.1 — Tighten the constraint

**Problem.** Change `x + y ≤ 5` to `x + y ≤ 4`. Re-solve in both Python and Kotlin. Report status and objective.

**Expected output:** status `OPTIMAL`, objective 4, `(x, y) = (4, 0)`. Confirm both languages agree.

**Acceptance criteria:** both programs print the same assignment; the test suite for each is updated to match.

<details><summary>Hint</summary>

`3·4 + 2·0 = 12` ✓ and `4 + 0 = 4 ≤ 4` ✓. The solver will land here because no integer (x, y) satisfies `3x + 2y = 12` with `x + y = 5` when the cap is tightened.

</details>

### Exercise 2.2 — Make it infeasible on purpose

**Problem.** Add `x + y >= 10` to the model. Re-solve. What status do you get? Then remove the new constraint and instead change the equality to `3x + 2y = 7`. What status now?

**Expected output:** both cases give `INFEASIBLE`. Your program should explicitly print that and *not* try to call `solver.value(x)`.

**Acceptance criteria:** programs in both languages distinguish `INFEASIBLE` from `UNKNOWN` in their printed output. Bonus: in the equality case, call `model.validate()` in Kotlin (returns `String?`) and print it — does it detect the infeasibility or just say "valid model"?

<details><summary>Hint</summary>

`x + y ≤ 5` + `x + y ≥ 10` conflict instantly. `3x + 2y = 7` has no integer solutions with `x, y ≥ 0` because the smallest integer point on that line with `x ≥ 0` is `(x=1, y=2)` giving 7 — wait, that works. Try `3x + 2y = 1` instead (no non-negative integer solutions).

`model.validate()` is for structural errors (bad bounds, missing variables); it does NOT detect logical infeasibility. The solver does.

</details>

### Exercise 2.3 — Enumerate all feasible solutions

**Problem.** Remove the objective. Make it a pure CSP: list every `(x, y)` satisfying `3x + 2y = 12` and `x + y ≤ 5` with `x, y ∈ [0, 10]`.

- Python: `solver.parameters.enumerate_all_solutions = True`, use a `CpSolverSolutionCallback` subclass.
- Kotlin: the same, but via the raw Java `CpSolverSolutionCallback` abstract class.

**Expected output:** two solutions: `(2, 3)` and `(4, 0)`. (Not `(0, 6)` — that violates `x + y ≤ 5`.)

**Acceptance criteria:** both programs print exactly these two solutions, in any order.

<details><summary>Hint</summary>

```python
class Printer(cp_model.CpSolverSolutionCallback):
    def on_solution_callback(self):
        print(self.value(x), self.value(y))
```

In Kotlin, `object : CpSolverSolutionCallback() { override fun onSolutionCallback() { ... } }` and remember that `value(x)` here is a method *on the callback*, not on the solver.

</details>

### Exercise 2.4 — Add integer multiplication

**Problem.** Add a third variable `z ∈ [0, 20]` and constrain `z = x * y`. Keep the existing constraints and objective. Re-solve.

Hint: the Python call is `model.add_multiplication_equality(z, [x, y])`. The Kotlin call is `model.addMultiplicationEquality(z, arrayOf(x, y))`. Both constrain `z` to equal the product of the listed variables.

**Expected output:** status `OPTIMAL`, objective `5`, `(x, y, z) = (2, 3, 6)`.

**Acceptance criteria:** the solver reports optimal in under a second, and `z == x * y` in the returned assignment.

<details><summary>Hint</summary>

`add_multiplication_equality` is *global*: it handles the integer product constraint via a dedicated propagator. Don't try to roll it yourself with a table of allowed `(x, y, z)` tuples — that's Chapter 6 (`Table` constraint) territory. For now, just use the built-in.

</details>

### Exercise 2.5 — Time the model

**Problem.** Bump the domain to `[0, 1_000_000]`. Re-solve and measure wall time in both languages.

**Expected output shape:** print `wall = solver.wall_time` / `solver.wallTime()`. The solver should still finish optimally in well under a second in both languages.

**Acceptance criteria:** times are within 2x of each other (they ought to be within noise — the solver is the same C++ core; only model-build time differs, and that's microseconds).

<details><summary>Hint</summary>

Wide domains don't slow CP-SAT much when the constraints are tight — in our model `3x + 2y = 12` pins down the search nearly instantly through propagation. But they do slow it a *bit*, and they bloat the proto. A well-chosen tight domain is always better, which is the lesson of this exercise.

</details>

Solutions live in `apps/py-cp-sat/ch02-hello/solutions/` and `apps/kt-cp-sat/ch02-hello/solutions/`. Try each exercise cold before peeking.

## 9. Self-check

**Q1.** What does `Loader.loadNativeLibraries()` do, and what happens if you forget it?

<details><summary>Answer</summary>

It extracts the platform-appropriate CP-SAT native library (`libortools.dylib` on macOS, `.so` on Linux, `.dll` on Windows) from the ortools-java jar's classifier dependency and calls `System.load(...)` on it, making the JNI calls available. If you forget it, the first call into any `com.google.ortools.sat.*` class throws `UnsatisfiedLinkError` at runtime — no hint at compile time. In `cpsat-kt`, we'll call it lazily-and-once inside `cpModel { }` so you can't forget.

</details>

**Q2.** What's the difference between `OPTIMAL` and `FEASIBLE`?

<details><summary>Answer</summary>

`OPTIMAL` means the solver has found a solution *and* proved no better one exists — for a COP, the objective value is provably the best; for a CSP it means the solver found something feasible. `FEASIBLE` means the solver found *a* solution but wasn't able to prove optimality (usually because a time limit hit). You can treat both as "I have a usable solution in hand" — but only `OPTIMAL` lets you stop looking; `FEASIBLE` means the `solver.best_objective_bound` is still different from `solver.objective_value` and the solver believes there's a better answer lurking.

</details>

**Q3.** Why is CP-SAT integer-only? How do you model a problem with a real-valued parameter?

<details><summary>Answer</summary>

CP-SAT's hybrid engine (CP propagators + SAT clauses + LP relaxations) is fundamentally built around integer domains and boolean literals. Real numbers don't fit the SAT side at all and would blow up the CP propagators. The workaround is *scaling*: if your real coefficient has three decimals of meaningful precision, multiply everything in the model by 1000 and solve over integers. For the objective, divide back down in reporting. This is what production CP-SAT users do — see the `knowledge/cp-sat/overview.md §10` "scale reals to integers" note.

</details>

**Q4.** Name three Kotlin-specific ergonomic misses in the raw OR-Tools Java API.

<details><summary>Answer</summary>

(Any three of:) no operator overloading on `IntVar`/`LinearExpr`; no sealed result type forcing `when` exhaustiveness; nullable platform types from Java (e.g. `parameters`); mandatory `Long` suffixes on numeric literals; the `Loader.loadNativeLibraries()` runtime-only requirement; the explicit `LinearExpr.newBuilder()` dance for every arithmetic expression; `Array<IntVar>` required in many API slots instead of `List<IntVar>`; no coroutine-friendly solve API (blocking). All of these are exactly what `cpsat-kt` fixes in Chapter 3.

</details>

**Q5.** Your program prints `status = MODEL_INVALID`. What's the single most useful next action?

<details><summary>Answer</summary>

Call `model.validate()` — it returns a non-empty error string describing the structural problem (e.g. "variable x has domain [100, 0] which is empty", "constraint references unknown variable", "LinearExpr coefficient overflow"). This is distinct from infeasibility, which `validate()` does NOT detect.

</details>

## 10. What this unlocks

Feeling the raw-Kotlin pain is the launchpad. Chapter 3 designs and implements `cpsat-kt` — the Kotlin DSL that makes the Kotlin code above collapse to something that reads like the Python, plus a sealed `SolveResult`, plus coroutine-friendly solving.

## 11. Further reading

- Google, [*OR-Tools CP-SAT first example*](https://developers.google.com/optimization/cp/cp_example) — same pattern we built here, on the official site.
- Google, [*OR-Tools install* (Python)](https://developers.google.com/optimization/install/python), [*Java*](https://developers.google.com/optimization/install/java) — authoritative install instructions, including the native-classifier footnotes.
- Krupke, [*CP-SAT Primer Part 1*](https://d-krupke.github.io/cpsat-primer/01_installation.html) — the best community primer on CP-SAT; chapter one covers installation and the hello-world in glorious detail.
- OR-Tools GitHub, [`ortools/sat/samples/`](https://github.com/google/or-tools/tree/stable/ortools/sat/samples) — browse the `*_sat.py` and `*Sat.java` samples (no Kotlin ones; this is why we're building `cpsat-kt`).
- Perron & Didier, [*The CP-SAT-LP Solver*](https://drops.dagstuhl.de/storage/00lipics/lipics-vol280-cp2023/LIPIcs.CP.2023.3/LIPIcs.CP.2023.3.pdf), CP 2023 — the canonical paper on what's *inside* the solver. Skim §1-3 now; come back for §4-6 after Chapter 6.
- Kotlin team, [*Type-safe builders*](https://kotlinlang.org/docs/type-safe-builders.html) — reading this before Chapter 3 is a great warm-up; it's exactly the technique we'll use to build our DSL.
