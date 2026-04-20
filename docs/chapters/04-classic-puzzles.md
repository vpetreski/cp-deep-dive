# Chapter 04 — Classic puzzles: N-Queens, SEND+MORE=MONEY, cryptarithmetic

> **Phase 2: CP-SAT basics + build cpsat-kt** · Estimated: ~3h · Status: ready-to-start · Last updated: 2026-04-19

## Goal

Build fluency with integer variables, boolean variables, and basic global constraints by solving three classic CP puzzles in both Python and Kotlin. By the end you've internalized `AllDifferent`, `AbsEquality`, and half-reification (`OnlyEnforceIf` / `enforceIf`), and `cpsat-kt` has grown to support them.

## Before you start

- **Prerequisites:** Chapters 1-3. You have working Python + Kotlin CP-SAT setups; `cpsat-kt` v0.1 is in place.
- **Required reading:**
  - [`knowledge/cp-sat/overview.md §4`](../knowledge/cp-sat/overview.md) — the constraints reference, especially `AllDifferent`, `AbsEquality`, and reification via `onlyEnforceIf`.
  - [`knowledge/cp-theory/overview.md §5`](../knowledge/cp-theory/overview.md) — why `AllDifferent` beats pairwise `!=`, the Hall-interval argument.
  - Google, [*N-queens in CP-SAT*](https://developers.google.com/optimization/cp/queens) — the official walkthrough for the Python version.
  - Google, [*Cryptarithmetic*](https://developers.google.com/optimization/cp/cryptarithmetic) — the SEND+MORE=MONEY example in the official docs.
- **Environment:** unchanged from Chapter 3. You'll extend `cpsat-kt` mid-chapter.

## Concepts introduced this chapter

- **`AllDifferent(x₁, …, xₙ)`** — a global constraint enforcing pairwise distinctness with a specialized propagator (Régin's bipartite-matching algorithm).
- **`AddAbsEquality(t, x)`** — `t = |x|`. Used for diagonals of a safe/unsafe queens formulation, and anywhere you need `|x - y|`.
- **`OnlyEnforceIf` (Python) / `enforceIf` (cpsat-kt)** — half-reification: "this constraint holds only when the literal is true."
- **Full reification** — `literal ⇔ constraint`; posted as two half-reifications, one per direction.
- **Symmetry breaking** — adding constraints that eliminate isomorphic duplicate solutions (e.g. `q[0] ≤ n/2` to halve the N-Queens search).
- **Linearization patterns** — modeling `max`, `abs`, `OR`, `XOR` as integer-linear constraints, since CP-SAT's core is integer-linear + SAT.

## 1. Intuition — three puzzles, one solver

**N-Queens.** Place N queens on an N×N chessboard so no two attack each other. One variable per column, value = the queen's row. Rows distinct, `\`-diagonals distinct, `/`-diagonals distinct. Three `AllDifferent`s. That's it.

**SEND+MORE=MONEY.** Assign digits 0-9 to the letters `S, E, N, D, M, O, R, Y` so that the schoolbook addition works out — `S ≠ 0, M ≠ 0`, and letters map to distinct digits. One `AllDifferent` over the letter variables, one equation with place values.

**Cryptarithmetic (your pick).** Same genre, different letters. `CROSS + ROADS = DANGER`, or `THIS + IS = NICE`. You choose one and model it.

The goal is less about the puzzles and more about: these are your *tools-of-the-trade* examples for `AllDifferent`, `AddAbsEquality`, and reified constraints. Muscle memory you'll lean on from now on.

## 2. Formal definition — the three models

### 2.1 N-Queens

```
Variables:  q[i] ∈ {0, ..., n-1}     for i = 0, ..., n-1
Constraints:
    AllDifferent(q)                    # distinct rows
    AllDifferent(q[i] + i for i)       # distinct \ diagonals
    AllDifferent(q[i] - i for i)       # distinct / diagonals
(Optional symmetry-breaking:) q[0] ≤ (n - 1) / 2
```

Why the diagonal trick works: two queens in columns `i, j` are on the same `\` diagonal iff `q[i] - i = q[j] - j`, and on the same `/` diagonal iff `q[i] + i = q[j] + j`. Requiring `q[i] + i` and `q[i] - i` to be all-different over all columns enforces both diagonal constraints at once. This is a classic "encode two constraints as one AllDifferent on a derived expression" pattern — memorize it.

### 2.2 SEND+MORE=MONEY

Letters: `S, E, N, D, M, O, R, Y`. Each maps to a distinct digit.

```
Variables:
    S, E, N, D, M, O, R, Y ∈ {0, ..., 9}
Constraints:
    AllDifferent(S, E, N, D, M, O, R, Y)
    S ≥ 1, M ≥ 1                                 # no leading zeros
    1000·S + 100·E + 10·N + D
  + 1000·M + 100·O + 10·R + E
  = 10000·M + 1000·O + 100·N + 10·E + Y
```

The equation is one linear constraint. No reification needed — it's a single equality.

### 2.3 Cryptarithmetic — your choice

Pick one you haven't seen before. Suggestions:

- `DONALD + GERALD = ROBERT`
- `EAT + THAT = APPLE`
- `CROSS + ROADS = DANGER`

Variables = distinct letters (`DONALDGERBT` = 10 letters for the first; 11 letters = infeasible since only 10 digits exist, verify before modeling). Constraints: same pattern as SEND+MORE.

## 3. Worked example by hand — 4-Queens revisited

You did 4-Queens by hand in Chapter 1. Now do it again, but model it in the three-`AllDifferent` formulation. Columns 0-3, queens `q[0], q[1], q[2], q[3] ∈ {0, 1, 2, 3}`.

```
Let q = (q[0], q[1], q[2], q[3])

AllDifferent(q[0], q[1], q[2], q[3])     → rows distinct
AllDifferent(q[0]+0, q[1]+1, q[2]+2, q[3]+3)   → \ diagonals distinct
AllDifferent(q[0]-0, q[1]-1, q[2]-2, q[3]-3)   → / diagonals distinct
```

Try `q = (1, 3, 0, 2)`:

- Rows: `{1, 3, 0, 2}` — all distinct ✓
- `q[i] + i`: `{1+0, 3+1, 0+2, 2+3} = {1, 4, 2, 5}` — all distinct ✓
- `q[i] - i`: `{1-0, 3-1, 0-2, 2-3} = {1, 2, -2, -1}` — all distinct ✓

Valid. Try `q = (2, 0, 3, 1)`:

- Rows: `{2, 0, 3, 1}` ✓
- `+`: `{2, 1, 5, 4}` ✓
- `-`: `{2, -1, 1, -2}` ✓

Valid. These are the two solutions of 4-Queens.

Now try the "bad" assignment `q = (0, 1, 2, 3)` (all queens on the main `\` diagonal):

- Rows: `{0, 1, 2, 3}` ✓
- `+`: `{0, 2, 4, 6}` ✓
- `-`: `{0, 0, 0, 0}` — **not distinct** ✗

`AllDifferent` on the `/`-diagonals kills this instantly. That's the propagator earning its keep.

## 4. Python implementation

Scaffold at `apps/py-cp-sat/ch04-puzzles/`. One file per puzzle:

### 4.1 `n_queens.py`

```python
"""Chapter 4 — N-Queens in CP-SAT (Python)."""
from __future__ import annotations
import time
from ortools.sat.python import cp_model


def solve_n_queens(n: int = 8, enumerate_all: bool = False) -> int:
    """Count the N-queens solutions (or find one if enumerate_all=False)."""
    model = cp_model.CpModel()
    # q[i] is the row of the queen in column i.
    q = [model.new_int_var(0, n - 1, f"q{i}") for i in range(n)]

    model.add_all_different(q)
    model.add_all_different(q[i] + i for i in range(n))   # \ diagonals
    model.add_all_different(q[i] - i for i in range(n))   # / diagonals

    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = 30.0

    if enumerate_all:
        solver.parameters.enumerate_all_solutions = True

        class Counter(cp_model.CpSolverSolutionCallback):
            def __init__(self) -> None:
                super().__init__()
                self.count = 0
            def on_solution_callback(self) -> None:
                self.count += 1

        counter = Counter()
        status = solver.solve(model, counter)
        return counter.count if status in (cp_model.OPTIMAL, cp_model.FEASIBLE) else 0

    status = solver.solve(model)
    return 1 if status in (cp_model.OPTIMAL, cp_model.FEASIBLE) else 0


if __name__ == "__main__":
    for n in [8, 12, 50]:
        t0 = time.perf_counter()
        count = solve_n_queens(n, enumerate_all=(n <= 12))
        t1 = time.perf_counter()
        print(f"n={n}: {'count' if n <= 12 else 'first-solution found'}={count}, {t1-t0:.3f}s")
```

### 4.2 `send_more_money.py`

```python
"""Chapter 4 — SEND+MORE=MONEY in CP-SAT (Python)."""
from ortools.sat.python import cp_model


def solve_send_more_money() -> dict[str, int] | None:
    model = cp_model.CpModel()

    letters = ["S", "E", "N", "D", "M", "O", "R", "Y"]
    v = {c: model.new_int_var(0, 9, c) for c in letters}

    model.add_all_different(v.values())
    model.add(v["S"] >= 1)
    model.add(v["M"] >= 1)

    send = 1000 * v["S"] + 100 * v["E"] + 10 * v["N"] + v["D"]
    more = 1000 * v["M"] + 100 * v["O"] + 10 * v["R"] + v["E"]
    money = 10000 * v["M"] + 1000 * v["O"] + 100 * v["N"] + 10 * v["E"] + v["Y"]
    model.add(send + more == money)

    solver = cp_model.CpSolver()
    status = solver.solve(model)
    if status in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        return {c: solver.value(v[c]) for c in letters}
    return None


if __name__ == "__main__":
    sol = solve_send_more_money()
    if sol is None:
        print("no solution")
    else:
        for c, d in sol.items():
            print(f"{c} = {d}")
        # Expected: S=9 E=5 N=6 D=7 M=1 O=0 R=8 Y=2
```

### 4.3 `crypt_donald_gerald.py` (one of the chapter's choice puzzles)

```python
"""Chapter 4 — DONALD + GERALD = ROBERT in CP-SAT (Python)."""
from ortools.sat.python import cp_model


def solve() -> dict[str, int] | None:
    model = cp_model.CpModel()
    letters = list("DONALGERT")  # D O N A L G E R T → 9 distinct letters (B is in ROBERT too?)
    # Letters in DONALD, GERALD, ROBERT: D, O, N, A, L, G, E, R, B, T  → 10 letters. Good.
    letters = ["D", "O", "N", "A", "L", "G", "E", "R", "B", "T"]
    v = {c: model.new_int_var(0, 9, c) for c in letters}
    model.add_all_different(v.values())

    # No leading zeros.
    for first in ("D", "G", "R"):
        model.add(v[first] >= 1)

    donald = (100000 * v["D"] + 10000 * v["O"] + 1000 * v["N"]
              + 100 * v["A"] + 10 * v["L"] + v["D"])
    gerald = (100000 * v["G"] + 10000 * v["E"] + 1000 * v["R"]
              + 100 * v["A"] + 10 * v["L"] + v["D"])
    robert = (100000 * v["R"] + 10000 * v["O"] + 1000 * v["B"]
              + 100 * v["E"] + 10 * v["R"] + v["T"])
    model.add(donald + gerald == robert)

    solver = cp_model.CpSolver()
    status = solver.solve(model)
    if status in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        return {c: solver.value(v[c]) for c in letters}
    return None


if __name__ == "__main__":
    sol = solve()
    print(sol)
```

Run all three:

```bash
cd apps/py-cp-sat/ch04-puzzles
uv sync
uv run python src/n_queens.py
uv run python src/send_more_money.py
uv run python src/crypt_donald_gerald.py
```

## 5. Kotlin implementation (cpsat-kt DSL)

Scaffold at `apps/kt-cp-sat/ch04-puzzles/`. One file per puzzle.

### 5.1 First, extend `cpsat-kt` with `allDifferent` on expressions

Before writing the N-Queens Kotlin, notice: we need `allDifferent(q[i] + i for i in ...)`. v0.1 of `cpsat-kt` only had `allDifferent(vars: Iterable<IntVar>)`. We need a version that takes `Iterable<LinearExpr>`.

Add to `libs/cpsat-kt/src/main/kotlin/io/vanja/cpsat/Constraints.kt`:

```kotlin
@JvmName("allDifferentExprs")
fun CpModel.allDifferent(exprs: Iterable<LinearExpr>) {
    java.addAllDifferent(exprs.map { it.java }.toTypedArray())
}
```

Commit this as a separate commit *before* the N-Queens chapter code. With a test (`libs/cpsat-kt/src/test/kotlin/io/vanja/cpsat/ConstraintsSpec.kt`):

```kotlin
"allDifferent accepts LinearExpr iterables (for N-Queens diagonals)" {
    val m = cpModel {
        val q = List(4) { i -> intVar("q$i", 0..3) }
        allDifferent(q)
        allDifferent(q.mapIndexed { i, v -> v + i.toLong() })
        allDifferent(q.mapIndexed { i, v -> v - i.toLong() })
    }
    (m.solveBlocking() is SolveResult.Optimal || m.solveBlocking() is SolveResult.Feasible) shouldBe true
}
```

This is the pattern: **chapter discovers missing DSL piece → you extend the library with a failing test first → then use it in the chapter**. Keep those as separate commits in the `libs/cpsat-kt/` module.

### 5.2 `NQueens.kt`

```kotlin
package apps.kt_cp_sat.ch04_puzzles

import io.vanja.cpsat.*
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking

fun nQueens(n: Int, enumerateAll: Boolean = false): Long = runBlocking {
    val model = cpModel {
        val q = List(n) { i -> intVar("q$i", 0 until n) }
        allDifferent(q)
        allDifferent(q.mapIndexed { i, v -> v + i.toLong() })
        allDifferent(q.mapIndexed { i, v -> v - i.toLong() })
    }
    val result = if (enumerateAll) {
        // For v0.1 of cpsat-kt we don't yet have solveFlow; use solveBlocking with
        // enumerateAllSolutions and raw Java callback. This motivates Chapter 5's solveFlow.
        var count = 0L
        val solver = com.google.ortools.sat.CpSolver()
        solver.parameters.enumerateAllSolutions = true
        solver.parameters.maxTimeInSeconds = 30.0
        val cb = object : com.google.ortools.sat.CpSolverSolutionCallback() {
            override fun onSolutionCallback() { count++ }
        }
        solver.solve(model.toJava(), cb)
        return@runBlocking count
    } else {
        model.solve { maxTimeInSeconds = 30.0 }
    }
    if (result is SolveResult.Optimal || result is SolveResult.Feasible) 1L else 0L
}

fun main() {
    for (n in listOf(8, 12, 50, 200)) {
        val enumerate = n <= 12
        val elapsed = measureTimeMillis {
            val count = nQueens(n, enumerateAll = enumerate)
            println("n=$n: ${if (enumerate) "count" else "first-found"}=$count")
        }
        println("   elapsed = ${elapsed}ms")
    }
}
```

Expected counts: n=4 → 2, n=8 → 92, n=12 → 14200. For n=50, 200 we only look for a single solution.

### 5.3 `SendMoreMoney.kt`

```kotlin
package apps.kt_cp_sat.ch04_puzzles

import io.vanja.cpsat.*
import kotlinx.coroutines.runBlocking

fun sendMoreMoney(): Map<Char, Long>? = runBlocking {
    val letters = "SENDMORY".toList()
    var sol: Map<Char, Long>? = null

    val model = cpModel {
        val v = letters.associateWith { c -> intVar(c.toString(), 0..9) }
        allDifferent(v.values)
        constraint { v['S']!! ge 1L }
        constraint { v['M']!! ge 1L }

        val send = 1000L * v['S']!! + 100L * v['E']!! + 10L * v['N']!! + v['D']!! * 1L
        val more = 1000L * v['M']!! + 100L * v['O']!! + 10L * v['R']!! + v['E']!! * 1L
        val money = 10000L * v['M']!! + 1000L * v['O']!! + 100L * v['N']!! + 10L * v['E']!! + v['Y']!! * 1L
        constraint { (send + more) eq money }  // <-- NOTE: this requires `eq` between LinearExprs; see §5.4

        // Capture solution via a trick: stash the var map and re-read after solve().
    }

    // … real code factors this out; see solutions folder for the clean version
    val result = model.solve()
    // (Solution extraction snipped for brevity — see the scaffold.)
    sol
}
```

### 5.4 Second cpsat-kt extension: `eq` between two LinearExprs

The SEND+MORE code needs `(send + more) eq money` where both sides are `LinearExpr`. v0.1 only supports `LinearExpr eq Long`. Add to `Constraints.kt`:

```kotlin
infix fun LinearExpr.eq(rhs: LinearExpr): Unit {
    // We can either: (a) move rhs to LHS as lhs - rhs = 0; (b) use addEquality(lhs, rhs) directly.
    // OR-Tools Java's addEquality(LinearArgument, LinearArgument) exists:
    model.java.addEquality(this.java, rhs.java)
}
```

Wait — the infix function needs `model` in scope. This is in `ConstraintBuilder`, so the receiver is already scoped. Adjust:

```kotlin
// In ConstraintBuilder:
infix fun LinearExpr.eq(rhs: LinearExpr) { model.java.addEquality(this.java, rhs.java) }
infix fun LinearExpr.le(rhs: LinearExpr) { model.java.addLessOrEqual(this.java, rhs.java) }
infix fun LinearExpr.ge(rhs: LinearExpr) { model.java.addGreaterOrEqual(this.java, rhs.java) }
// ... etc
```

Commit that as a separate library bump before wiring SEND+MORE.

### 5.5 Half-reification extension: `enforceIf`

N-Queens doesn't need it, but exercise 4.3 does — and it's natural to add it here. Add a new file `libs/cpsat-kt/src/main/kotlin/io/vanja/cpsat/Enforcement.kt`:

```kotlin
package io.vanja.cpsat

@CpSatDsl
class EnforcedConstraintBuilder internal constructor(
    internal val model: CpModel,
    internal val literal: com.google.ortools.sat.Literal,
) {
    infix fun LinearExpr.eq(rhs: Long) {
        model.java.addEquality(this.java, rhs).onlyEnforceIf(literal)
    }
    infix fun LinearExpr.le(rhs: Long) {
        model.java.addLessOrEqual(this.java, rhs).onlyEnforceIf(literal)
    }
    // ... ge, lt, gt, neq
}

fun CpModel.enforceIf(b: BoolVar, block: EnforcedConstraintBuilder.() -> Unit) {
    EnforcedConstraintBuilder(this, b.java).block()
}

fun CpModel.enforceIf(b: BoolVar, negated: Boolean = false, block: EnforcedConstraintBuilder.() -> Unit) {
    val lit = if (negated) b.java.not() else b.java
    EnforcedConstraintBuilder(this, lit).block()
}
```

For full reification (`b ⇔ constraint`), post both sides:

```kotlin
val b = boolVar("b")
enforceIf(b) { x + y eq 10L }       // b ⇒ (x+y=10)
enforceIf(b, negated = true) { x + y neq 10L }  // ¬b ⇒ (x+y ≠ 10)
```

The library now supports the reification pattern; exercise 4.3 uses it.

## 6. MiniZinc implementation (optional)

Declarative reference for the curious — you'll formalize all three in Chapter 7:

```minizinc
% n-queens.mzn
include "alldifferent.mzn";
int: n;
array[1..n] of var 1..n: q;
constraint alldifferent(q);
constraint alldifferent([q[i] + i | i in 1..n]);
constraint alldifferent([q[i] - i | i in 1..n]);
solve satisfy;
```

Same three-`alldifferent` pattern. MiniZinc shines here — it's basically the math on the page.

## 7. Comparison & takeaways

| Aspect | Python | Kotlin (via `cpsat-kt`) |
|---|---|---|
| N-Queens model size | ~8 lines | ~8 lines |
| Diagonals expression | `q[i] + i for i in range(n)` | `q.mapIndexed { i, v -> v + i.toLong() }` |
| `AllDifferent` type | auto-detected (ints, expressions) | overloaded: one for `Iterable<IntVar>`, one for `Iterable<LinearExpr>` |
| SEND+MORE solution extraction | `{c: solver.value(v[c]) for c in letters}` | `letters.associateWith { result.values[v[it]] }` |
| Reification | `.only_enforce_if(b)` chained to a constraint | `enforceIf(b) { ... }` block |
| Wall time (N=50 single-sol) | ~200ms | ~200ms (identical — same solver) |
| Wall time (N=12 enumerate) | ~3s | ~3s |

**Key insight.** *Once `cpsat-kt` has `allDifferent(Iterable<LinearExpr>)` and `enforceIf`, Kotlin reads as densely as Python for these puzzles.* The DSL disappeared — what's left is the problem.

**Secondary insight.** *`AllDifferent` over a derived expression (`q[i] + i`) is the canonical way to model "pairwise-distinct function values." Remember the trick.* You'll use it in NSP for shift-pattern constraints ("all nurses' weekly shift signatures differ"), in routing for time-window precedence, everywhere.

**Tertiary insight.** *The library grows per chapter.* This chapter added `allDifferent(Iterable<LinearExpr>)`, `LinearExpr eq/le/ge LinearExpr`, and `enforceIf`. Separate commits, each with a test. When you pick this up in Chapter 5 for `solveFlow`, the same pattern continues.

## 8. Exercises

### Exercise 4.1 — N=200 queens, what dominates?

**Problem.** Run N-Queens for n = 100, 200, 500 (first-solution only). Measure wall time in both languages. Determine what dominates the runtime: model-building, presolve, search?

**Expected output:** a small table: n, build-time, solve-time, status. The gap between Python and Kotlin should be <2× (the solver is the same C++ core).

**Acceptance criteria:** you can explain which phase dominates for each n. Hint: add `solver.parameters.log_search_progress = True` and read the log — it tells you presolve time, search time, first-solution time.

<details><summary>Hint</summary>

N-Queens has three `AllDifferent`s over n variables each. Presolve does very little here (there's no implicit structure to collapse). For small n, search is instant. For large n, search dominates but CP-SAT's portfolio finds a first solution quickly — minutes of solve time don't show up until n~5000+ on a laptop. Compare `presolveDuration` and `wallTime` in the log.

</details>

### Exercise 4.2 — SEND+MORE variants

**Problem.** Modify `SEND + MORE = MONEY` into three variants:

- `SEND + MOST = MONEY` (replace MORE with MOST) — feasible or not?
- `CROSS + ROADS = DANGER` (new letters) — find a solution.
- Enumerate all solutions to `TWO + TWO = FOUR`. How many are there?

**Expected output:** status and solution/count for each; confirm results match across Python and Kotlin.

**Acceptance criteria:** `CROSS+ROADS=DANGER` has at least one solution printed; TWO+TWO=FOUR has a specific count (let the solver tell you — don't peek online).

<details><summary>Hint</summary>

TWO+TWO=FOUR has 4 distinct letters (T, W, O, F, U, R — that's 6 actually; count again). T, W, O, F, U, R = 6 letters. Leading zeros forbidden on T and F. Enumerate all; should be a small number.

</details>

### Exercise 4.3 — Reified cryptarithmetic

**Problem.** In `SEND+MORE=MONEY`, add a boolean `odd_e = 1 iff E is odd`. Extract it via `enforceIf`. Then, add a constraint that if `odd_e` then `M + O ≥ 5` (arbitrary, illustrates reification).

**Expected output:** the original `(9, 5, 6, 7, 1, 0, 8, 2)` solution should satisfy both; confirm `odd_e = 1` and `M + O = 1 ≥ 5`… wait, that's infeasible. So either the reification disables that branch, or the solver finds another solution. Which happens?

**Acceptance criteria:** you can explain the outcome in one sentence: either "no solution because the implied constraint kills it" or "different solution because another `E=odd` path exists."

<details><summary>Hint</summary>

Reification pattern: `odd_e ⇔ (E mod 2 == 1)`. CP-SAT has `addModuloEquality(t, E, 2)` and then `addImplication` / `enforceIf`. Post both directions. For the implication, `enforceIf(odd_e) { M + O ge 5L }`.

</details>

### Exercise 4.4 — Custom cryptarithmetic

**Problem.** Invent your own 3-term cryptarithmetic. Example: `RED + BLUE = GREEN`. Check the letter count (10 letters max — unique digits only). Solve in both languages.

**Expected output:** a solution, or "infeasible" if your letters don't admit one. Run it by a friend; did they find it fun?

**Acceptance criteria:** your choice has between 8 and 10 distinct letters. Solve time < 1s on any CP-SAT install.

<details><summary>Hint</summary>

Count letters: R, E, D, B, L, U, G, N = 8 distinct. `RED + BLUE = GREEN` has `GREEN` starting with G, so G ≥ 1. No other leading letters to watch. Should solve in milliseconds.

</details>

### Exercise 4.5 — Symmetry breaking measurable win

**Problem.** N-Queens has 8-fold rotational/reflective symmetry. Adding `q[0] < q[n-1]` breaks some of it. Enumerate all solutions for n = 8 with and without the breaker. Report the counts and times.

**Expected output (n=8):** 92 unique solutions without, ~12 unique solutions with the particular breaker (the exact number depends on which symmetries you break). The solver should finish faster *with* the breaker.

**Acceptance criteria:** you measure a concrete speedup and explain why in one sentence. Spoiler: symmetry breaking exposes fewer isomorphic branches; the solver finds each remaining branch once instead of 8 times.

<details><summary>Hint</summary>

`model.add(q[0] < q[n-1])` (Python) / `constraint { q[0] lt q.last() }` (Kotlin). For full symmetry breaking in N-Queens, see Gent & Smith, [*Symmetry Breaking in Constraint Programming*](https://research.ing.unitn.it/flinks/GentSmith-1999-ECAI-Symmetry.pdf) — lex-ordering is the standard.

</details>

Solutions in `apps/py-cp-sat/ch04-puzzles/solutions/` and `apps/kt-cp-sat/ch04-puzzles/solutions/`. Don't peek before trying.

## 9. Self-check

**Q1.** How does `AllDifferent` scale differently than pairwise `!=` in CP-SAT?

<details><summary>Answer</summary>

`AllDifferent` has a dedicated propagator (Régin's algorithm) running in polynomial time that enforces *domain consistency* — it removes values globally based on bipartite-matching arguments and Hall intervals. Pairwise `!=` uses only binary propagation, missing pigeonhole detection. On N-Queens, the three `AllDifferent`s give vastly stronger pruning than `O(n²)` pairwise `!=` would — you can measure it by swapping the implementation and comparing `num_branches` in the solver log.

</details>

**Q2.** What is `OnlyEnforceIf` for, and when is half-reification *not* enough?

<details><summary>Answer</summary>

`OnlyEnforceIf(literal)` attaches a constraint such that it's enforced only when the literal is true: `literal ⇒ constraint`. That's half-reification. It's not enough when you need the *converse* direction too — e.g. "I want `b` to reflect the truth of `x + y = 10`", meaning `b ⇔ (x + y = 10)`. For that you need *full* reification: post `constraint.only_enforce_if(b)` AND `negated_constraint.only_enforce_if(~b)`. In `cpsat-kt`, `enforceIf(b, negated = true) { ... }` posts the second direction.

</details>

**Q3.** For N=200 queens, what limits throughput — CPU, memory, or search strategy?

<details><summary>Answer</summary>

Usually *search strategy* for first-feasible, and *branches explored* for proof-of-done. CP-SAT's portfolio finds a first solution in seconds (large instances have many feasible solutions; you hit one fast). Proving optimality / counting all solutions is where branching dominates and `num_search_workers` + symmetry breaking buy you real wins. CPU is rarely the bottleneck on modern laptops for single-instance N-Queens. Memory becomes a factor only past N ~1000.

</details>

**Q4.** Why does SEND+MORE=MONEY have exactly one solution (9567 + 1085 = 10652)?

<details><summary>Answer</summary>

The letters force a tightly constrained linear combination — `S + M` must carry into a new digit at the leftmost position, pinning `M = 1` and (after propagation) `S = 9` uniquely. With those fixed, the rest follows by combinatorial elimination in the equation. A great exercise for CP reasoning, and a lucky accident that one-solution cryptarithmetics got so popular in textbooks.

</details>

**Q5.** You add `enforceIf(b) { x le 10 }` and the solver returns infeasible. Does that mean `b` has to be false in every solution?

<details><summary>Answer</summary>

Not directly — it means no feasible `(b, x, …)` assignment exists given *all* constraints. If the rest of the model forces `b = true`, then `x > 10` is the cause. If the rest forces `x > 10`, then `b = false` is the cause. Practical debugging: comment out the `enforceIf`, re-solve; if feasible, the `enforceIf` is the culprit and you reason from there. For bigger models, `solver.sufficient_assumptions_for_infeasibility()` (Python) returns a minimal infeasible subset of assumption literals.

</details>

## 10. What this unlocks

You now have fluency with feasibility problems (CSPs) and a fully-loaded `cpsat-kt` for linear / `AllDifferent` / reified work. Chapter 5 moves from "find a solution" to "find the *best* solution" — objectives, bounds, gaps, callbacks, streaming.

## 11. Further reading

- Google, [*N-queens example*](https://developers.google.com/optimization/cp/queens) — the official walkthrough; mirrors what you just built.
- Google, [*Cryptarithmetic*](https://developers.google.com/optimization/cp/cryptarithmetic) — SEND+MORE=MONEY in the OR-Tools docs.
- Régin, [*A filtering algorithm for constraints of difference*](https://www.aaai.org/Papers/AAAI/1994/AAAI94-055.pdf), AAAI 1994 — the prototype paper. §3 is accessible even without a CS-theory background.
- Hakan Kjellerstrand's [CP model collection](http://www.hakank.org/) — a treasure trove of cryptarithmetics and puzzle models in many languages, great for Exercise 4.4 inspiration.
- Gent & Smith, [*Symmetry Breaking in Constraint Programming*](https://research.ing.unitn.it/flinks/GentSmith-1999-ECAI-Symmetry.pdf), ECAI 1999 — the canonical symmetry-breaking paper; Exercise 4.5 territory.
- MiniZinc Handbook, [§2 global constraints](https://docs.minizinc.dev/en/stable/predicates.html) — declarative statement of `alldifferent`; warm-up for Chapter 7.
