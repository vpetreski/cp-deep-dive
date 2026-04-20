# CP-SAT: Deep Overview

A foundational reference for learning Google OR-Tools' CP-SAT solver in both Python and Kotlin/Java. Targets OR-Tools **v9.15.6755** (released 2026-01-12 / PyPI 2026-01-14), the current stable as of April 2026.

---

## 1. What CP-SAT Is

**CP-SAT** is the flagship constraint-programming solver inside Google's OR-Tools suite. It's a **hybrid solver** that fuses three traditions inside one engine:

- **Constraint Programming (CP)** — rich global constraints (AllDifferent, Circuit, NoOverlap, Cumulative, Automaton, Reservoir) and domain-based propagation.
- **SAT / CDCL** — a modern conflict-driven clause-learning Boolean satisfiability core that performs **Lazy Clause Generation (LCG)**: CP propagators post *explanations* as clauses to the SAT core on demand, giving CP-like modeling with SAT-like learning and nogoods.
- **LP / MIP** — a (dual) simplex, linear relaxations of the model, cutting planes, branch-and-cut, and integration with dedicated MIP-style heuristics.

On top of that, CP-SAT is a **parallel portfolio solver**. Each worker runs a different strategy (different linearization level, different branching, LNS, feasibility pump, relaxation-induced neighborhood search, local search such as `ViolationLS` from CPAIOR 2024, etc.) and they share bounds and solutions over a shared information store. This portfolio is the reason CP-SAT has won the **MiniZinc Challenge** gold medal every year since 2018.

Key distinctions from a classical MIP solver:
- Works over **integers and Booleans only** (reals must be scaled to integers).
- All variables are **bounded** — no unbounded integers.
- It's branded "CP-SAT-LP" internally; Perron & Didier's 2023 paper is titled "The CP-SAT-LP Solver" (LIPIcs CP 2023, vol. 280).

Positioning statement: *CP-SAT is the solver you reach for when your problem has combinatorial structure (scheduling, assignment, routing, packing, rostering) but is too large or too integer-heavy for a pure CP solver, and too discrete or non-linear for a pure MIP solver.*

Sources: <https://developers.google.com/optimization/cp/cp_solver>, <https://d-krupke.github.io/cpsat-primer/07_under_the_hood.html>, <https://drops.dagstuhl.de/storage/00lipics/lipics-vol280-cp2023/LIPIcs.CP.2023.3/LIPIcs.CP.2023.3.pdf>.

---

## 2. Version & Installation (April 2026)

**Latest OR-Tools release:** **v9.15.6755**, tagged 2026-01-12. Highlights: Python 3.14 support, improvements to `no_overlap_2d` (presolve, propagation, cuts), XPressMP added in MathOpt, bumped deps (Protobuf 33.1, HiGHS 1.12.0, SCIP 10.0.0). See <https://github.com/google/or-tools/releases/tag/v9.15>.

### Python

```bash
pip install ortools          # latest
pip install "ortools==9.15.6755"
```

- Requires **Python ≥ 3.9**; wheels ship for 3.9 – 3.14 on Linux (manylinux), macOS (x64 + arm64), Windows x64.
- Import path: `from ortools.sat.python import cp_model`.
- PyPI: <https://pypi.org/project/ortools/>.

### Java (and Kotlin, via the Java bindings)

Maven:

```xml
<dependency>
  <groupId>com.google.ortools</groupId>
  <artifactId>ortools-java</artifactId>
  <version>9.15.6755</version>
</dependency>
```

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("com.google.ortools:ortools-java:9.15.6755")
}
```

- Artifact coordinate: <https://central.sonatype.com/artifact/com.google.ortools/ortools-java/9.15.6755>.
- Minimum JDK: **Java 11** (9.15 builds target JDK 11 bytecode; native runtime libs bundled per-platform in the jar).
- Kotlin: **no official Kotlin bindings exist**. You use the Java classes directly; Kotlin just calls them. Version 1.9+ works fine. (Optional) write thin extension functions / a small DSL to get idiomatic Kotlin. No maintained `ortools-kotlin` package.

### Sanity-check install

```python
# Python
from ortools.sat.python import cp_model
print(cp_model.CpSolver().solver_version())
```

```java
// Java
import com.google.ortools.Loader;
public class Check {
  public static void main(String[] a) {
    Loader.loadNativeLibraries();              // MUST be called once before use
    System.out.println(new com.google.ortools.sat.CpSolver());
  }
}
```

```kotlin
// Kotlin
import com.google.ortools.Loader
import com.google.ortools.sat.CpSolver

