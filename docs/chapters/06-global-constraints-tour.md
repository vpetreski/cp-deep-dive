---
title: "Chapter 6 — The global constraints tour"
phase: 2
estimated: 3h
status: draft
last_updated: 2026-04-19
---

# Chapter 6 — The global constraints tour

## 1. Goal

You already use `AllDifferent`. This chapter tours the rest of CP-SAT's *global constraints* — the vocabulary that separates a toy CP solver from a production one:

- `Circuit` (Hamiltonian cycle — TSP)
- `Table` (explicit allowed tuples)
- `Element` (array lookup with variable index)
- `Automaton` (sequence accepted by a DFA — the backbone of shift-transition rules)
- `Inverse` (permutation in both directions)
- `LexicographicLessEqual` (symmetry breaking)
- `Reservoir` (sliding supply/demand with bounds)

One tiny runnable example per constraint, Python and Kotlin side by side. By the end, you extend `cpsat-kt` with every one of them — bringing the library to ~80% of CP-SAT's surface — and you know which tool to reach for when.

## 2. Before you start

- Chapters 1–5 complete; `cpsat-kt` v0.1 is wired into `apps/kt-cp-sat/` via composite build.
- You understand that a *global constraint* is a high-level pattern with a dedicated propagator that is strictly stronger than its obvious decomposition into primitives.
- You've read [`docs/knowledge/cp-sat/overview.md`](../knowledge/cp-sat/overview.md) sections on global constraints.

## 3. Concepts introduced

| Constraint | One-line purpose | Canonical example |
|---|---|---|
| `Circuit` | Binary arcs form one Hamiltonian cycle | TSP |
| `Table(vars, tuples)` | Assignment of `vars` must equal one listed row | Regulations, compatibility tables |
| `Element(index, values, target)` | `target == values[index]`, with `index` a variable | Variable lookup |
| `Automaton(vars, q0, T, F)` | Sequence of `vars` is accepted by the DFA | Sliding-window shift rules |
| `Inverse(f, g)` | `g[f[i]] == i` for all i (two-way permutation) | Nurse↔task assignment |
| `LexicographicLessEqual(a, b)` | Array `a` is ≤ array `b` in lex order | Symmetry breaking |
| `Reservoir(times, demands, min, max)` | Running sum of demands stays in `[min, max]` | Inventory, tank levels |

*Decomposition rule of thumb.* Any global can be decomposed into linear/boolean primitives. The decomposition is **correct but weak** — it doesn't give the solver enough propagation. The global's custom propagator is what makes it worth using.

## 4. Intuition

You already intuited `AllDifferent`: it's a single constraint with a *filtering algorithm* (Régin 1994, matching-based) that prunes many more values than the N×N pairwise `!=` decomposition. Every global in this chapter is the same idea: **a pattern common enough that it deserves a specialized propagator**.

- `Circuit`: look at the arc boolean variables as edges of a graph; the propagator keeps sub-tours from forming.
- `Table`: store a set of allowed rows; propagate by intersecting allowed rows with current domains.
- `Element`: the classic "variable-index array lookup"; propagator maintains `index`'s domain to just the positions where `values[index]` could still equal `target`.
- `Automaton`: run a DFA over the sequence of decision variables; the propagator maintains the set of reachable states at every position.
- `Inverse`: if `f[3] = 7` then `g[7] = 3`, and both arrays are permutations. Used when you have two equivalent viewpoints (forward map + inverse map).
- `Lex ≤`: fixes symmetry — two arrays that would otherwise be interchangeable get forced into a canonical order.
- `Reservoir`: like `Cumulative` but with signed deltas — deposits and withdrawals; the capacity bounds hold at every timepoint.

## 5. Formal definitions (one line each)

