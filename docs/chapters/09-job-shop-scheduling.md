---
title: "Chapter 9 — Intervals, NoOverlap, Cumulative (Job-Shop)"
phase: 4
estimated: 3h
status: draft
last_updated: 2026-04-19
---

# Chapter 9 — Intervals, NoOverlap, Cumulative (Job-Shop)

## 1. Goal

Leave puzzles and enter real-world scheduling. This chapter introduces CP-SAT's **interval primitives** — the building blocks for every time-based problem:

- `IntervalVar` — a start, size, and end triple with an implicit `start + size = end` constraint.
- `NoOverlap` — a set of intervals don't overlap (single resource: machine, nurse, room).
- `Cumulative` — a set of intervals consume a shared resource whose total demand never exceeds capacity (multi-capacity resource: crane, parallel CPUs, charging stations).
- `OptionalInterval` — an interval that exists only if its presence literal is true (used for alternatives).

You'll:

1. Model and solve the classic **Job-Shop Scheduling Problem (JSSP)** from the OR-Tools documentation.
2. Extend to a cumulative variant where machines have capacity > 1.
3. Produce a **Gantt chart** visualization (matplotlib in Python, simple SVG in Kotlin).
4. Extend `cpsat-kt` with the interval DSL — `interval { start; size; end }`, `noOverlap`, `cumulative`.

By the end, `cpsat-kt` is feature-complete for NSP. You're ready for Phase 5.

## 2. Before you start

- Chapters 1–8 complete. `cpsat-kt` has all globals from Ch 6 and parity-tested `.mzn → Python → Kotlin` from Ch 8.
- Read [`docs/knowledge/cp-sat/overview.md`](../knowledge/cp-sat/overview.md) scheduling section (IntervalVar, NoOverlap, Cumulative semantics).

## 3. Concepts introduced

- **IntervalVar(start, size, end)** — a decision variable bundle representing a time span. `size` can be constant or variable; `end - start = size` is always enforced.
- **OptionalInterval(start, size, end, is_present)** — an interval that's either "active" or "absent"; when absent, constraints over it are skipped.
- **NoOverlap([intervals])** — at most one interval active at any point in time. Equivalent to a single-capacity `Cumulative` with all demands = 1.
- **Cumulative([intervals], [demands], capacity)** — at every time point, the sum of demands of active-overlapping intervals ≤ capacity.
- **Makespan** — the maximum end time across all intervals. The classic scheduling objective: `minimize max(end_i)`.
- **Job-Shop Scheduling Problem (JSSP)** — n jobs, each a sequence of tasks; each task needs a specific machine; tasks within a job have a precedence order; each machine handles one task at a time. Minimize makespan.
- **Critical path** — a longest chain of consecutive tasks from start to end; reducing its length reduces the makespan lower bound.
- **Disjunctive scheduling** vs **cumulative scheduling** — single-capacity (one at a time) vs multi-capacity (parallel up to K).

## 4. Intuition

A `Cumulative(capacity=1)` and a `NoOverlap` express the same idea — at most one interval active at a time — but CP-SAT uses **different propagators** for each. `NoOverlap` has a specialized "disjunctive" propagator (Vilím 2002 — edge finding, energy reasoning) optimized for unit capacity. `Cumulative` is more general, slightly slower per call, but handles capacity > 1.

Rule: **use `NoOverlap` when capacity is 1**; **use `Cumulative` when capacity > 1**. Don't write `Cumulative` with demands all 1 and capacity 1 — you'd waste the disjunctive propagator.

*Mental model for intervals.* An `IntervalVar` is a 3-tuple `(s, d, e)` where:

- `s` = start time (decision variable)
- `d` = duration / size (decision variable or constant)
- `e` = end time (decision variable)
- Implicit constraint: `s + d = e`

An `OptionalInterval` adds a boolean `p` (presence). When `p = false`, all scheduling constraints over this interval are relaxed — the interval is effectively "not there".

*Why not just use three `IntVar`s + a manual equality?* You can. But `IntervalVar` is the atomic object scheduling propagators recognize. Passing a list of three separate `IntVar`s to `NoOverlap` wouldn't work — it expects intervals specifically, because the propagator reasons about spans, not three independent numbers.

## 5. Formal definitions

**Interval.**