fun main() {
    Loader.loadNativeLibraries()
    println(CpSolver())
}
```

---

## 3. Core API Concepts

CP-SAT uses a **model-builder** pattern. You never manipulate the solver's internal state directly — you describe the problem into a `CpModel`, then hand it to a `CpSolver`. Under the hood, `CpModel` is a thin typed wrapper over the `CpModelProto` protobuf; this is why the model is freely *cloneable, serializable, and inspectable*.

| Concept | Python | Java / Kotlin |
|---|---|---|
| Problem container | `cp_model.CpModel()` | `new CpModel()` |
| Solver | `cp_model.CpSolver()` | `new CpSolver()` |
| Integer variable | `model.new_int_var(lb, ub, name)` | `model.newIntVar(lb, ub, name)` |
| Integer var from domain | `model.new_int_var_from_domain(Domain.from_values([1,3,5]), "x")` | `model.newIntVarFromDomain(Domain.fromValues(new long[]{1,3,5}), "x")` |
| Boolean variable | `model.new_bool_var("b")` | `model.newBoolVar("b")` |
| Constant | `model.new_constant(42)` | `model.newConstant(42)` |
| Interval (fixed duration) | `model.new_fixed_size_interval_var(start, 5, "i")` | `model.newFixedSizeIntervalVar(start, 5, "i")` |
| Interval (variable) | `model.new_interval_var(start, size, end, "i")` | `model.newIntervalVar(start, size, end, "i")` |
| Optional interval | `model.new_optional_interval_var(start, size, end, present, "i")` | `model.newOptionalIntervalVar(start, size, end, present, "i")` |
| Linear expression | `2*x + 3*y` (operator-overloaded) | `LinearExpr.weightedSum(new IntVar[]{x,y}, new long[]{2,3})` |
| Negation of a literal | `~b` or `b.Not()` | `b.not()` |

Python exploits operator overloading heavily: `model.add(2*x + 3*y <= 10)` Just Works. Java has no operator overloading, so you always build a `LinearExpr` (via `LinearExpr.newBuilder()`, `LinearExpr.sum(...)`, `LinearExpr.weightedSum(...)`, `LinearExpr.term(x, 2)`, `LinearExpr.affine(x, 2, 3)`) and call `addLessOrEqual`, `addEquality`, `addGreaterOrEqual`.

**Side-by-side: a trivial model.**

```python
from ortools.sat.python import cp_model

model = cp_model.CpModel()
x = model.new_int_var(0, 50, "x")
y = model.new_int_var(0, 50, "y")
z = model.new_int_var(0, 50, "z")
model.add(2*x + 7*y + 3*z <= 50)
model.add(3*x - 5*y + 7*z <= 45)
model.add(5*x + 2*y - 6*z <= 37)
model.maximize(2*x + 2*y + 3*z)

solver = cp_model.CpSolver()
status = solver.solve(model)
if status in (cp_model.OPTIMAL, cp_model.FEASIBLE):
    print(solver.objective_value, solver.value(x), solver.value(y), solver.value(z))
```

```java
import com.google.ortools.Loader;
import com.google.ortools.sat.*;