- `Circuit(arcs)`: each variable represents a directed arc with an associated boolean; the selected arcs form one Hamiltonian circuit on `n` nodes.
- `Table(x₁,…,xₙ, T)`: `(x₁,…,xₙ) ∈ T`, where `T` ⊆ Z^n is a finite list of allowed tuples.
- `Element(index, A, target)`: `target = A[index]`, with `index` an `IntVar`, `A` an array of `IntVar`s (or ints), `target` an `IntVar`.
- `Automaton((x₁…xₙ), q₀, δ, F)`: there is a DFA run such that reading the symbols `x₁, x₂, …, xₙ` starting from `q₀` ends in a state in `F`, using transition function `δ: Q × Σ → Q`.
- `Inverse(f, g)`: for all `i`, `g[f[i]] = i` and `f[g[i]] = i`.
- `LexLeq(a, b)`: arrays `a` and `b` have the same length and `a ≤_lex b`.
- `Reservoir(ts, ds, lo, hi)`: for every time `t`, `lo ≤ Σ_{i : ts[i] ≤ t} ds[i] ≤ hi`.

## 6. Worked examples

The rest of the chapter is six mini-problems, each a complete Python program and its Kotlin twin. The goal isn't problem-solving — it's getting every constraint into your fingers.

### 6.1 `Circuit` — TSP on 8 cities

Traveling Salesman on an 8-city instance with a symmetric distance matrix. Minimize total tour length. Model: a boolean variable `arc[i,j]` per directed arc; `AddCircuit([(i, j, arc[i,j]) for all i,j])`.

**Python (`apps/python/ch06-globals/tsp_circuit.py`):**

```python
from ortools.sat.python import cp_model

DIST = [
    [ 0, 29, 20, 21, 16, 31, 100, 12],
    [29,  0, 15, 29, 28, 40,  72, 21],
    [20, 15,  0, 15, 14, 25,  81,  9],
    [21, 29, 15,  0,  4, 12,  92, 12],
    [16, 28, 14,  4,  0, 16,  94,  9],
    [31, 40, 25, 12, 16,  0,  95, 24],
    [100,72, 81, 92, 94, 95,   0, 90],
    [12, 21,  9, 12,  9, 24,  90,  0],
]

def solve_tsp():
    n = len(DIST)
    model = cp_model.CpModel()
    arcs = {}
    for i in range(n):
        for j in range(n):
            if i == j:
                continue
            arcs[i, j] = model.NewBoolVar(f"arc_{i}_{j}")

    model.AddCircuit([(i, j, arcs[i, j]) for (i, j) in arcs])

    model.Minimize(sum(DIST[i][j] * arcs[i, j] for (i, j) in arcs))

    solver = cp_model.CpSolver()
    solver.parameters.log_search_progress = True
    status = solver.Solve(model)
    if status in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        tour = [0]
        cur = 0
        while True:
            for j in range(n):
                if j != cur and solver.BooleanValue(arcs[cur, j]):
                    tour.append(j)
                    cur = j
                    break
            if cur == 0:
                break
        print("tour:", tour, "length:", int(solver.ObjectiveValue()))
    else:
        print("no tour")

if __name__ == "__main__":
    solve_tsp()
```

**Kotlin (`apps/kotlin/ch06-globals/Tsp.kt`) — requires `circuit` in `cpsat-kt`:**

```kotlin
import io.vanja.cpsat.*

private val DIST = arrayOf(
    intArrayOf(  0, 29, 20, 21, 16, 31, 100, 12),
    intArrayOf( 29,  0, 15, 29, 28, 40,  72, 21),
    intArrayOf( 20, 15,  0, 15, 14, 25,  81,  9),
    intArrayOf( 21, 29, 15,  0,  4, 12,  92, 12),
    intArrayOf( 16, 28, 14,  4,  0, 16,  94,  9),
    intArrayOf( 31, 40, 25, 12, 16,  0,  95, 24),
    intArrayOf(100, 72, 81, 92, 94, 95,   0, 90),
    intArrayOf( 12, 21,  9, 12,  9, 24,  90,  0),
)

fun main() {
    val n = DIST.size
    val arcMap = mutableMapOf<Pair<Int, Int>, BoolVar>()

    val model = cpModel {
        for (i in 0 until n) for (j in 0 until n) {
            if (i == j) continue
            arcMap[i to j] = boolVar("arc_${i}_$j")
        }
        circuit(arcMap.map { (ij, b) -> Arc(ij.first, ij.second, b) })
        minimize {
            weightedSum(
                arcMap.values.toList(),
                arcMap.keys.map { (i, j) -> DIST[i][j].toLong() },
            )
        }
    }

    when (val result = model.solveBlocking { logSearchProgress = true }) {
        is SolveResult.Optimal, is SolveResult.Feasible -> {
            val assignment = (result as? SolveResult.Optimal)?.values
                ?: (result as SolveResult.Feasible).values
            val tour = mutableListOf(0)
            var cur = 0
            while (true) {
                val next = (0 until n).first { j ->
                    j != cur && assignment[arcMap[cur to j]!!]
                }
                tour += next
                cur = next
                if (cur == 0) break
            }
            println("tour: $tour")
        }
        else -> println("no tour")
    }
}
```