```
IntervalVar(s, d, e) ≡ { (s, d, e) ∈ ℤ³ : s + d = e, d ≥ 0 }
```

**OptionalInterval.**

```
OptionalInterval(s, d, e, p) ≡ { (s, d, e, p) : p ⇒ (s + d = e), p ⇒ (d ≥ 0) }
```

When `p = 0`, the triple can be arbitrary — no constraint.

**NoOverlap.**

```
NoOverlap(I₁, ..., Iₙ) ≡ ∀ i ≠ j : ¬( sᵢ < eⱼ ∧ sⱼ < eᵢ )
```

Equivalently: `eᵢ ≤ sⱼ ∨ eⱼ ≤ sᵢ` for all `i < j`, restricted to pairs of present intervals for optionals.

**Cumulative.**

```
Cumulative(I, demands, C) ≡ ∀ t ∈ ℤ :  Σ { demands[i] : sᵢ ≤ t < eᵢ, pᵢ = 1 } ≤ C
```

## 6. Worked example: Classic Job-Shop

The canonical OR-Tools job-shop instance (3 jobs, 3 machines):

```
job 0 = [(machine=0, dur=3), (machine=1, dur=2), (machine=2, dur=2)]
job 1 = [(machine=0, dur=2), (machine=2, dur=1), (machine=1, dur=4)]
job 2 = [(machine=1, dur=4), (machine=2, dur=3)]
```

Rules:

- Tasks within a job happen in order (precedence).
- Each machine handles one task at a time (NoOverlap per machine).
- Goal: minimize makespan (when the last task finishes).

**Horizon** = sum of all durations = 21 (safe upper bound).

### 6.1 Python (`apps/python/ch09-jobshop/jobshop.py`)

```python
from ortools.sat.python import cp_model
import matplotlib.pyplot as plt

# (machine, duration) per task
JOBS = [
    [(0, 3), (1, 2), (2, 2)],
    [(0, 2), (2, 1), (1, 4)],
    [(1, 4), (2, 3)],
]


def solve_jobshop():
    model = cp_model.CpModel()
    horizon = sum(d for job in JOBS for _, d in job)

    # intervals_per_machine[m] = list of IntervalVars on machine m
    intervals_per_machine = {m: [] for m in range(3)}
    task_vars = {}   # (job, task_idx) -> (start, end, interval)

    for j, job in enumerate(JOBS):
        for t, (machine, dur) in enumerate(job):
            start = model.NewIntVar(0, horizon, f"s_{j}_{t}")
            end = model.NewIntVar(0, horizon, f"e_{j}_{t}")
            interval = model.NewIntervalVar(start, dur, end, f"iv_{j}_{t}")
            task_vars[j, t] = (start, end, interval)
            intervals_per_machine[machine].append(interval)

        # Precedence within a job
        for t in range(len(job) - 1):
            model.Add(task_vars[j, t + 1][0] >= task_vars[j, t][1])

    # One task per machine at a time
    for m in intervals_per_machine:
        model.AddNoOverlap(intervals_per_machine[m])

    # Makespan
    makespan = model.NewIntVar(0, horizon, "makespan")
    model.AddMaxEquality(makespan, [task_vars[j, len(JOBS[j]) - 1][1]
                                     for j in range(len(JOBS))])
    model.Minimize(makespan)

    solver = cp_model.CpSolver()
    solver.parameters.num_search_workers = 8
    status = solver.Solve(model)

    if status != cp_model.OPTIMAL:
        print("not optimal; status =", solver.StatusName(status))
        return

    print(f"makespan = {int(solver.ObjectiveValue())}")
    schedule = []
    for j, job in enumerate(JOBS):
        for t, (machine, dur) in enumerate(job):
            s, e, _ = task_vars[j, t]
            schedule.append((j, t, machine, solver.Value(s), solver.Value(e), dur))
            print(f"  J{j}T{t} machine={machine} start={solver.Value(s)} end={solver.Value(e)}")

    draw_gantt(schedule)


def draw_gantt(schedule):
    fig, ax = plt.subplots(figsize=(10, 4))
    colors = ["tab:blue", "tab:orange", "tab:green"]
    for (j, t, m, s, e, d) in schedule:
        ax.barh(y=m, width=d, left=s, color=colors[j], edgecolor="black")
        ax.text(s + d / 2, m, f"J{j}T{t}", ha="center", va="center", color="white")
    ax.set_yticks(range(3))
    ax.set_yticklabels([f"M{i}" for i in range(3)])
    ax.set_xlabel("time")
    ax.set_title("Job-Shop schedule")
    plt.tight_layout()
    plt.savefig("gantt.png", dpi=150)
    print("wrote gantt.png")


if __name__ == "__main__":
    solve_jobshop()
```