public class Example {
  public static void main(String[] args) {
    Loader.loadNativeLibraries();
    CpModel model = new CpModel();
    IntVar x = model.newIntVar(0, 50, "x");
    IntVar y = model.newIntVar(0, 50, "y");
    IntVar z = model.newIntVar(0, 50, "z");
    IntVar[] vars = {x, y, z};
    model.addLessOrEqual(LinearExpr.weightedSum(vars, new long[]{2, 7, 3}), 50);
    model.addLessOrEqual(LinearExpr.weightedSum(vars, new long[]{3, -5, 7}), 45);
    model.addLessOrEqual(LinearExpr.weightedSum(vars, new long[]{5, 2, -6}), 37);
    model.maximize(LinearExpr.weightedSum(vars, new long[]{2, 2, 3}));

    CpSolver solver = new CpSolver();
    CpSolverStatus status = solver.solve(model);
    if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
      System.out.printf("obj=%f x=%d y=%d z=%d%n",
          solver.objectiveValue(), solver.value(x), solver.value(y), solver.value(z));
    }
  }
}
```

Source: <https://developers.google.com/optimization/cp/cp_example>.

---

## 4. Constraints Reference

Every constraint is a method on `CpModel` returning a `Constraint` object. Capturing that object lets you attach `OnlyEnforceIf(literal)` to make it a **half-reified** constraint (enforced only when the literal is true).

| Category | Python (`CpModel.*`) | Java (`CpModel.*`) | What it does |
|---|---|---|---|
| Linear | `add(expr <= v)`, `add(expr == v)`, `add(expr >= v)`, `add_linear_constraint(expr, lb, ub)` | `addLessOrEqual`, `addEquality`, `addGreaterOrEqual`, `addLinearConstraint` | General linear constraint; integer-linear only |
| Not equal | `add(x != y)` / `add_different` | `addDifferent` | Disequality |
| All-different | `add_all_different(xs)` | `addAllDifferent(xs)` | All values distinct |
| Element | `add_element(index, values, target)` | `addElement(index, values, target)` | `target == values[index]` |
| Inverse | `add_inverse(xs, ys)` | `addInverse(xs, ys)` | `ys[xs[i]] == i` |
| Boolean OR | `add_bool_or([lits])` | `addBoolOr(lits)` | ≥1 literal true |
| Boolean AND | `add_bool_and([lits])` | `addBoolAnd(lits)` | All literals true |
| Boolean XOR | `add_bool_xor([lits])` | `addBoolXor(lits)` | Odd count true |
| Exactly N | `add_exactly_one`, `add_at_most_one`, `add_at_least_one` | `addExactlyOne`, `addAtMostOne`, `addAtLeastOne` | Cardinality shortcuts |
| Implication | `add_implication(a, b)` | `addImplication(a, b)` | `a ⇒ b` |
| Min / Max | `add_min_equality(t, xs)`, `add_max_equality(t, xs)` | `addMinEquality`, `addMaxEquality` | `t = min/max(xs)` |
| Abs | `add_abs_equality(t, x)` | `addAbsEquality(t, x)` | `t = |x|` |
| Mul | `add_multiplication_equality(t, [x,y,...])` | `addMultiplicationEquality` | `t = ∏xᵢ` |
| Div | `add_division_equality(t, x, y)` | `addDivisionEquality` | Integer division |
| Mod | `add_modulo_equality(t, x, y)` | `addModuloEquality` | `t = x mod y` |
| Circuit | `add_circuit([(src,dst,lit), ...])` | `addCircuit` | Hamiltonian circuit with optional arcs |
| Multiple circuit | `add_multiple_circuit([...])` | `addMultipleCircuit` | Multiple routes from depot 0 (VRP) |
| Automaton | `add_automaton(transition_vars, start, finals, transitions)` | `addAutomaton` | DFA constraint over a sequence |
| NoOverlap (1D) | `add_no_overlap([intervals])` | `addNoOverlap` | Disjoint intervals |
| NoOverlap2D | `add_no_overlap_2d([x_intervals], [y_intervals])` | `addNoOverlap2D` | Non-overlapping rectangles (bin packing, 2D cutting) |
| Cumulative | `add_cumulative(intervals, demands, capacity)` | `addCumulative` | Σ demands(active) ≤ capacity at all t |
| Reservoir | `add_reservoir_constraint(times, level_changes, lo, hi)` + `_with_active` variant | `addReservoirConstraint` | Level stays in [lo,hi] |
| Lex | `add_lex_less`, `add_lex_less_or_equal` (Python helpers) | via reification | Lexicographic order on sequences |
| Assumptions | `add_assumption`, `add_assumptions` | `addAssumption`, `addAssumptions` | Marker literals for infeasibility cores |

Reification example — "buy is active only if budget > 100":

```python
buy = model.new_bool_var("buy")
budget = model.new_int_var(0, 1000, "budget")
model.add(budget > 100).only_enforce_if(buy)
model.add(budget <= 100).only_enforce_if(~buy)
```

```java
BoolVar buy = model.newBoolVar("buy");
IntVar budget = model.newIntVar(0, 1000, "budget");
model.addGreaterThan(budget, 100).onlyEnforceIf(buy);
model.addLessOrEqual(budget, 100).onlyEnforceIf(buy.not());
```

Sources: <https://or-tools.github.io/docs/pdoc/ortools/sat/python/cp_model.html>, <https://github.com/google/or-tools/blob/stable/ortools/sat/docs/boolean_logic.md>.

---

## 5. Objective

`CpModel` supports exactly one objective, set with:

| | Python | Java |
|---|---|---|
| Minimize | `model.minimize(expr)` | `model.minimize(expr)` |
| Maximize | `model.maximize(expr)` | `model.maximize(expr)` |

Both take any `LinearExpr`-convertible thing. In Python, `model.minimize(sum(coeff[i]*x[i] for i in ...))` is the idiomatic form; in Java, build a `LinearExpr.weightedSum(...)` or `LinearExpr.sum(...)`.

### Lexicographic / hierarchical objectives

CP-SAT has no built-in lex-objective; the standard pattern is **solve, fix, re-solve**:

```python
# 1) Minimize the primary objective.
model.minimize(primary)
solver.solve(model)
best_primary = int(solver.objective_value)