**DSL gap.** `cpsat-kt` v0.1 doesn't have `circuit` yet. Add it:

```kotlin
// libs/cpsat-kt/src/main/kotlin/io/vanja/cpsat/Constraints.kt
data class Arc(val from: Int, val to: Int, val literal: BoolVar)

fun CpModel.circuit(arcs: Iterable<Arc>) {
    val builder = javaModel.addCircuit()
    for (a in arcs) builder.addArc(a.from, a.to, a.literal.toJava())
}
```

*Why `Circuit` vs MTZ?* The Miller–Tucker–Zemlin sub-tour elimination uses extra integer variables and O(n²) linear constraints. `Circuit` has a custom graph-based propagator that prunes much earlier. On this 8-city instance both solve in milliseconds, but on 30+ cities you'll feel the difference — MTZ often 10–100× slower.

### 6.2 `Table` — allowed nurse-skill assignments

A nurse can be assigned to a ward only if `(nurse, ward, skill_level)` is in a policy table. Eight nurses, five wards, three skill levels.

**Python (`apps/python/ch06-globals/table_skills.py`):**

```python
from ortools.sat.python import cp_model

# (nurse_id, ward_id, minimum_skill)
ALLOWED = [
    (0, 0, 1), (0, 1, 1), (0, 2, 2),
    (1, 1, 1), (1, 2, 1), (1, 3, 3),
    (2, 0, 2), (2, 2, 3), (2, 4, 2),
    # ...
]

def solve():
    model = cp_model.CpModel()
    nurse = model.NewIntVar(0, 7, "nurse")
    ward = model.NewIntVar(0, 4, "ward")
    skill = model.NewIntVar(1, 3, "skill")

    model.AddAllowedAssignments([nurse, ward, skill], ALLOWED)
    model.Maximize(skill)   # pick highest-skill allowed row

    solver = cp_model.CpSolver()
    status = solver.Solve(model)
    if status == cp_model.OPTIMAL:
        print(f"nurse={solver.Value(nurse)} ward={solver.Value(ward)} skill={solver.Value(skill)}")
```

**Kotlin — after adding `table` to `cpsat-kt`:**

```kotlin
// libs/cpsat-kt/src/main/kotlin/io/vanja/cpsat/Constraints.kt
fun CpModel.table(vars: List<IntVar>, tuples: List<LongArray>) {
    javaModel.addAllowedAssignments(vars.map { it.toJava() }.toTypedArray())
        .apply { tuples.forEach { addTuple(it) } }
}
```

```kotlin
val allowed = listOf(
    longArrayOf(0, 0, 1), longArrayOf(0, 1, 1), longArrayOf(0, 2, 2),
    longArrayOf(1, 1, 1), longArrayOf(1, 2, 1), longArrayOf(1, 3, 3),
    longArrayOf(2, 0, 2), longArrayOf(2, 2, 3), longArrayOf(2, 4, 2),
)

val result = cpModel {
    val nurse = intVar("nurse", 0..7)
    val ward = intVar("ward", 0..4)
    val skill = intVar("skill", 1..3)
    table(listOf(nurse, ward, skill), allowed)
    maximize { skill }
}.solve()
```

Use `Table` when the allowed relation is too complex to express cleanly as a set of linear/boolean constraints, but small enough to enumerate. It's fast on tens of thousands of tuples with well-chosen variable ordering. Beyond that, you're better off with logical or channeling constraints.

### 6.3 `Element` — variable index into a cost array

Pick an item from `items` by index; the cost depends on the index. Useful for "choose one of N discrete options" where each option has known data.

**Python:**

