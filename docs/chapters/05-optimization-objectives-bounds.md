# Chapter 05 — Optimization, objectives, bounds

> **Phase 2: CP-SAT basics + build cpsat-kt** · Estimated: ~3h · Status: ready-to-start · Last updated: 2026-04-19

## Goal

Move from feasibility (CSP) to optimization (COP). By the end you can build an objective, read the gap between an incumbent and the solver's best bound, enumerate intermediate solutions, decide when to stop early, and extend `cpsat-kt` with `solveFlow` for streaming incumbents as a Kotlin `Flow<Solution>`.

## Before you start

- **Prerequisites:** Chapters 1-4. You know `CpModel`, `CpSolver`, `AllDifferent`, reification.
- **Required reading:**
  - [`knowledge/cp-sat/overview.md §5-6`](../knowledge/cp-sat/overview.md) — objectives, solve(), status, callbacks, parameters.
  - [`knowledge/cp-sat/overview.md §9`](../knowledge/cp-sat/overview.md) — search & parameter tuning order.
  - Knapsack background: Wikipedia, [*Knapsack problem*](https://en.wikipedia.org/wiki/Knapsack_problem) — 5-minute read.
  - Krupke, [*CP-SAT Primer Part 4: Parameters*](https://d-krupke.github.io/cpsat-primer/04_parameters.html) — especially the "understanding the log" section.
- **Environment:** unchanged; we extend `cpsat-kt`.

## Concepts introduced this chapter

- **Objective** — a linear expression given to `model.minimize(...)` or `model.maximize(...)`. Exactly one per model.
- **Objective value** (incumbent) — the value of the objective in the best feasible solution found *so far*.
- **Best objective bound** — the solver's proven bound on the true optimum (from LP relaxation, cuts, branch-and-bound). For minimize: bound ≤ optimum ≤ incumbent. For maximize: incumbent ≤ optimum ≤ bound.
- **Optimality gap** — a measure of the remaining uncertainty, typically `|incumbent - bound| / |incumbent|`. When the solver proves gap = 0 → `OPTIMAL`.
- **`SolutionCallback`** (Python) / **`solveFlow`** (our cpsat-kt addition) — a way to observe every incumbent as the solver finds it, rather than waiting for the final result.
- **Enumerate all solutions** — `enumerate_all_solutions = True`, which only makes sense for a pure CSP (no objective); turns the solver into an all-solutions finder.
- **Time limits, gap limits, worker counts** — the three production-critical knobs.
- **Parallelism and non-monotonic logs** — why the log can *look* like the bound jumps around under `num_search_workers > 1`.

## 1. Intuition — the knapsack robber

You're a (very polite) robber staring at a shelf of items in a jewelry shop. Each item has a weight `w[i]` and a value `v[i]`. Your bag holds at most `W` kilograms. You want to walk out with the *most valuable* subset that fits.

That's **0-1 knapsack**. Decision variables `x[i] ∈ {0, 1}` — take item `i` or don't. Constraint: `Σ w[i] · x[i] ≤ W`. Objective: `max Σ v[i] · x[i]`.

If an item can be taken multiple times (you're robbing a wholesale warehouse with identical-weight bracelets), that's **bounded knapsack**: `x[i] ∈ {0, 1, …, c[i]}` and the rest is the same.

Now watch the solver work:

- It finds a quick feasible solution — say, value = 700.
- It keeps searching, finds value = 810.
- The LP relaxation says "the true max can be at most 900" (that's the bound).
- Current **gap** = (900 - 810) / 810 ≈ 11%.
- The solver keeps pushing. Finds 840. Bound tightens to 870. Gap drops to 3.5%.
- Eventually bound meets incumbent at 850. Solver proves optimal. Done.

This dance — incumbent climbing, bound falling, gap shrinking — is what optimization looks like. Understanding it lets you stop early with a provably-near-optimal answer when you can't afford to prove optimality.

## 2. Formal definition — COP, bound, and gap

A **COP** is `⟨X, D, C, f⟩`. Given variables `X`, domains `D`, constraints `C`, and an objective `f`, find a solution `s* ∈ feasible(X, D, C)` such that `f(s*) ≤ f(s)` for every other `s ∈ feasible(X, D, C)` (for minimization; flip for maximization).

### Incumbent and bound

At any point during solving:

- **Incumbent value** `I` = objective value of the best solution found so far. For minimization: `I ≥ f(s*)`. For maximization: `I ≤ f(s*)`.
- **Best bound** `B` = proven bound on `f(s*)`. For minimization, `B ≤ f(s*)`: "no solution can be better than `B`." For maximization, `B ≥ f(s*)`.

**At optimality, `I = B = f(s*)`.** That's how the solver "proves optimal" — not by enumerating everything, but by closing the gap between `I` and `B`.

### Gap

Relative optimality gap:

```
gap = |I - B| / max(|I|, ε)     (ε avoids division by zero)
```

If `gap ≤ relative_gap_limit`, solver stops with `OPTIMAL` status. You set this to `0.05` in production to mean "stop when within 5% of the proven bound" — often an hour shorter than waiting for `OPTIMAL` on a gap-0 proof.

### Parameters to set

| Parameter | Role | Typical value |
|---|---|---|
| `max_time_in_seconds` | Wall-clock limit | 30-300s in interactive, 1-24h in batch |
| `num_search_workers` | Parallel portfolio | Physical cores (8-16 on M-series) |
| `relative_gap_limit` | Stop when gap ≤ X | 0.01-0.05 in prod; 0.0 for proof |
| `absolute_gap_limit` | Absolute variant | For small integer objectives |
| `log_search_progress` | Print the log | `true` during dev, `false` in prod |
| `enumerate_all_solutions` | CSP only, no objective | `true` for Chapter 4-style counting |

### Incumbents via callbacks

Python has a built-in `CpSolverSolutionCallback` you subclass. Override `on_solution_callback()`, which fires on every improved incumbent:

```python
class Listener(cp_model.CpSolverSolutionCallback):
    def on_solution_callback(self):
        print(self.objective_value, self.best_objective_bound)
```

For `cpsat-kt`, we'll build a `solveFlow` that emits a `Flow<Solution>` — coroutine-friendly, cancellation-aware, composable with `take`, `debounce`, etc.

## 3. Worked example by hand — 0-1 knapsack, 4 items

```
items    A    B    C    D
weight   2    3    4    5
value    3    4    5    6
Capacity W = 5
```

Enumerate `2^4 = 16` subsets. Compute (weight, value) of each. Strike out those over capacity.

| x_A x_B x_C x_D | weight | value | feasible? |
|---|---|---|---|
| 0000 | 0 | 0 | ✓ |
| 1000 (A) | 2 | 3 | ✓ |
| 0100 (B) | 3 | 4 | ✓ |
| 0010 (C) | 4 | 5 | ✓ |
| 0001 (D) | 5 | 6 | ✓ |
| 1100 (AB) | 5 | 7 | ✓ |
| 1010 (AC) | 6 | — | ✗ |
| 1001 (AD) | 7 | — | ✗ |
| 0110 (BC) | 7 | — | ✗ |
| 0101 (BD) | 8 | — | ✗ |
| 0011 (CD) | 9 | — | ✗ |
| 1110 (ABC) | 9 | — | ✗ |
| 1101 (ABD) | 10 | — | ✗ |
| 1011 (ACD) | 11 | — | ✗ |
| 0111 (BCD) | 12 | — | ✗ |
| 1111 (ABCD) | 14 | — | ✗ |

Optimal: `(1, 1, 0, 0)` → take A and B, value = 7, weight = 5.

During the solver's search, before it proves this optimal, an incumbent could be 6 (take D alone) with bound 7 (LP relaxation says the fractional optimum is 7). Gap = (7 - 6) / 6 ≈ 17%. Solver continues, finds (A, B) with value 7, bound still 7. Gap = 0. Done.

For the Python/Kotlin code below, we'll use a slightly bigger instance (15 items) so the bound/incumbent dance is visible in the log.

## 4. Python implementation

Scaffold: `apps/py-cp-sat/ch05-optimization/`.

### 4.1 0-1 knapsack

```python
"""Chapter 5 — 0-1 Knapsack (Python)."""
from __future__ import annotations
from dataclasses import dataclass
from ortools.sat.python import cp_model


@dataclass
class Item:
    name: str
    weight: int
    value: int


def solve_knapsack(items: list[Item], capacity: int, time_limit: float = 5.0, gap: float = 0.0):
    model = cp_model.CpModel()
    x = [model.new_bool_var(f"x_{it.name}") for it in items]

    model.add(sum(it.weight * x[i] for i, it in enumerate(items)) <= capacity)
    model.maximize(sum(it.value * x[i] for i, it in enumerate(items)))

    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = time_limit
    solver.parameters.log_search_progress = True
    solver.parameters.num_search_workers = 8
    if gap > 0:
        solver.parameters.relative_gap_limit = gap

    class Listener(cp_model.CpSolverSolutionCallback):
        def __init__(self):
            super().__init__()
            self.trace: list[tuple[float, float, float]] = []  # (wall, obj, bound)
        def on_solution_callback(self):
            self.trace.append((self.wall_time, self.objective_value, self.best_objective_bound))

    listener = Listener()
    status = solver.solve(model, listener)

    chosen = [items[i].name for i in range(len(items)) if solver.boolean_value(x[i])]
    return {
        "status": solver.status_name(status),
        "value": int(solver.objective_value) if status in (cp_model.OPTIMAL, cp_model.FEASIBLE) else None,
        "bound": solver.best_objective_bound,
        "chosen": chosen,
        "trace": listener.trace,
    }


if __name__ == "__main__":
    items = [
        Item("gold-ring", weight=2, value=7),
        Item("silver-cup", weight=3, value=5),
        Item("bronze-bust", weight=4, value=3),
        Item("emerald", weight=5, value=12),
        Item("ruby", weight=5, value=10),
        Item("pearl-necklace", weight=3, value=8),
        Item("brass-clock", weight=6, value=4),
        Item("diamond", weight=7, value=20),
        Item("sapphire", weight=4, value=9),
        Item("opal", weight=3, value=6),
        Item("platinum-bar", weight=8, value=18),
        Item("jade-figurine", weight=5, value=8),
        Item("amber-stone", weight=2, value=4),
        Item("ivory-carving", weight=6, value=11),
        Item("copper-coin", weight=1, value=2),
    ]
    capacity = 20
    r = solve_knapsack(items, capacity, time_limit=5.0)
    print(f"status = {r['status']}  value = {r['value']}  bound = {r['bound']}")
    print(f"chosen = {r['chosen']}")
    print("trace (wall, obj, bound):")
    for (t, obj, b) in r["trace"]:
        print(f"  t={t:.3f}s  obj={obj:.0f}  bound={b:.2f}")
```

### 4.2 Plot convergence (Exercise 5.2 preview)

```python
# apps/py-cp-sat/ch05-optimization/src/plot_convergence.py
import matplotlib.pyplot as plt
from .main import solve_knapsack, items, capacity

r = solve_knapsack(items, capacity, time_limit=5.0)
ts, objs, bounds = zip(*r["trace"])
fig, ax = plt.subplots()
ax.plot(ts, objs, label="incumbent")
ax.plot(ts, bounds, label="bound", linestyle="--")
ax.set_xlabel("wall time (s)")
ax.set_ylabel("objective")
ax.legend()
fig.savefig("convergence.png", dpi=150)
```

### 4.3 Bounded knapsack

Change `new_bool_var` to `new_int_var(0, c[i], ...)` for a per-item cap `c[i]`:

```python
x = [model.new_int_var(0, it.max_count, f"x_{it.name}") for it in items]
```

Mostly the same story; the solve gets slightly harder because the search tree fans out by `c[i]+1` at each item instead of 2.

## 5. Kotlin implementation (cpsat-kt + solveFlow extension)

### 5.1 First extend cpsat-kt with `solveFlow`

Add `libs/cpsat-kt/src/main/kotlin/io/vanja/cpsat/Callbacks.kt`:

```kotlin
package io.vanja.cpsat

import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverSolutionCallback
import com.google.ortools.sat.CpSolverStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Solution(
    val wallTime: Double,
    val objective: Long,
    val bound: Long,
    val values: Assignment,
)

fun CpModel.solveFlow(params: SolverParams.() -> Unit = {}): Flow<Solution> = channelFlow {
    val solver = CpSolver()
    SolverParams().apply(params).applyTo(solver.parameters.toBuilder())
    val chan = Channel<Solution>(capacity = Channel.BUFFERED)

    val cb = object : CpSolverSolutionCallback() {
        override fun onSolutionCallback() {
            // Capture by copy — solver state is shared; we take a snapshot via Assignment
            // pointing at the solver. Since the flow is consumed live, this works as long
            // as the consumer doesn't retain Assignment references across callbacks.
            val snap = Solution(
                wallTime = wallTime(),
                objective = objectiveValue().toLong(),
                bound = bestObjectiveBound().toLong(),
                values = Assignment(solver),
            )
            trySend(snap)
        }
    }

    // Run the (blocking) solve on the IO dispatcher.
    val job = launch(Dispatchers.IO) {
        try {
            val status = solver.solve(this@solveFlow.toJava(), cb)
            // Optional: emit a final Solution on OPTIMAL/FEASIBLE if we haven't already;
            // callers typically consume until close().
            close()
        } catch (e: Throwable) {
            close(e)
        }
    }

    // Propagate cancellation to the solve job if the consumer cancels.
    awaitClose { job.cancel() }
}
```

Notes:

- We use `channelFlow` because `CpSolverSolutionCallback` fires from the solver thread (possibly multiple of them — one per `numSearchWorkers`). `channelFlow` handles thread-safe emit.
- `Assignment(solver)` captures the solver reference; each `Solution.values[...]` call queries the solver's *current* values at the time of the query. Since incumbents are written atomically in CP-SAT, reading right after the callback fires gives the correct values. (For very defensive code you'd snapshot into a map — do that in v0.2 if needed.)
- `awaitClose { job.cancel() }` ensures consumer-side cancellation kills the solve. `CpSolver.solve()` checks a cancellation flag internally; interrupting the thread is honored.

Parity test in `libs/cpsat-kt/src/test/kotlin/io/vanja/cpsat/SolveFlowSpec.kt`:

```kotlin
"solveFlow emits at least one incumbent and terminates" {
    runTest {
        val model = cpModel {
            val x = intVar("x", 0..100)
            val y = intVar("y", 0..100)
            constraint { x + y le 50L }
            maximize { 3L * x + 2L * y }
        }
        val seen = mutableListOf<Long>()
        model.solveFlow { maxTimeInSeconds = 5.0; numSearchWorkers = 1 }
            .collect { sol -> seen += sol.objective }
        seen.shouldNotBeEmpty()
        seen.last() shouldBe 150L  // max is 3*50 + 2*0 = 150
    }
}
```

### 5.2 `Knapsack.kt` with cpsat-kt

```kotlin
package apps.kt_cp_sat.ch05_optimization

import io.vanja.cpsat.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

data class Item(val name: String, val weight: Long, val value: Long)

fun main() = runBlocking {
    val items = listOf(
        Item("gold-ring", 2, 7),
        Item("silver-cup", 3, 5),
        Item("bronze-bust", 4, 3),
        Item("emerald", 5, 12),
        Item("ruby", 5, 10),
        Item("pearl-necklace", 3, 8),
        Item("brass-clock", 6, 4),
        Item("diamond", 7, 20),
        Item("sapphire", 4, 9),
        Item("opal", 3, 6),
        Item("platinum-bar", 8, 18),
        Item("jade-figurine", 5, 8),
        Item("amber-stone", 2, 4),
        Item("ivory-carving", 6, 11),
        Item("copper-coin", 1, 2),
    )
    val capacity = 20L

    val (model, x) = run {
        lateinit var vars: List<BoolVar>
        val m = cpModel {
            vars = items.map { boolVar("x_${it.name}") }
            constraint {
                weightedSum(vars.map { constant(1L) }, items.map { it.weight * 1L /* dummy */ })
                // Cleaner: build LinearExpr manually.
            }
            // The idiomatic form:
            constraint {
                (items.foldIndexed(constant(0L) as IntVar) { i, acc, it ->
                    // placeholder — use explicit weightedSum helper
                    acc
                }) le capacity
            }
            // BEST PRACTICE in cpsat-kt for knapsack:
            constraint {
                weightedSum(vars.map { it as IntVar /* BoolVar extends IntVar */ },
                            items.map { it.weight }) le capacity
            }
            maximize {
                weightedSum(vars.map { it as IntVar }, items.map { it.value })
            }
        }
        m to vars
    }

    // Stream incumbents
    model.solveFlow {
        maxTimeInSeconds = 5.0
        numSearchWorkers = 8
        logSearchProgress = true
    }.collect { sol ->
        println("t=${"%.3f".format(sol.wallTime)}  obj=${sol.objective}  bound=${sol.bound}")
    }

    // Final answer
    val final = model.solveBlocking { maxTimeInSeconds = 5.0; numSearchWorkers = 8 }
    when (final) {
        is SolveResult.Optimal, is SolveResult.Feasible -> {
            println("---")
            println("final obj = ${(final as? SolveResult.Optimal)?.objective ?: (final as SolveResult.Feasible).objective}")
            // `final.values[x[i]]` gives each selection.
        }
        else -> println("no solution (status=$final)")
    }
}
```

Caveat: the `BoolVar` → `IntVar` cast in `weightedSum(vars.map { it as IntVar }, ...)` is awkward — `BoolVar` in `cpsat-kt` v0.1 is its own wrapper; OR-Tools' Java `BoolVar` extends `IntVar`, but our wrappers are siblings. Fix this as a library improvement: add `fun BoolVar.asIntVar(): IntVar` (wrapping the same Java object) or overload `weightedSum` to accept `Iterable<BoolVar>`. Make that a separate commit before the chapter body — same TDD pattern as Chapter 4.

### 5.3 Early-stop at 5% gap

```kotlin
val result = model.solveBlocking {
    maxTimeInSeconds = 30.0
    numSearchWorkers = 8
    relativeGapLimit = 0.05
}
```

When the solver proves the gap drops below 5%, it stops with status `OPTIMAL` — note: CP-SAT reports `OPTIMAL` when the gap limit is hit, because technically the solution is optimal relative to the gap policy. The actual proven-optimal-to-zero-gap would be `OPTIMAL` with `gap = 0`.

## 6. MiniZinc implementation (optional)

```minizinc
int: n;
array[1..n] of int: weight;
array[1..n] of int: value;
int: capacity;
array[1..n] of var 0..1: x;

constraint sum(i in 1..n)(weight[i]*x[i]) <= capacity;
solve maximize sum(i in 1..n)(value[i]*x[i]);
```

With a `.dzn`:

```minizinc
n = 15;
weight = [2,3,4,5,5,3,6,7,4,3,8,5,2,6,1];
value  = [7,5,3,12,10,8,4,20,9,6,18,8,4,11,2];
capacity = 20;
```

Run with CP-SAT backend: `minizinc --solver com.google.or-tools knapsack.mzn knapsack.dzn`.

Declarative MIP-ish formulation — every MIP solver (HiGHS, Gurobi) also accepts this.

## 7. Comparison & takeaways

| Aspect | Python (`CpSolverSolutionCallback`) | Kotlin (`solveFlow` in `cpsat-kt`) |
|---|---|---|
| Incumbent streaming | subclass callback | `Flow<Solution>` |
| Cancellation | `solver.stop_search()` | `coroutineScope.cancel()` |
| Composition | manual (loop + break) | standard: `.take(5)`, `.debounce(...)`, etc. |
| Threading | callback fires on solver thread; use locks | channelFlow handles it |
| Idiomaticness | OK for Python | native, first-class for coroutine-based Kotlin |

**Key insight.** *The gap between incumbent and bound is the solver telling you how much more work it thinks is left. Reading the log and the gap is the #1 skill for production optimization.* A model that solves in 100ms with gap 0 and a model that times out at 30min with gap 15% are very different in the real world. The latter is often "good enough" — acknowledge the gap, don't pretend you proved optimal.

**Secondary insight.** *A `Flow<Solution>` is a better interface than a callback for anything non-trivial.* You can `take(1)` for "just the first feasible", `.scan` for trend analysis, `.debounce` to avoid spamming the UI — all standard operators. This is what `cpsat-kt` bought you by choosing coroutines upfront.

**Tertiary insight.** *Parallel workers make the log non-monotonic.* One worker is doing LNS and briefly has a worse bound than another worker proved a second ago; the solver merges bound info but you might see a temporary dip. That's not a bug — it's the portfolio at work. Don't panic at non-monotonic logs.

## 8. Exercises

### Exercise 5.1 — Bounded knapsack

**Problem.** Take the 15-item knapsack. Let each item `i` have `max_count[i]` in `{1, 2, 3}` (randomize per item). Change `x[i]` to integer variables with domain `[0, max_count[i]]`. Resolve. Compare the optimal value to 0-1 knapsack.

**Expected output:** both models solve to optimality; the bounded version has objective ≥ the 0-1 version (more items → more freedom).

**Acceptance criteria:** both languages agree on the optimum. Log the wall times and compare — bounded is harder, expect 2-5× more search.

<details><summary>Hint</summary>

In `cpsat-kt`, `intVar("x_$name", 0..maxCount)` instead of `boolVar(...)`. The weighted-sum constraint and objective need no change — they already work on `IntVar`.

</details>

### Exercise 5.2 — Plot convergence

**Problem.** Use the trace collected in §4.1 (or by consuming `solveFlow` in Kotlin) to plot incumbent + bound over time. One plot in Python (matplotlib); one in Kotlin (use `lets-plot-kotlin` or dump the trace to CSV and plot externally).

**Expected output:** a PNG showing the two curves converging.

**Acceptance criteria:** clear axis labels, both lines visible, legend distinguishing incumbent from bound.

<details><summary>Hint</summary>

For a small instance the whole solve finishes in <100ms and the plot has 1-2 points. Use a larger instance (try n=50 random items) to see real convergence.

</details>

### Exercise 5.3 — Stop early at 5% gap

**Problem.** Set `relative_gap_limit = 0.05` (Python: `solver.parameters.relative_gap_limit`; Kotlin: via `solveBlocking { relativeGapLimit = 0.05 }`). Run on a big instance (50 items, random). Compare: (a) wall time, (b) final gap, (c) how much worse the incumbent is vs running to gap 0.

**Expected output:** early-stop saves significant time; the incumbent is at most 5% worse (often 0-1% worse).

**Acceptance criteria:** you can articulate when this tradeoff is worth it in one sentence (hint: production latency SLAs).

<details><summary>Hint</summary>

Build a random instance with `random.seed(42)` to make the result reproducible. Run both versions, print wall-time and final objective, compute the difference.

</details>

### Exercise 5.4 — Multi-objective via lexicographic

**Problem.** The knapsack has two goals: (a) maximize value, (b) among same-value subsets, minimize total weight. Implement via solve-fix-resolve:

1. Maximize value → get `best_value`.
2. Add `model.add(value_expr == best_value)`.
3. Replace objective: `model.minimize(weight_expr)`.
4. Re-solve.

**Expected output:** same value as before, smallest total weight among max-value subsets.

**Acceptance criteria:** the weight of the lex-optimal subset is ≤ the weight of the plain-max-value subset.

<details><summary>Hint</summary>

CP-SAT doesn't have a native lex-objective. The "solve, fix, re-solve" pattern is the go-to. In Kotlin, you'll need to update the model — either mutate it in place (add constraint + new `maximize`/`minimize`, which replaces the objective) or build two separate `cpModel { }` blocks sharing an instance data structure. Both work.

</details>

### Exercise 5.5 — Enumerate all optimal solutions

**Problem.** For the 15-item knapsack, enumerate *all* subsets achieving the optimal value (there are usually a handful). Approach: (a) solve to get optimal value; (b) add `objective == best_value`; (c) `enumerate_all_solutions = True`, solve again with a callback that collects each.

**Expected output:** list all optimal subsets. Usually 1-5 of them.

**Acceptance criteria:** each printed subset has the same total value (the optimum) and satisfies the capacity.

<details><summary>Hint</summary>

`enumerate_all_solutions = true` is *incompatible* with an objective — you can't enumerate while optimizing. That's why you first solve, fix the objective as an equality constraint, drop the `maximize`, and THEN enumerate.

</details>

Solutions live in `apps/*/ch05-optimization/solutions/`. Try first; peek if stuck after 10 minutes.

## 9. Self-check

**Q1.** What does "bound" mean in CP-SAT, and how is it computed?

<details><summary>Answer</summary>

The **best objective bound** is a proven limit on the true optimum, derived from the solver's linear relaxation, cutting planes, and partial branch-and-bound proofs. For minimization it's a lower bound (`bound ≤ optimum ≤ incumbent`); for maximization it's an upper bound (`incumbent ≤ optimum ≤ bound`). It's computed throughout the solve by the LP engine and by proof-of-optimality logic on closed search subtrees. When `bound = incumbent`, the solver has proved optimality.

</details>

**Q2.** When is it OK to stop early with a nonzero gap?

<details><summary>Answer</summary>

When business value of extra solve time < expected improvement. Specifically: if the gap is small (say ≤5%), the incumbent is provably close to optimal — in most business contexts that's "good enough" and the extra minutes or hours to prove optimality aren't worth it. Also: SLA-bound services (must respond in <1s), interactive UIs, batch jobs with many similar problems where cumulative time matters. Never stop at a huge gap (e.g. 50%) without understanding *why* — huge gap means either the model's LP relaxation is weak (fixable) or the problem is genuinely hard (live with it or reformulate).

</details>

**Q3.** Why can parallel search make the log look non-monotonic?

<details><summary>Answer</summary>

`num_search_workers > 1` runs multiple solvers in a portfolio, each with different strategies (LNS, feasibility pump, LP-driven, HINT_SEARCH, etc.). They share incumbent and bound through a shared store. The log prints bound updates as they arrive from workers — and a worker running LNS might find an improved incumbent while another is mid-proof on a weaker bound, so the printed sequence can dip or bump. The *final* bound is globally correct; the intermediate log is per-worker. Read it as a flow of events, not a strictly monotonic sequence.

</details>

**Q4.** When you set `enumerate_all_solutions = True`, you must also…

<details><summary>Answer</summary>

…remove the objective (or at least understand it's ignored). CP-SAT's enumeration mode is for CSPs, not COPs. If you want "all *optimal* solutions," first solve to get the optimum, then add a constraint `objective_expr == optimum`, then remove the objective (by replacing with a satisfy-only model, or simply not calling `minimize`/`maximize` in a freshly rebuilt model), THEN enumerate.

</details>

**Q5.** Your solver reports `gap = 0.0` and `status = OPTIMAL`. 10 seconds in, it's still running. What's happening?

<details><summary>Answer</summary>

Different moments: `gap = 0` and `status = OPTIMAL` are set together only at the end of the solve. If you're looking at the log mid-solve and seeing `gap = 0` at 10 seconds, the solver has proved optimality and is in *shutdown* — typically cleaning up worker threads, printing the final log, and returning. The solve is essentially done; a few tens to hundreds of milliseconds of cleanup remain. If it's sitting at gap 0 for *minutes*, that's a bug worth reporting — file at https://github.com/google/or-tools/issues.

</details>

## 10. What this unlocks

You have full optimization machinery: objectives, bounds, gap, streaming, tuning. Chapter 6 tours the remaining global constraints (Circuit, Automaton, Table, Element, Inverse, Reservoir, LexLeq) — the rest of CP-SAT's vocabulary.

## 11. Further reading

- Google, [*CP-SAT solver tasks*](https://developers.google.com/optimization/cp/cp_tasks) — time limits, callbacks, limits in the official docs.
- Krupke, [*CP-SAT Primer Part 4: Parameters*](https://d-krupke.github.io/cpsat-primer/04_parameters.html) — the best deep dive into parameters and the log format.
- Krupke, [*CP-SAT Log Analyzer*](https://github.com/d-krupke/CP-SAT-Log-Analyzer) — parses the log and plots bound/incumbent over time; the Python version of what you build in Exercise 5.2.
- Wolsey, [*Integer Programming*](https://www.wiley.com/en-us/Integer+Programming-p-9780471283669) — the canonical IP textbook; chapter on branch-and-bound gives the MIP view of bounds. CP-SAT is hybrid but the LP engine lives inside it and this book's mental model transfers.
- Kotlin team, [*Asynchronous Flow*](https://kotlinlang.org/docs/flow.html) — `Flow`, `channelFlow`, cancellation basics. Reread §5.1 of this chapter after you've read this.
- Krupke, [*CP-SAT Primer Part 2: Coding patterns*](https://d-krupke.github.io/cpsat-primer/09_coding_patterns.html) — especially the sections on incremental modeling and lex-objective patterns.