# 2) Lock primary and minimize the secondary.
model.add(primary == best_primary)
model.minimize(secondary)      # replaces the previous objective
solver.solve(model)
```

Or: weighted sum with a *carefully chosen* large multiplier on the primary so that any improvement on the primary dominates. Weighted-sum is simpler; the solve-fix-re-solve pattern is safer when the ranges are unknown.

A close alternative in v9.13+ is the `objective_shaving_search_level` parameter for assisted multi-objective shaping. For true Pareto front enumeration, script it yourself.

---

## 6. Solving

```python
solver = cp_model.CpSolver()
solver.parameters.max_time_in_seconds = 30.0
solver.parameters.num_search_workers = 8
solver.parameters.log_search_progress = True
status = solver.solve(model)
```

```java
CpSolver solver = new CpSolver();
solver.getParameters().setMaxTimeInSeconds(30.0);
solver.getParameters().setNumSearchWorkers(8);
solver.getParameters().setLogSearchProgress(true);
CpSolverStatus status = solver.solve(model);
```

### Status codes

| Value | Meaning |
|---|---|
| `OPTIMAL` | Proved optimal (or proved feasible and no objective). |
| `FEASIBLE` | A feasible solution exists but optimality not proved (hit a limit). |
| `INFEASIBLE` | Proved no solution exists. |
| `MODEL_INVALID` | Validation failed. Call `model.validate()` (Python) / `model.validate()` (Java) for the reason. |
| `UNKNOWN` | Nothing proved, no feasible solution found within limits. |

### Reading results

- Python: `solver.value(x)`, `solver.boolean_value(b)`, `solver.objective_value`, `solver.best_objective_bound`, `solver.wall_time`, `solver.num_branches`, `solver.num_conflicts`, `solver.response_stats()`.
- Java: `solver.value(x)`, `solver.booleanValue(b)`, `solver.objectiveValue()`, `solver.bestObjectiveBound()`, `solver.wallTime()`, `solver.responseStats()`.

### Callbacks (every intermediate solution)

```python
class Printer(cp_model.CpSolverSolutionCallback):
    def __init__(self, vars):
        super().__init__()
        self._vars = vars
        self._count = 0
    def on_solution_callback(self):
        self._count += 1
        print(self._count, [self.value(v) for v in self._vars],
              "obj=", self.objective_value)