```python
from ortools.sat.python import cp_model

COSTS = [7, 3, 12, 1, 8, 5, 9]

model = cp_model.CpModel()
idx = model.NewIntVar(0, len(COSTS) - 1, "idx")
cost = model.NewIntVar(0, max(COSTS), "cost")
model.AddElement(idx, COSTS, cost)
model.Minimize(cost)

solver = cp_model.CpSolver()
solver.Solve(model)
print(f"idx={solver.Value(idx)}, cost={solver.Value(cost)}")
```

**Kotlin — after adding `element` to `cpsat-kt`:**

```kotlin
// libs/cpsat-kt/src/main/kotlin/io/vanja/cpsat/Constraints.kt
fun CpModel.element(index: IntVar, values: LongArray, target: IntVar) {
    javaModel.addElement(index.toJava(), values, target.toJava())
}
```

```kotlin
val costs = longArrayOf(7, 3, 12, 1, 8, 5, 9)
cpModel {
    val idx = intVar("idx", costs.indices)
    val cost = intVar("cost", 0L..costs.max())
    element(idx, costs, cost)
    minimize { cost }
}.solve()
```

`Element` becomes essential when the array is itself variable — `AddElement(idx, arrayOfIntVars, target)`. That generalization can't be modeled with a simple expression; you need the constraint.

### 6.4 `Automaton` — no more than 3 consecutive night shifts

A nurse's 14-day schedule is a sequence of symbols in `{D=0, E=1, N=2, O=3}` (Day, Evening, Night, Off). Constraint: **no more than 3 consecutive `N`s**.

Build a DFA that counts consecutive Ns (0, 1, 2, 3, fail). Accept any string that never reaches the fail state.

```
states Q = {0, 1, 2, 3, BAD}
start q0 = 0
final F = {0, 1, 2, 3}
transitions δ:
    for s in {D, E, O}: δ(q, s) = 0 for q ∈ {0..3}
    δ(0, N) = 1
    δ(1, N) = 2
    δ(2, N) = 3
    δ(3, N) = BAD
```

Since BAD has no transitions back out, any run entering BAD is rejected — enforcing the rule.

**Python:**

```python
from ortools.sat.python import cp_model

D, E, N_, O = 0, 1, 2, 3
DAYS = 14

model = cp_model.CpModel()
schedule = [model.NewIntVar(0, 3, f"day_{d}") for d in range(DAYS)]

# (state, label, next_state)
transitions = []
for state in range(4):
    for label in (D, E, O):
        transitions.append((state, label, 0))
transitions += [(0, N_, 1), (1, N_, 2), (2, N_, 3)]
# no transition from state 3 on label N -> implicit reject

model.AddAutomaton(schedule, 0, [0, 1, 2, 3], transitions)
solver = cp_model.CpSolver()
solver.parameters.enumerate_all_solutions = False
solver.Solve(model)
print([solver.Value(v) for v in schedule])
```

**Kotlin — after adding `automaton` to `cpsat-kt`:**

```kotlin
// libs/cpsat-kt/src/main/kotlin/io/vanja/cpsat/Constraints.kt
data class Transition(val from: Long, val label: Long, val to: Long)

fun CpModel.automaton(
    vars: List<IntVar>,
    startState: Long,
    finalStates: LongArray,
    transitions: List<Transition>,
) {
    val c = javaModel.addAutomaton(
        vars.map { it.toJava() }.toTypedArray(),
        startState,
        finalStates,
    )
    for (t in transitions) c.addTransition(t.from, t.to, t.label)
}
```

```kotlin
cpModel {
    val days = 14
    val D = 0L; val E = 1L; val N = 2L; val O = 3L
    val sched = List(days) { d -> intVar("day_$d", 0..3) }

    val transitions = buildList {
        for (state in 0L..3L) {
            for (label in listOf(D, E, O)) add(Transition(state, label, 0))
        }
        add(Transition(0, N, 1))
        add(Transition(1, N, 2))
        add(Transition(2, N, 3))
        // no transition from 3 on N -> reject
    }
    automaton(sched, startState = 0, finalStates = longArrayOf(0, 1, 2, 3),
              transitions = transitions)
}.solve()
```

*Why this matters for NSP.* Sliding-window rules like "at most 3 of every 4 days working" or "between nights and morning at least 11 hours rest" encode beautifully as automata. Chapter 10 comes back to this.