Expected: `makespan = 11` for this instance. The Gantt chart shows three horizontal rows (one per machine), each with colored bars for each task.

### 6.2 Kotlin (`apps/kotlin/ch09-jobshop/src/main/kotlin/JobShop.kt`)

First, extend `cpsat-kt` with the interval DSL. This is the big Ch 9 library add.

**`libs/cpsat-kt/src/main/kotlin/io/vanja/cpsat/Intervals.kt`:**

```kotlin
package io.vanja.cpsat

import com.google.ortools.sat.IntervalVar as JavaInterval

class IntervalVar internal constructor(internal val javaVar: JavaInterval) {
    fun toJava(): JavaInterval = javaVar
    val startExpr: LinearExpr get() = LinearExpr.ofJava(javaVar.startExpr)
    val endExpr: LinearExpr get() = LinearExpr.ofJava(javaVar.endExpr)
    val sizeExpr: LinearExpr get() = LinearExpr.ofJava(javaVar.sizeExpr)
}

class IntervalBuilder internal constructor(private val model: CpModel, private val name: String) {
    var start: IntVar? = null
    var size: Long? = null
    var sizeVar: IntVar? = null
    var end: IntVar? = null
    var isPresent: BoolVar? = null   // null → mandatory

    internal fun build(): IntervalVar {
        val s = start ?: error("interval $name: start is required")
        val e = end ?: error("interval $name: end is required")
        val sz = size
        val szVar = sizeVar
        val java = when {
            szVar != null && isPresent != null ->
                model.javaModel.newOptionalIntervalVar(s.toJava(), szVar.toJava(), e.toJava(), isPresent!!.toJava(), name)
            szVar != null ->
                model.javaModel.newIntervalVar(s.toJava(), szVar.toJava(), e.toJava(), name)
            sz != null && isPresent != null ->
                model.javaModel.newOptionalFixedSizeIntervalVar(s.toJava(), sz, isPresent!!.toJava(), name)
            sz != null ->
                model.javaModel.newFixedSizeIntervalVar(s.toJava(), sz, name)
            else -> error("interval $name: either size (Long) or sizeVar (IntVar) must be set")
        }
        return IntervalVar(java)
    }
}

fun CpModel.interval(name: String, block: IntervalBuilder.() -> Unit): IntervalVar {
    val builder = IntervalBuilder(this, name)
    builder.block()
    return builder.build()
}
```

**`libs/cpsat-kt/src/main/kotlin/io/vanja/cpsat/Constraints.kt` additions:**

```kotlin
fun CpModel.noOverlap(intervals: Iterable<IntervalVar>) {
    javaModel.addNoOverlap(intervals.map { it.toJava() }.toTypedArray())
}

fun CpModel.cumulative(
    intervals: Iterable<IntervalVar>,
    demands: LongArray,
    capacity: Long,
) {
    val intervalArr = intervals.toList()
    require(intervalArr.size == demands.size) {
        "cumulative: intervals and demands must have same size"
    }
    val c = javaModel.addCumulative(capacity)
    intervalArr.forEachIndexed { i, iv ->
        c.addDemand(iv.toJava(), demands[i])
    }
}
```

Now the Kotlin job-shop:

```kotlin
import io.vanja.cpsat.*

private val JOBS: List<List<Pair<Int, Long>>> = listOf(
    listOf(0 to 3L, 1 to 2L, 2 to 2L),
    listOf(0 to 2L, 2 to 1L, 1 to 4L),
    listOf(1 to 4L, 2 to 3L),
)
private const val MACHINES = 3

fun main() {
    val horizon = JOBS.sumOf { job -> job.sumOf { (_, d) -> d } }

    val result = cpModel {
        val intervalsPerMachine = Array(MACHINES) { mutableListOf<IntervalVar>() }
        // task vars indexed by (job, taskIdx)
        val taskStart = Array(JOBS.size) { j -> arrayOfNulls<IntVar>(JOBS[j].size) }
        val taskEnd = Array(JOBS.size) { j -> arrayOfNulls<IntVar>(JOBS[j].size) }

        for ((j, job) in JOBS.withIndex()) {
            for ((t, pair) in job.withIndex()) {
                val (machine, dur) = pair
                val s = intVar("s_${j}_$t", 0..horizon)
                val e = intVar("e_${j}_$t", 0..horizon)
                val iv = interval("iv_${j}_$t") {
                    start = s
                    size = dur
                    end = e
                }
                taskStart[j][t] = s
                taskEnd[j][t] = e
                intervalsPerMachine[machine].add(iv)
            }

            // Precedence within job
            for (t in 0 until job.size - 1) {
                constraint { taskStart[j][t + 1]!! ge taskEnd[j][t]!! }
            }
        }

        // NoOverlap per machine
        for (m in 0 until MACHINES) {
            noOverlap(intervalsPerMachine[m])
        }

        // Makespan
        val makespan = intVar("makespan", 0..horizon)
        for (j in JOBS.indices) {
            val lastEnd = taskEnd[j][JOBS[j].size - 1]!!
            constraint { makespan ge lastEnd }
        }
        minimize { makespan }
    }.solveBlocking { numSearchWorkers = 8 }

    when (result) {
        is SolveResult.Optimal -> println("makespan = ${result.objective}")
        is SolveResult.Feasible -> println("feasible but not proven optimal; obj=${result.objective}")
        else -> println("no solution: $result")
    }
}
```

*Note.* `cpsat-kt` v0.1 didn't expose `makespan = max(...)` directly. Instead of `AddMaxEquality`, we used the equivalent formulation `makespan >= lastEnd_j` for all j, combined with `minimize`: the solver drives makespan down until it equals the max. These are mathematically equivalent when you're minimizing makespan.

If you want explicit `max`, add this helper to `cpsat-kt`:

```kotlin
// libs/cpsat-kt/src/main/kotlin/io/vanja/cpsat/Constraints.kt
fun CpModel.maxOf(target: IntVar, exprs: Iterable<IntVar>) {
    javaModel.addMaxEquality(target.toJava(), exprs.map { it.toJava() }.toTypedArray())
}
```

Then: `maxOf(makespan, (0 until JOBS.size).map { j -> taskEnd[j][JOBS[j].size - 1]!! })`.

### 6.3 Cumulative variant: machines with parallelism

Imagine machine 0 can handle **two** tasks in parallel (capacity = 2); machines 1, 2 are single-capacity. Same jobs, same durations; only the resource semantics change.

**Python:**

```python
# Replace NoOverlap on machine 0 with Cumulative(capacity=2, demand=1 per interval)
# machines 1, 2 stay NoOverlap

demands_m0 = [1] * len(intervals_per_machine[0])
model.AddCumulative(intervals_per_machine[0], demands_m0, 2)
model.AddNoOverlap(intervals_per_machine[1])
model.AddNoOverlap(intervals_per_machine[2])
```

**Kotlin:**

```kotlin
cumulative(intervalsPerMachine[0],
           demands = LongArray(intervalsPerMachine[0].size) { 1 },
           capacity = 2)
noOverlap(intervalsPerMachine[1])
noOverlap(intervalsPerMachine[2])
```

Makespan drops (machine 0 can pipeline). For this tiny instance it drops by ~1-2 units; in production JSSP it can be dramatic.

### 6.4 Gantt visualization — Kotlin SVG

A no-dependency SVG writer for the schedule:

```kotlin
fun writeGantt(schedule: List<Task>, path: String) {
    // Task: (job, taskIdx, machine, start, end)
    val cellHeight = 40
    val scale = 30
    val height = MACHINES * cellHeight + 40
    val width = schedule.maxOf { it.end * scale + 60 }.toInt()
    val palette = listOf("#4c72b0", "#dd8452", "#55a467", "#c44e52", "#8172b2")

    val svg = buildString {
        append("""<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height">""")
        for (m in 0 until MACHINES) {
            val y = m * cellHeight + 20
            append("""<text x="5" y="${y + cellHeight / 2}" font-size="14">M$m</text>""")
        }
        for (task in schedule) {
            val x = task.start * scale + 30
            val w = (task.end - task.start) * scale
            val y = task.machine * cellHeight + 25
            val fill = palette[task.job % palette.size]
            append("""<rect x="$x" y="$y" width="$w" height="${cellHeight - 10}" fill="$fill" stroke="black"/>""")
            append("""<text x="${x + w / 2}" y="${y + cellHeight / 2}" text-anchor="middle" font-size="12" fill="white">J${task.job}T${task.taskIdx}</text>""")
        }
        append("</svg>")
    }
    java.io.File(path).writeText(svg)
    println("wrote $path")
}
```