solver.parameters.enumerate_all_solutions = True     # for feasibility problems
solver.solve(model, Printer([x, y, z]))
```

```java
class Printer extends CpSolverSolutionCallback {
  private int count = 0;
  private final IntVar[] vars;
  Printer(IntVar[] vars) { this.vars = vars; }
  @Override public void onSolutionCallback() {
    count++;
    for (IntVar v : vars) System.out.print(value(v) + " ");
    System.out.println("obj=" + objectiveValue());
  }
}
solver.getParameters().setEnumerateAllSolutions(true);
solver.solve(model, new Printer(new IntVar[]{x, y, z}));
```

### Key parameters

| Parameter | Default | When to set |
|---|---|---|
| `max_time_in_seconds` | ∞ | Always, in production. |
| `num_search_workers` | ~8 / #cores | Set to physical cores; 1 for deterministic single-thread. |
| `log_search_progress` | `false` | Turn on while developing — the log is essential. |
| `log_callback` (Python) / `log_callback` / `log_to_stdout` | stdout | Redirect log to logger/Jupyter. |
| `relative_gap_limit` | 0 | Stop within X% of optimum (e.g. 0.05 = 5%). |
| `absolute_gap_limit` | 0 | Absolute variant. |
| `enumerate_all_solutions` | `false` | Feasibility enumeration (no objective). |
| `random_seed` | 1 | Reproducibility under a fixed `num_workers=1`. |
| `cp_model_presolve` | `true` | Disable only to debug presolve. |
| `fill_tightened_domains_in_response` | `false` | Inspect after-presolve domains. |

Source: <https://github.com/google/or-tools/blob/stable/ortools/sat/sat_parameters.proto>.

---

## 7. Python vs Java / Kotlin

### Naming & ergonomics

| Aspect | Python | Java / Kotlin |
|---|---|---|
| Naming convention | `snake_case` (`new_int_var`, `add_all_different`) | `camelCase` (`newIntVar`, `addAllDifferent`) |
| Linear expressions | Operator overloading (`2*x + 3*y <= 5`) | `LinearExpr.weightedSum`, `LinearExpr.sum`, `LinearExpr.newBuilder()` |
| Negation | `~lit` or `lit.Not()` | `lit.not()` |
| Accessing values | `solver.value(x)`, `solver.boolean_value(b)` | `solver.value(x)`, `solver.booleanValue(b)` |
| Solution callback | subclass `CpSolverSolutionCallback`, override `on_solution_callback` | same class name, override `onSolutionCallback` |
| Setup | `import` and go | **Must call `Loader.loadNativeLibraries()` once** before any OR-Tools use, or you get `UnsatisfiedLinkError`. |
| Parameters | `solver.parameters.max_time_in_seconds = 10` | `solver.getParameters().setMaxTimeInSeconds(10)` (uses the `SatParameters` protobuf builder) |
| Variable arrays | Python lists of `IntVar` | `IntVar[]` arrays; helpers take arrays, not `List<IntVar>` |

### Sharp edges in Java

- Everything is a protobuf underneath, so parameter access is `solver.getParameters().setX(...)`.
- `LinearExpr` objects are immutable — use `LinearExprBuilder` (`LinearExpr.newBuilder()`) to build up sums conditionally.
- `IntVar` is not a number — you can't do `x + 1`. Wrap via `LinearExpr.affine(x, 1, 1)` or a builder.
- `BoolVar` extends `IntVar` but has `.not()` returning a `Literal`. Most `addBoolOr` / `addImplication` etc. take `Literal[]`, not `BoolVar[]` — at the call site you usually just pass bool vars, but negations require `.not()`.

### Kotlin idioms (unofficial)

There are no published Kotlin bindings; you use `ortools-java` as a plain dependency. Common patterns teams adopt:

- Tiny operator overloads for `LinearExpr`:
  ```kotlin
  import com.google.ortools.sat.IntVar
  import com.google.ortools.sat.LinearExpr

  operator fun IntVar.times(c: Long): LinearExpr = LinearExpr.term(this, c)
  operator fun IntVar.plus(o: IntVar): LinearExpr =
      LinearExpr.newBuilder().add(this).add(o).build()
  ```
- `inline fun CpModel.intVar(range: LongRange, name: String) = newIntVar(range.first, range.last, name)` — Kotlin-friendly factories.
- A small `fun cpModel(block: CpModel.() -> Unit): CpModel` DSL wrapper.
- Use `solver.parameters { ... }` extension:
  ```kotlin
  inline fun CpSolver.parameters(block: SatParameters.Builder.() -> Unit) {
      val b = parameters.toBuilder().apply(block).build()
      // reflection/reassign if needed, or use getParameters().setXxx inline
  }
  ```

Kotlin users often just accept Java-style verbosity for CP-SAT code — it stays close to docs and examples on developers.google.com.

---

## 8. Scheduling Primitives (Deep Dive)

Scheduling is CP-SAT's sharpest edge. The core abstraction is the **interval variable**: a triple `(start, size, end)` with the invariant `start + size == end`. All three are `IntVar` (or integer expressions) with their own domains.

### Four flavors of IntervalVar

| Flavor | Python | Java |
|---|---|---|
| Fixed-size | `new_fixed_size_interval_var(start, size, name)` | `newFixedSizeIntervalVar(start, size, name)` |
| Variable size | `new_interval_var(start, size, end, name)` | `newIntervalVar(start, size, end, name)` |
| Optional fixed-size | `new_optional_fixed_size_interval_var(start, size, present, name)` | `newOptionalFixedSizeIntervalVar` |
| Optional variable | `new_optional_interval_var(start, size, end, present, name)` | `newOptionalIntervalVar` |

The optional variants take a `present` BoolVar. When `present == 0`, `NoOverlap`/`Cumulative` silently ignore the interval; if you want precedence or start-time equalities to still matter, you must explicitly gate them with `only_enforce_if(present)`.

### NoOverlap — the 1D disjunctive resource

```python
machines = {m: [] for m in all_machines}
for job in jobs:
    for task in job.tasks:
        iv = model.new_interval_var(task.start, task.duration, task.end, f"t{task.id}")
        machines[task.machine].append(iv)
for m, ivs in machines.items():
    model.add_no_overlap(ivs)