### 6.5 `Inverse` — tasks ↔ nurses bijection

Forward: `assigned_nurse[task] = which nurse does task`. Inverse: `assigned_task[nurse] = which task the nurse does`. Both are permutations. Enforcing both *with the right wiring* gives the solver stronger propagation than one alone.

```python
from ortools.sat.python import cp_model
model = cp_model.CpModel()
N = 5
nurse_of_task = [model.NewIntVar(0, N - 1, f"nurse_of_{t}") for t in range(N)]
task_of_nurse = [model.NewIntVar(0, N - 1, f"task_of_{n}") for n in range(N)]
model.AddInverse(nurse_of_task, task_of_nurse)
```

```kotlin
// libs/cpsat-kt/src/main/kotlin/io/vanja/cpsat/Constraints.kt
fun CpModel.inverse(f: List<IntVar>, g: List<IntVar>) {
    require(f.size == g.size) { "inverse: arrays must have same length" }
    javaModel.addInverse(
        f.map { it.toJava() }.toTypedArray(),
        g.map { it.toJava() }.toTypedArray(),
    )
}
```

### 6.6 `LexicographicLessEqual` — symmetry breaking

Five identical nurses on the same schedule are interchangeable — every feasible solution has `5! = 120` trivially-symmetric twins. To collapse the symmetry, force their schedule arrays into lexicographic order.

```python
for n in range(N - 1):
    model.AddLexicographicLessEqual(schedule[n], schedule[n + 1])
```

```kotlin
// libs/cpsat-kt/src/main/kotlin/io/vanja/cpsat/Constraints.kt
fun CpModel.lexLeq(a: List<IntVar>, b: List<IntVar>) {
    require(a.size == b.size) { "lexLeq: arrays must have same length" }
    javaModel.addLexicographicLessEqual(
        a.map { it.toJava() }.toTypedArray(),
        b.map { it.toJava() }.toTypedArray(),
    )
}
```

*Why it matters.* Without lex ordering the solver explores all 120 permutations as distinct search tree nodes. With lex ordering it explores one. For larger groups (say 20 interchangeable nurses) the speedup is exponential.

### 6.7 `Reservoir` — fluid-level constraints

A reservoir holds supplies that deposits add and withdrawals subtract. The level must stay in `[min, max]` at all times. In CP-SAT: each event has a time and a (positive or negative) delta.

```python
from ortools.sat.python import cp_model
model = cp_model.CpModel()
events = [
    (model.NewIntVar(0, 20, "t0"),  5),   # deposit 5
    (model.NewIntVar(0, 20, "t1"), -3),   # withdraw 3
    (model.NewIntVar(0, 20, "t2"),  4),
    (model.NewIntVar(0, 20, "t3"), -6),
]
times = [t for t, _ in events]
deltas = [d for _, d in events]
active = [model.NewConstant(1) for _ in events]
model.AddReservoirConstraint(times, deltas, active, min_level=0, max_level=10)
```

```kotlin
// libs/cpsat-kt/src/main/kotlin/io/vanja/cpsat/Constraints.kt
fun CpModel.reservoir(
    times: List<IntVar>,
    deltas: LongArray,
    actives: List<BoolVar>,
    minLevel: Long,
    maxLevel: Long,
) {
    val c = javaModel.addReservoirConstraint(minLevel, maxLevel)
    require(times.size == deltas.size && times.size == actives.size)
    times.forEachIndexed { i, t ->
        c.addEvent(t.toJava(), deltas[i], actives[i].toJava())
    }
}
```

`Reservoir` is useful for time-indexed resources: battery charge, cache occupancy, skill-accreditation quotas over a horizon, weekend-off allowances. In NSP it helps model "accumulated fatigue" with a recharge during off-days.

## 7. Extending `cpsat-kt`

After this chapter, your `Constraints.kt` looks like:

```kotlin
package io.vanja.cpsat

// ---- from previous chapters ----
fun CpModel.allDifferent(vars: Iterable<IntVar>) { /* ... */ }
fun CpModel.allDifferent(exprs: Iterable<LinearExpr>) { /* ... */ }  // ch 4
fun CpModel.exactlyOne(bools: Iterable<BoolVar>) { /* ... */ }
fun CpModel.atMostOne(bools: Iterable<BoolVar>) { /* ... */ }
fun CpModel.atLeastOne(bools: Iterable<BoolVar>) { /* ... */ }

// ---- new in ch 6 ----
data class Arc(val from: Int, val to: Int, val literal: BoolVar)
fun CpModel.circuit(arcs: Iterable<Arc>) { /* ... */ }

fun CpModel.table(vars: List<IntVar>, tuples: List<LongArray>) { /* ... */ }

fun CpModel.element(index: IntVar, values: LongArray, target: IntVar) { /* ... */ }
fun CpModel.element(index: IntVar, values: List<IntVar>, target: IntVar) {
    javaModel.addElement(
        index.toJava(),
        values.map { it.toJava() }.toTypedArray(),
        target.toJava(),
    )
}

data class Transition(val from: Long, val label: Long, val to: Long)
fun CpModel.automaton(
    vars: List<IntVar>,
    startState: Long,
    finalStates: LongArray,
    transitions: List<Transition>,
) { /* ... */ }

fun CpModel.inverse(f: List<IntVar>, g: List<IntVar>) { /* ... */ }

fun CpModel.lexLeq(a: List<IntVar>, b: List<IntVar>) { /* ... */ }

fun CpModel.reservoir(
    times: List<IntVar>,
    deltas: LongArray,
    actives: List<BoolVar>,
    minLevel: Long,
    maxLevel: Long,
) { /* ... */ }
```

And matching unit tests in `libs/cpsat-kt/src/test/kotlin/io/vanja/cpsat/ConstraintsSpec.kt` plus a parity test per global (run the same model via raw Java API and via the DSL; assert identical `SolveResult`). That's ~7 new specs added.

**TDD loop:**

1. Write a failing test for the new constraint (parity + one unit test for happy path).
2. Implement the DSL extension in `Constraints.kt`.
3. Green.
4. Commit with message `cpsat-kt: add circuit/table/element/automaton/inverse/lex/reservoir`.
5. Move to next.

## 8. Comparison & takeaways

| Aspect | Python | Kotlin via `cpsat-kt` |
|---|---|---|
| `Circuit` | `model.AddCircuit([(i,j,arc),...])` | `circuit(arcs)` with `Arc(i, j, bool)` |
| `Table` | `model.AddAllowedAssignments(vars, tuples)` | `table(vars, tuples)` |
| `Element` | `model.AddElement(idx, values, target)` | `element(idx, values, target)` |
| `Automaton` | `model.AddAutomaton(vars, q0, F, transitions)` | `automaton(...)` with `Transition` data class |
| `Inverse` | `model.AddInverse(f, g)` | `inverse(f, g)` |
| `LexLeq` | `model.AddLexicographicLessEqual(a, b)` | `lexLeq(a, b)` |
| `Reservoir` | `model.AddReservoirConstraint(times, deltas, actives, lo, hi)` | `reservoir(times, deltas, actives, lo, hi)` |

**Key insight.** *A global constraint is always at least as tight as its decomposition, and usually much tighter.* If you find yourself writing a manual decomposition of a pattern that has a global (e.g. "at most 3 consecutive Ns" via pairwise booleans), stop and use the global. The solver will thank you with orders of magnitude less search.

**Secondary insight.** *Adding a constraint to `cpsat-kt` is cheap — ten minutes per global if you follow the TDD recipe.* Don't hand-roll ad-hoc wrappers in chapter apps; invest the minute to add it to the library with a test. That's the whole point of the composite build.

**Tertiary insight.** *`Automaton` is the most under-appreciated global in CP.* Most scheduling rules — "max N in a row", "pattern A must precede B", forbidden subsequences — are automata. Thinking in automata unlocks cleaner models.

## 9. Exercises

### Exercise 6.1 — TSP: MTZ vs Circuit

**Problem.** Encode the same 8-city instance with Miller–Tucker–Zemlin sub-tour elimination (a classic LP-style formulation) and compare solve times to the `Circuit` version.