Opens in any browser. Not pretty, but dependency-free and reproducible in any Kotlin project.

## 7. MiniZinc version (`apps/mzn/ch09-jobshop/jobshop.mzn`)

```minizinc
include "cumulative.mzn";
include "disjunctive.mzn";

int: n_jobs;
int: n_machines;
array[1..n_jobs] of int: n_tasks;
int: max_tasks = max(n_tasks);
array[1..n_jobs, 1..max_tasks] of int: machine;   % 0-indexed machine; use -1 for padding
array[1..n_jobs, 1..max_tasks] of int: duration;

int: horizon = sum(j in 1..n_jobs)(sum(t in 1..n_tasks[j])(duration[j, t]));

array[1..n_jobs, 1..max_tasks] of var 0..horizon: start;
array[1..n_jobs, 1..max_tasks] of var 0..horizon: endv;

constraint forall(j in 1..n_jobs, t in 1..n_tasks[j])
  (endv[j, t] = start[j, t] + duration[j, t]);

% Precedence
constraint forall(j in 1..n_jobs, t in 1..n_tasks[j] - 1)
  (start[j, t+1] >= endv[j, t]);

% NoOverlap per machine
constraint forall(m in 0..n_machines-1)
  (disjunctive(
    [start[j, t] | j in 1..n_jobs, t in 1..n_tasks[j] where machine[j, t] = m],
    [duration[j, t] | j in 1..n_jobs, t in 1..n_tasks[j] where machine[j, t] = m]
  ));

var 0..horizon: makespan;
constraint forall(j in 1..n_jobs)(makespan >= endv[j, n_tasks[j]]);

solve minimize makespan;

output ["makespan = ", show(makespan), "\n"];
```

With `jobshop.dzn`:

```minizinc
n_jobs = 3;
n_machines = 3;
n_tasks = [3, 3, 2];
machine = array2d(1..3, 1..3, [
    0, 1, 2,
    0, 2, 1,
    1, 2, -1,
]);
duration = array2d(1..3, 1..3, [
    3, 2, 2,
    2, 1, 4,
    4, 3, 0,
]);
```

Run:

```bash
minizinc --solver com.google.or-tools jobshop.mzn jobshop.dzn
minizinc --solver chuffed              jobshop.mzn jobshop.dzn
```

Chuffed is often a strong performer on JSSP — try both and compare.

## 8. Comparison & takeaways