```

### Cumulative — capacity-bounded resource

```python
# N technicians, task i requires demand[i] technicians for duration[i]
model.add_cumulative(intervals, demands, capacity=num_technicians)
```

`demands` can be integer constants or `IntVar` expressions.

### NoOverlap2D — 2D packing (bins, cutting stock, floor plans)

```python
model.add_no_overlap_2d(x_intervals, y_intervals)
```

Significantly improved presolve/cuts in v9.14–9.15.

### Reservoir — inflows / outflows bounded

```python
model.add_reservoir_constraint(times, level_changes, min_level, max_level)
```

Used for inventory, battery state-of-charge, staffing minima.

### Precedence & makespan

Precedence between non-optional intervals is just a linear constraint between end/start expressions:

```python
model.add(task_a.end_expr() <= task_b.start_expr())
```

For optional intervals, wrap in `only_enforce_if(present_a, present_b)`.

Makespan (minimize the latest end):

```python
makespan = model.new_int_var(0, horizon, "makespan")
model.add_max_equality(makespan, [t.end for t in last_tasks_per_job])
model.minimize(makespan)
```

### Job-shop skeleton (Python)

```python
from ortools.sat.python import cp_model
model = cp_model.CpModel()
horizon = sum(d for job in jobs_data for _, d in job)
tasks = {}
machine_to_ivs = collections.defaultdict(list)
for j, job in enumerate(jobs_data):
    for k, (machine, dur) in enumerate(job):
        s = model.new_int_var(0, horizon, f"s_{j}_{k}")
        e = model.new_int_var(0, horizon, f"e_{j}_{k}")
        iv = model.new_interval_var(s, dur, e, f"iv_{j}_{k}")
        tasks[(j, k)] = (s, e, iv)
        machine_to_ivs[machine].append(iv)
for m, ivs in machine_to_ivs.items():
    model.add_no_overlap(ivs)
for j, job in enumerate(jobs_data):
    for k in range(1, len(job)):
        model.add(tasks[(j, k)][0] >= tasks[(j, k-1)][1])     # precedence
makespan = model.new_int_var(0, horizon, "makespan")
model.add_max_equality(makespan, [tasks[(j, len(jobs_data[j])-1)][1] for j in range(len(jobs_data))])
model.minimize(makespan)
```

### Employee scheduling — a Boolean grid

Shift scheduling avoids intervals in the textbook formulation and uses a 3-D Boolean array `shifts[n, d, s]`:

```python
shifts = {(n, d, s): model.new_bool_var(f"s_{n}_{d}_{s}")
          for n in nurses for d in days for s in shifts_per_day}

# Each shift is covered by exactly one nurse:
for d in days:
    for s in shifts_per_day:
        model.add_exactly_one(shifts[n, d, s] for n in nurses)

# Each nurse works at most one shift per day:
for n in nurses:
    for d in days:
        model.add_at_most_one(shifts[n, d, s] for s in shifts_per_day)