**Expected output:** both find the same optimal tour; MTZ is slower (or at least comparable on 8 cities; you'll see the gap widen if you push to 20 cities).

**Acceptance criteria:** table of (instance size, method, seconds, nodes explored); one-sentence interpretation.

<details><summary>Hint</summary>

MTZ: one `IntVar` `u[i]` per city (excluding the depot) in `[1, n-1]`; for every directed edge `(i,j)` with `i≠0, j≠0`: `u[i] - u[j] + n * arc[i,j] <= n - 1`. This forbids sub-tours by assigning a strictly increasing position to each city along the tour.

</details>

### Exercise 6.2 — Automaton for "≤ 2 consecutive nights"

**Problem.** Modify the DFA in §6.4 to forbid more than 2 consecutive Ns (instead of 3). Solve the 14-day model and print one feasible schedule.

**Expected output:** a 14-symbol string in `{D, E, N, O}` with no NN-N... sub-sequence.

**Acceptance criteria:** manually verify no run of 3+ N exists; automated with `assert` in code.

<details><summary>Hint</summary>

Drop the `(2, N, 3)` transition. Now max 2 consecutive Ns; the 3rd N has nowhere to go.

</details>

### Exercise 6.3 — Automaton for "exactly 2 weekends off in 4 weeks"

**Problem.** 28 days, labels `{WORK=0, OFF=1}`. Weekends are days `5, 6, 12, 13, 19, 20, 26, 27`. Count how many of those 8 weekend-days are `OFF`; require exactly 2 weekends (= 4 weekend-days, if both days count together) are OFF.

**Hint.** Easier to decompose this one: count OFF booleans over weekend indices and constrain the sum. `Automaton` alone is awkward here because the alphabet isn't rich enough — you'd need to label each day with its position type (weekend vs weekday), then an automaton per-day-type. Try the sum approach; use the exercise to notice that *Automaton is powerful but not always the right tool*.

<details><summary>Hint</summary>

```python
weekend_days = [5, 6, 12, 13, 19, 20, 26, 27]
weekend_off = [model.NewBoolVar(f"off_w_{d}") for d in weekend_days]
# link each bool to schedule[d] == OFF
for i, d in enumerate(weekend_days):
    model.Add(schedule[d] == OFF).OnlyEnforceIf(weekend_off[i])
    model.Add(schedule[d] != OFF).OnlyEnforceIf(weekend_off[i].Not())
# exactly 2 weekends both off: each weekend is 2 days, so require 4 weekend-days off
# AND those 4 must pair: (d=5 AND d=6) OR (d=12 AND d=13) OR ...
# simplification: exactly 2 pairs of (both days of the weekend off)
```

</details>

### Exercise 6.4 — Element with variable values array

**Problem.** Instead of a constant array of costs, make the array also variable (each cost `c[i]` is an `IntVar` in `[0, 100]`). Use `Element(idx, vars, target)`. Add the constraint that `sum(c) == 200`. Minimize `target`.

**Expected output:** minimum possible target, subject to all cost variables summing to 200 and the chosen index producing `target`.

**Acceptance criteria:** both languages agree on the answer.

### Exercise 6.5 — Lex breaks symmetry: proof via count

**Problem.** 5 interchangeable nurses, 7 days each in `{0=off, 1=work}`, at least 3 total work days per nurse. Enumerate all solutions (a) without lex, (b) with lex. Count.

**Expected output:** without lex: count of feasible schedules including all 5! permutations of equivalent assignments. With lex: count divided by 120.

**Acceptance criteria:** ratio close to 120. (Exact ratio depends on how many solutions have stabilizers; often exactly 120.)

<details><summary>Hint</summary>

Use `solver.parameters.enumerate_all_solutions = True` and a callback that just `count += 1`. Remember: enumerate mode is incompatible with a nonzero objective — this problem is a pure CSP, so that's fine.

</details>

### Exercise 6.6 — Reservoir for weekend-off budget

**Problem.** Model "each nurse has ≤ 2 weekends off per 4-week block" as a reservoir: each weekend-off is a +1 event, each start-of-block is a -2 event (withdrawing the budget), level must stay in [0, 2].

**Acceptance criteria:** solution doesn't exceed 2 weekend-offs per rolling 4-week window starting at each block boundary.

<details><summary>Hint</summary>

This is over-complicated for `Reservoir` — in production you'd use sliding-window sums. The exercise is to *see* the awkwardness and *remember* that `Reservoir`'s sweet spot is a single continuous resource (tank, battery) rather than bucketed allowances. Good to learn what a tool is *not* for.

</details>

Solutions live in `apps/*/ch06-globals/solutions/`. Try first; peek if stuck.

## 10. Self-check

**Q1.** Why is `Circuit` faster than MTZ for TSP?

<details><summary>Answer</summary>

`Circuit` has a dedicated graph-based propagator that reasons about sub-tours directly — it can detect and forbid a partial sub-tour immediately, without waiting for the LP to kick in. MTZ is an LP relaxation: its propagation goes through the linear relaxation, which is weaker (the LP optimum of the MTZ formulation has a notoriously loose bound). On small instances the difference is marginal; on 30+ cities it's often 10–100× (and on 50+ cities, MTZ can be hours while Circuit is seconds).

</details>

**Q2.** What's `Automaton` doing under the hood?

<details><summary>Answer</summary>

It maintains, at each position `i` in the variable sequence, the set of DFA states reachable from the start by reading the first `i-1` variables. When a variable's domain changes, it updates the reachable-state sets forward and backward (via the transition function and its inverse) and prunes any value that would lead to a state from which no final state is reachable. This is *arc consistency* on the automaton — very strong propagation, equivalent to the `regular` global constraint.

</details>

**Q3.** When prefer `Table` over inline logic?

<details><summary>Answer</summary>

When the allowed relation is (a) irregular enough that linear/boolean constraints would be clunky (no nice closed form), and (b) small enough to enumerate (thousands to hundreds of thousands of tuples, not millions). Also when the set is data-driven — e.g. a policy in a CSV that changes per deployment. `Table` with CP-SAT's native propagator is one of the most efficient known ways to enforce arbitrary finite relations.

</details>

**Q4.** What's the difference between `Element(idx, values, target)` and `target == values[idx]` written in Python?

<details><summary>Answer</summary>

In Python, `values[idx]` with `idx` being a Python int would just look up the value — but `idx` is an `IntVar`, so the `[]` operator doesn't know what to do. You need `AddElement` (or its DSL equivalent) to say "`target` equals whichever cell `idx` picks". This is the whole reason `Element` exists as a global: variable-index array lookup isn't expressible as a single linear expression.

</details>

**Q5.** A schedule has 20 interchangeable nurses. You add `AddLexicographicLessEqual` on consecutive pairs. How much does the search tree shrink (order of magnitude)?

<details><summary>Answer</summary>

By a factor of `20!` (about `2.4 × 10^18`) in the worst case — every feasible solution has `20!` permutation-equivalent twins, and lex ordering collapses them into one. In practice the gain is smaller because many solutions have non-trivial stabilizers (two nurses with identical schedules aren't distinguishable even before lex), but the direction is clear: *lex symmetry breaking is the single most impactful modeling trick when you have groups of interchangeable entities*.

</details>

## 11. What this unlocks

You now have `cpsat-kt` at ~80% feature parity with CP-SAT — enough to express any constraint the NSP will need. The only missing pieces are scheduling primitives (`IntervalVar`, `NoOverlap`, `Cumulative`) which arrive in Chapter 9.

Next up, Chapter 7 takes a detour into **MiniZinc** — the solver-agnostic modeling language. You'll re-solve N-Queens, knapsack, and SEND+MORE declaratively and compare against CP-SAT on two different backends.

## 12. Further reading

- van Hoeve & Katriel, [*Global Constraints*](https://www.andrew.cmu.edu/user/vanhoeve/papers/global_constraints_chapter.pdf) — the canonical handbook chapter; ~60 pages.
- OR-Tools docs, [*Reference: CpModel*](https://developers.google.com/optimization/reference/python/sat/python/cp_model) — all the `Add*` constraint methods.
- Pesant, [*A Regular Language Membership Constraint for Finite Sequences of Variables*](https://www.cs.princeton.edu/courses/archive/fall06/cos597D/papers/pesant.pdf) — the original `Regular` / `Automaton` paper.
- Beldiceanu, [*Global Constraint Catalog*](https://sofdem.github.io/gccat/) — 300+ globals with formal definitions, propagators, applications.
- [`docs/knowledge/cp-sat/overview.md`](../knowledge/cp-sat/overview.md), §§ global constraints — our own summary of which globals exist and when to use each.