| Aspect | Python CP-SAT | Kotlin `cpsat-kt` | MiniZinc |
|---|---|---|---|
| Interval creation | `NewIntervalVar(s, d, e, name)` | `interval(name){ start; size; end }` | implicit via `start + duration = end` |
| Optional intervals | `NewOptionalIntervalVar(...)` | `interval{ start; size; end; isPresent = ... }` | `exists` and `forall ... where present` patterns |
| NoOverlap | `AddNoOverlap([intervals])` | `noOverlap(intervals)` | `disjunctive(starts, durations)` |
| Cumulative | `AddCumulative([iv], [demand], cap)` | `cumulative(intervals, demands, capacity)` | `cumulative(starts, durations, resources, cap)` |
| Makespan | `AddMaxEquality(mk, [ends])` then minimize | `maxOf(mk, ends)` + `minimize{ mk }` | `makespan >= endv[j, last]; solve minimize makespan` |
| Gantt plot | matplotlib `barh` | handwritten SVG | none built-in (use MiniZincIDE's viewer or export) |

**Key insight.** *NoOverlap is to Cumulative what AllDifferent is to pairwise !=.* Both pairs describe the same idea (single-resource; different-values), but the dedicated propagator in the first version is orders of magnitude stronger. Always prefer `NoOverlap` on capacity=1 resources.

**Secondary insight.** *Makespan minimization finds one optimum with many equivalent schedules.* Your Python and Kotlin runs may produce different schedules with the same `makespan = 11`. That's correct — just wire a secondary lexicographic criterion (e.g. sum of start times) if you need stability.

**Tertiary insight.** *Intervals are the atomic object scheduling propagators reason about.* Don't try to fake them with three IntVars + manual precedence. The propagator needs the "this is an interval" type tag to know which specialized reasoning to run.

## 9. Exercises

### Exercise 9.1 — Add a 4th job

**Problem.** Add `job 3 = [(2, 2), (0, 3), (1, 1)]` to the JSSP instance. Re-solve. Compare new makespan.

**Expected output:** makespan increases (more work; bottleneck shifts).

**Acceptance criteria:** Python and Kotlin agree on the new makespan.

### Exercise 9.2 — Critical path extraction

**Problem.** After solving, identify the *critical path* — the chain of tasks whose sum of durations equals the makespan. Print each critical task.

**Expected output:** a list like `J0T0 → J0T1 → J1T2 → ...` whose total duration = makespan.

**Acceptance criteria:** sum of durations along the printed path = makespan; each consecutive pair is either a precedence (same job) or a shared-machine sequence (no idle time in between).

<details><summary>Hint</summary>

Iterate over tasks with zero slack: `slack(task) = latest_start(task) - earliest_start(task)`, where both are computed by fixing makespan and running two more tiny solves. Tasks with 0 slack are on a critical path. (In CP-SAT you can also use `solver.ObjectiveBound()` tricks, but the two-extra-solves approach is simpler.)

</details>

### Exercise 9.3 — Cumulative with variable demand

**Problem.** Modify the Python and Kotlin JSSP so each task consumes a random demand in `{1, 2, 3}` on machine 0 (capacity = 5). Demand is fixed per task (not a decision variable). Keep machines 1, 2 as single-capacity. Compare makespan to the uniform-demand-1 version.

**Expected output:** makespan changes (usually up — higher demands queue more).

**Acceptance criteria:** reproducible (fixed RNG seed for demand generation).

### Exercise 9.4 — OptionalInterval: machine alternatives

**Problem.** Suppose task J0T1 can run on machine 1 OR machine 3 (new machine). Model as two `OptionalInterval`s (one per alternative) with a presence literal each, and exactly-one-present constraint. Re-solve.

**Expected output:** the solver picks the machine that minimizes makespan (if machine 3 is free, it probably uses it).

**Acceptance criteria:** exactly one of the two alternatives is active; makespan ≤ original.

<details><summary>Hint</summary>

```python
p_m1 = model.NewBoolVar("j0t1_on_m1")
p_m3 = model.NewBoolVar("j0t1_on_m3")
model.Add(p_m1 + p_m3 == 1)
iv_m1 = model.NewOptionalIntervalVar(s, 2, e, p_m1, "iv_j0t1_m1")
iv_m3 = model.NewOptionalIntervalVar(s, 2, e, p_m3, "iv_j0t1_m3")
# add iv_m1 to machine 1's NoOverlap; iv_m3 to machine 3's NoOverlap
```

Precedence uses the shared `s` and `e`. Exactly one presence literal will be true; the other interval is "absent".

</details>

### Exercise 9.5 — Parity test against MiniZinc

**Problem.** Wire the MiniZinc `.mzn` from §7 into a pytest that runs all three implementations (Python, Kotlin, MiniZinc CP-SAT) on the classic 3-job instance and asserts makespan parity.

**Acceptance criteria:** test passes with makespan = 11; fails if you break any implementation.

Solutions live in `apps/*/ch09-jobshop/solutions/`. Try first.

## 10. Self-check

**Q1.** Difference between `NoOverlap` and `Cumulative(capacity=1)`?

<details><summary>Answer</summary>

Semantically identical — both mean at most one interval active per time point. Practically different: CP-SAT uses a dedicated *disjunctive propagator* for `NoOverlap` (edge finding, energy reasoning, not-first/not-last rules from Vilím 2002) that is much stronger than the generic `Cumulative` propagator on capacity-1 instances. Always use `NoOverlap` when capacity is exactly 1.

</details>

**Q2.** When do you need `OptionalInterval`?

<details><summary>Answer</summary>

When the interval's existence itself is a decision — e.g. "this task can run on machine A or machine B", so you create two optional intervals with a presence literal each, constrain exactly one to be present, and the scheduling constraints automatically ignore the absent one. Also used for jobs that may be dropped entirely (soft deadlines where you minimize number of dropped jobs). If the task *always* runs, use mandatory `IntervalVar`.

</details>

**Q3.** Minimize makespan vs minimize weighted tardiness — what changes in the model?

<details><summary>Answer</summary>

*Makespan* = max end time. One `IntVar` for makespan, constrain it to dominate all end times, minimize it. *Weighted tardiness* = Σ wⱼ · max(0, endⱼ - dueⱼ). Each job has a due date; tardiness is end minus due if positive else zero (needs `max(0, ·)` — encode via an auxiliary `IntVar(0, horizon)` with two inequalities). The objective becomes a weighted sum, not a max. Qualitatively: makespan cares only about the latest task; tardiness penalizes every late job in proportion to its weight — closer to real-world service-level priorities.

</details>

**Q4.** Why does setting `num_search_workers = 8` speed up JSSP but not always help on smaller problems?

<details><summary>Answer</summary>

CP-SAT runs a *portfolio* of search strategies across workers: some do large-neighborhood-search (LNS), some do plain branch-and-bound, some do LP-relaxation-driven probing, some do feasibility pump. On harder problems (many variables, tight constraints) these strategies find complementary progress; on small problems the fixed setup cost per worker dominates and single-threaded often wins. Rule of thumb: profile both; don't assume more workers = faster.

</details>

**Q5.** You set `NoOverlap` on a list of intervals but the solver still reports `INFEASIBLE` even though you can manually draw a valid schedule. What's the most likely bug?

<details><summary>Answer</summary>

You forgot a precedence constraint, or your horizon is too small for the total work. Double-check: (1) each interval's `start + size = end` is enforced (it is automatically for `IntervalVar`, but not if you tried to use three IntVars + manual equality and forgot one); (2) precedence `start[j,t+1] >= end[j,t]`; (3) the horizon covers the sum of all task durations. A too-tight horizon silently makes feasible instances infeasible — that's the #1 newbie bug.

</details>

**Q6.** In `cpsat-kt`, how does the DSL distinguish between a fixed-size interval and a variable-size interval?

<details><summary>Answer</summary>

The builder has `size: Long?` (fixed, constant) and `sizeVar: IntVar?` (variable). You set one or the other — setting both is an error. The builder picks `newFixedSizeIntervalVar` vs `newIntervalVar` accordingly. For optional intervals, setting `isPresent = boolVar("p")` switches to the `newOptional*` counterparts. The builder's internal `build()` picks the right Java factory based on which fields are set.

</details>

## 11. What this unlocks

`cpsat-kt` is now feature-complete for the NSP. Intervals, global constraints, objectives, streaming, parameters — all wired. Phase 5 builds the full NSP on top.

Chapter 10 shifts to **calendar time and shift transitions** — the specifically NSP-flavored modeling. You'll encode 14-day schedules with 3 shifts/day, minimum rest between shifts, maximum consecutive days worked — the exact rules real hospitals enforce. Much of what you learned about `Automaton` in Ch 6 pays off there.

## 12. Further reading

- Pinedo, [*Scheduling: Theory, Algorithms, and Systems*](https://www.springer.com/gp/book/9783319265780) — the canonical scheduling textbook; chapters 5-7 cover JSSP variants exhaustively.
- Vilím, [*Filtering Algorithms for the Unary Resource Constraint*](https://archive.org/details/vilim-thesis) — edge-finding and the theoretical basis for CP-SAT's disjunctive propagator.
- Baptiste, Le Pape, Nuijten, [*Constraint-based Scheduling*](https://link.springer.com/book/10.1007/978-1-4615-1479-4) — specialized textbook, useful alongside CP-SAT source.
- OR-Tools examples: [`cp_sat_example_minimal_jobshop.py`](https://github.com/google/or-tools/blob/stable/examples/python/flexible_job_shop_sat.py) — the official reference.
- Krupke, [*CP-SAT Primer Part 5: Scheduling*](https://d-krupke.github.io/cpsat-primer/05_scheduling.html) — the best practical guide to intervals and cumulatives in CP-SAT.
- [`docs/knowledge/cp-sat/overview.md`](../knowledge/cp-sat/overview.md) scheduling section — our own distilled notes.