```

Sources: <https://github.com/google/or-tools/blob/stable/ortools/sat/docs/scheduling.md>, <https://developers.google.com/optimization/scheduling/job_shop>, <https://developers.google.com/optimization/scheduling/employee_scheduling>.

---

## 9. Search & Parameters — What to Tune and When

CP-SAT's portfolio means the solver makes most decisions for you. **The best lever is almost always the model, not the parameters.** That said, the handful that matter:

| Parameter | What it does | When to touch |
|---|---|---|
| `num_search_workers` | Parallel workers in the portfolio. | Match physical cores on dedicated machines; `=1` for determinism/bench. On laptops, hyperthreading often *hurts* — set to physical cores. |
| `linearization_level` | 0/1/2 — how aggressively linear relaxations are built. `1` = default. | Try `2` for highly-constrained LP-like problems (cumulative-heavy). Try `0` for pure-boolean or SAT-like models. |
| `search_branching` | Selection heuristic: `AUTOMATIC_SEARCH`, `FIXED_SEARCH` (uses decision strategies), `PORTFOLIO_SEARCH`, `LP_SEARCH`, `PSEUDO_COST_SEARCH`, `PORTFOLIO_WITH_QUICK_RESTART_SEARCH`, `HINT_SEARCH`. | Use `FIXED_SEARCH` *only* when combined with `model.add_decision_strategy(...)` — otherwise leave default. |
| `preferred_variable_order` | `IN_ORDER`, `IN_REVERSE_ORDER`, `IN_RANDOM_ORDER` — default variable ordering fallback. | Rarely. |
| `log_search_progress` | Detailed portfolio + presolve log. | Always during development — the **log itself** tells you what bound is tight, what presolve collapsed, which worker is dominating. Pair with [Krupke's CP-SAT Log Analyzer](https://github.com/d-krupke/CP-SAT-Log-Analyzer). |
| `relative_gap_limit` / `absolute_gap_limit` | Stop when within X% / δ of the best bound. | Production — you almost never need *proven* optimal. |
| `cp_model_presolve` | Presolve on/off. | Off only for debugging. |
| `use_lns_only` / `repair_hint` | LNS-only or hint-driven modes. | Warm-start workflows / anytime-solver settings. |

`sat_parameters.proto` lists 300+ knobs — most are internal. <https://github.com/google/or-tools/blob/stable/ortools/sat/sat_parameters.proto>.

**Tuning order of operations** (after Krupke):
1. Get `log_search_progress=True` output. Read it.
2. Tighten variable domains in the model.
3. Add redundant constraints that cut search (symmetry breaking, implied sums).
4. Provide hints (`add_hint`) from a heuristic solution.
5. Set time + gap limits.
6. *Only then* touch `linearization_level`, `num_search_workers`.

---

## 10. Best Practices & Gotchas

### Keep bounds tight

Every `IntVar` must be bounded. Don't use `[0, 1_000_000_000]` "to be safe" — the solver doesn't automatically tighten bounds across a weak model, and wide bounds blow up propagators and the linear relaxation. If makespan can't exceed the sum of durations, that sum is the bound.

### Prefer global constraints over Boolean decompositions

- `add_all_different(xs)` beats `O(n²)` pairwise `add(xs[i] != xs[j])`.
- `add_circuit(arcs)` beats a homegrown successor model.
- `add_no_overlap(intervals)` beats pairwise disjunction Booleans.

Globals carry filtering algorithms and feed cuts into the LP relaxation; decompositions don't.

### Use BoolVars as reification carriers

```python
c = model.add(x + y == 10).only_enforce_if(b)
```
is **half-reified**: `b ⇒ (x+y=10)` but not the converse. For full reification (`b ⇔ ...`), post both directions with `~b`:

```python
model.add(x + y == 10).only_enforce_if(b)
model.add(x + y != 10).only_enforce_if(~b)
```

### Watch out for optional intervals

Constraints between optional intervals need to be reified by the presence literals, or they'll force absence:

```python
# WRONG if both optional:
model.add(a.end_expr() <= b.start_expr())
# RIGHT:
model.add(a.end_expr() <= b.start_expr()).only_enforce_if([a_present, b_present])
```

### Break symmetry

If two workers (or two bins, two machines) are interchangeable, the solver will waste time exploring isomorphic assignments. Break symmetry explicitly:

```python
# Lex-order workers by their first assigned task id.
model.add(first_task_of[worker_0] <= first_task_of[worker_1])
```

`add_lex_less_or_equal` helps for sequences.

### Warm start with hints

If you have a heuristic or a previous solution, feed it as a hint. CP-SAT runs `HINT_SEARCH` workers that repair and extend hints:

```python
model.add_hint(x, 3)
model.add_hint(y, 5)
```

```java
model.addHint(x, 3);
model.addHint(y, 5);
```

Combine with `solver.parameters.repair_hint = True` (Python) for infeasible but near-feasible hints.

### Scale reals to integers

CP-SAT is integer-only. Multiply all coefficients by 100 / 1000 / whatever precision you need, then divide objectives back down when reporting.

### Validate and inspect

- `model.validate()` returns a non-empty string if the model is malformed.
- `model.export_to_file("model.pb.txt")` dumps the protobuf (human-readable) for sharing with Perron on the OR-Tools discussion group.
- `solver.sufficient_assumptions_for_infeasibility()` returns a minimal infeasible subset when you use assumption literals — invaluable for debugging "why no solution".

### Determinism

CP-SAT is deterministic **per seed + workers combination**. `num_search_workers = 1` + fixed `random_seed` gives fully reproducible runs. Parallel runs are deterministic if `random_seed` is fixed but can have different wall times.

---

## 11. Resources

### Official

- Landing page: <https://developers.google.com/optimization>
- Intro & install: <https://developers.google.com/optimization/introduction>
- CP overview: <https://developers.google.com/optimization/cp>
- CP-SAT solver: <https://developers.google.com/optimization/cp/cp_solver>
- CP-SAT first example: <https://developers.google.com/optimization/cp/cp_example>
- N-queens: <https://developers.google.com/optimization/cp/queens>
- Cryptarithmetic: <https://developers.google.com/optimization/cp/cryptarithmetic>
- Solver limits / time / callbacks: <https://developers.google.com/optimization/cp/cp_tasks>
- Scheduling overview: <https://developers.google.com/optimization/scheduling>
- Employee (nurse) scheduling: <https://developers.google.com/optimization/scheduling/employee_scheduling>
- Job-shop: <https://developers.google.com/optimization/scheduling/job_shop>
- GitHub repo: <https://github.com/google/or-tools>
- CP-SAT docs (GitHub, canonical): <https://github.com/google/or-tools/tree/stable/ortools/sat/docs>
  - `boolean_logic.md`, `integer_arithmetic.md`, `channeling.md`, `scheduling.md`, `solver.md`, `troubleshooting.md`
- Python API reference (pdoc): <https://or-tools.github.io/docs/pdoc/ortools/sat/python/cp_model.html>
- Java API reference (Javadoc): <https://or-tools.github.io/docs/java/index.html>
- Samples (per language):
  - Python: <https://github.com/google/or-tools/tree/stable/ortools/sat/samples> (`*_sat.py`)
  - Python domain examples: <https://github.com/google/or-tools/tree/stable/examples/python> (34 `*_sat.py` files incl. `flexible_job_shop_sat.py`, `rcpsp_sat.py`, `shift_scheduling_sat.py`, `bus_driver_scheduling_sat.py`, `cover_rectangle_sat.py`)
  - Java: <https://github.com/google/or-tools/tree/stable/examples/java> (plus per-sample `*Sat.java` files under `ortools/sat/samples`)
  - **No Kotlin samples** — Kotlin consumers use the Java jar unchanged.
- Parameters (authoritative): <https://github.com/google/or-tools/blob/stable/ortools/sat/sat_parameters.proto>
- Releases: <https://github.com/google/or-tools/releases>
- PyPI: <https://pypi.org/project/ortools/>
- Maven Central: <https://central.sonatype.com/artifact/com.google.ortools/ortools-java>

### Community / tutorial

- **Dominik Krupke's CP-SAT Primer** — the definitive third-party primer: <https://d-krupke.github.io/cpsat-primer/> (repo: <https://github.com/d-krupke/cpsat-primer>)
- Krupke's **CP-SAT Log Analyzer** — visualize `log_search_progress` output: <https://github.com/d-krupke/CP-SAT-Log-Analyzer>
- OR-Tools discussion group (mailing list / Google Group): <https://groups.google.com/g/or-tools-discuss> — Laurent Perron and Frédéric Didier answer directly.
- Stack Overflow tag: `or-tools`, `cp-sat`.

### Talks and papers by the maintainers

- **"CP-SAT: A Python solver for constraint programming"** — Laurent Perron, CPAIOR / scheduling-seminar slides: <https://schedulingseminar.com/presentations/SchedulingSeminar_LaurentPerron.pdf>
- **"The CP-SAT-LP Solver"** — Perron, Didier, Gay, CP 2023 (LIPIcs vol. 280): <https://drops.dagstuhl.de/storage/00lipics/lipics-vol280-cp2023/LIPIcs.CP.2023.3/LIPIcs.CP.2023.3.pdf>
- **"ViolationLS: Constraint-Based Local Search in CP-SAT"** — Davies, Didier, Perron, CPAIOR 2024 (Springer LNCS 14742): <https://link.springer.com/chapter/10.1007/978-3-031-60597-0_16>
- Scheduling Seminar talk by Perron: <https://www.youtube.com/watch?v=vvUxusrUcpU>
- Perron's CMU EWO slide deck "OR-Tools / CP-SAT": <https://egon.cheme.cmu.edu/ewo/docs/CP-SAT%20and%20OR-Tools.pdf>
- Perron on Google Scholar: <https://scholar.google.com/citations?user=umrglaIAAAAJ>
- MiniZinc Challenge results (CP-SAT has won every year since 2018): <https://www.minizinc.org/challenge/>

### Recommended reading order for a newcomer

1. The intro + first example on developers.google.com.
2. Krupke's primer parts 1–5 (installation → parameters).
3. The GitHub `ortools/sat/docs` pages on boolean logic, channeling, scheduling.
4. One complete example in your target language from `ortools/sat/samples` (e.g. `nurses_sat.py` or `minimal_jobshop_sat.py`).
5. Perron's CP-SAT-LP paper for the mental model of what's happening under the hood.
6. Krupke's primer parts 6–11 (under the hood, advanced modeling, coding patterns, benchmarking).
