---
title: "Chapter 8 — MiniZinc prototype, then port"
phase: 3
estimated: 2h
status: draft
last_updated: 2026-04-19
---

# Chapter 8 — MiniZinc prototype, then port

## 1. Goal

Use MiniZinc as a *specification tool*. You will:

1. Write a small **5-nurse × 7-day** Nurse Scheduling Problem in MiniZinc first — no Python, no Kotlin — because the declarative form makes the math obvious and forces you to state every rule before any code.
2. Port that exact model to CP-SAT in Python and Kotlin (`cpsat-kt`).
3. Confirm all three implementations agree on the optimum and the assignment.

By the end, you have a trusted three-way artifact — `.mzn` spec, Python impl, Kotlin impl — for a tiny NSP instance, and the workflow you'll re-use for the real NSP in Phases 4 and 5.

## 2. Before you start

- Chapters 1–7 complete. MiniZinc installed with CP-SAT backend registered (Ch 7 Appendix A).
- `cpsat-kt` has AllDifferent on expressions (Ch 4), `solveFlow` (Ch 5), and all globals including `automaton` (Ch 6).
- Read [`docs/knowledge/nurse-scheduling/overview.md`](../knowledge/nurse-scheduling/overview.md) sections 1–3 (what is NSP, formal model, hard constraints).

## 3. Concepts introduced

- **Prototype-then-port workflow** — design declaratively, implement imperatively.
- **Specification-as-code** — the `.mzn` file is the ground truth; implementations must match.
- **Instance equivalence tests** — given the same inputs, all three implementations produce the same objective and assignment.
- **MiniZinc-Python API** — official Python package for driving MiniZinc models programmatically.
- **Boolean-grid NSP encoding** — `assign[n,d,s] ∈ {0,1}` cube, the simplest NSP model.
- **Demand vector** — `demand[d,s]` = required nurses for day `d` shift `s`.

## 4. Intuition

Why prototype in MiniZinc even if you'll ship in CP-SAT? Three reasons:

1. **The syntax pressure-tests the model.** In MiniZinc you can't hide a missing constraint in Python glue code — every rule must be written down. If it doesn't compile, the spec is incomplete.
2. **The declarative form is the spec.** A business analyst can read `.mzn`. They cannot read `model.add(sum(x) <= 5).OnlyEnforceIf(cond)`. When the NSP's soft constraints start multiplying, the `.mzn` becomes your shared artifact.
3. **Solver portability is a sanity check.** If Gecode and CP-SAT agree on the optimum of your `.mzn`, you've caught most modeling bugs before porting.

*The workflow:*

```
idea
 |
 v
 .mzn draft  (5-10 minutes per constraint)
 |
 v
 run on 2+ solvers; sanity-check
 |
 v
 port to Python CP-SAT  (straight translation)
 |
 v
 port to Kotlin cpsat-kt  (straight translation)
 |
 v
 three-way parity: same obj, same assignment
```

## 5. The tiny NSP (5 nurses × 7 days × 3 shifts)

**Instance.**

- 5 nurses (N₁..N₅)
- 7 days (Mon..Sun)
- 3 shifts per day: Day (D), Evening (E), Night (N)
- Demand: each shift needs exactly 1 nurse per day
- Total slots = 7 × 3 = 21 nurse-shifts
- Total available = 5 × 7 = 35 nurse-days (one shift or off)

**Hard constraints.**

1. **HC1 — Coverage.** Every (day, shift) is staffed by exactly 1 nurse.
2. **HC2 — At most one shift per day.** Each nurse works 0 or 1 shifts per day.
3. **HC3 — No night-then-morning.** If a nurse works night on day `d`, they don't work day shift on day `d+1`.
4. **HC4 — Max 5 working days per week.** Each nurse: `sum of worked days ≤ 5`.

**Soft objective (for now, trivial).**

- Minimize max shifts worked by any nurse (balance workload).

## 6. MiniZinc model

**`apps/mzn/ch08-nsp-tiny/nsp.mzn`:**

```minizinc
include "globals.mzn";

% ---- Parameters (overridable by .dzn) ----
int: N;                                % number of nurses
int: D;                                % number of days
int: S = 3;                            % shifts per day: 0=Day, 1=Eve, 2=Night
array[1..D, 0..S-1] of int: demand;    % required nurses per (day, shift)
int: max_work_days;                    % max working days per nurse

% ---- Decision variables ----
% assign[n, d, s] = 1  iff nurse n works shift s on day d
array[1..N, 1..D, 0..S-1] of var 0..1: assign;

% Helper: whether nurse n works on day d (works some shift)
array[1..N, 1..D] of var 0..1: works;

constraint forall(n in 1..N, d in 1..D)
  (works[n, d] == sum(s in 0..S-1)(assign[n, d, s]));

% ---- HC1 Coverage ----
constraint forall(d in 1..D, s in 0..S-1)
  (sum(n in 1..N)(assign[n, d, s]) == demand[d, s]);

% ---- HC2 At most one shift per day ----
constraint forall(n in 1..N, d in 1..D)
  (works[n, d] <= 1);
% (redundant with the 0..1 domain on assign + works definition, but spells
%  out the intent; keep it.)

% ---- HC3 No day shift after night shift ----
constraint forall(n in 1..N, d in 1..D-1)
  (assign[n, d, 2] + assign[n, d+1, 0] <= 1);

% ---- HC4 Max working days per week ----
constraint forall(n in 1..N)
  (sum(d in 1..D)(works[n, d]) <= max_work_days);

% ---- Objective: minimize max shifts per nurse ----
var int: max_shifts;
constraint forall(n in 1..N)
  (sum(d in 1..D, s in 0..S-1)(assign[n, d, s]) <= max_shifts);

solve minimize max_shifts;

% ---- Output ----
output [
    "max_shifts = ", show(max_shifts), "\n",
    "\nSchedule (rows=nurses, cols=days; D/E/N/_):\n",
]
++ [
    if s == 0 /\ d == 1 then
      "N" ++ show(n) ++ " "
    else ""
    endif
    ++
    if s == 0 /\ fix(assign[n,d,0]) == 1 then "D"
    elseif s == 1 /\ fix(assign[n,d,1]) == 1 then "E"
    elseif s == 2 /\ fix(assign[n,d,2]) == 1 then "N"
    elseif s == 2 /\ fix(works[n,d]) == 0 then "_"
    else ""
    endif
    ++
    if s == 2 /\ d == D then "\n" else "" endif
  | n in 1..N, d in 1..D, s in 0..S-1
];
```

**`apps/mzn/ch08-nsp-tiny/nsp.dzn`:**

```minizinc
N = 5;
D = 7;
demand = array2d(1..7, 0..2, [
    1,1,1,
    1,1,1,
    1,1,1,
    1,1,1,
    1,1,1,
    1,1,1,
    1,1,1,
]);
max_work_days = 5;
```

Run:

```bash
minizinc --solver com.google.or-tools nsp.mzn nsp.dzn
minizinc --solver gecode              nsp.mzn nsp.dzn
minizinc --solver chuffed             nsp.mzn nsp.dzn
```

All three should report `max_shifts = 5` — the minimum since 21 shifts / 5 nurses rounded up is 5. The schedule itself varies (many optima exist), but the objective is invariant.

*Debug tip.* If you see `max_shifts = ??` varying across runs, add `--seed-rnd 42` to CP-SAT or set `-r 1` on Gecode to freeze the solver's RNG. Still varying? Check your model — multiple optima is fine, varying *objective* is a bug.

## 7. Python port (`apps/python/ch08-nsp-tiny/main.py`)

```python
from ortools.sat.python import cp_model

N = 5
D = 7
S = 3       # 0=Day, 1=Evening, 2=Night
DEMAND = [[1, 1, 1] for _ in range(D)]   # every slot = 1 nurse
MAX_WORK_DAYS = 5


def solve_tiny_nsp():
    model = cp_model.CpModel()

    # assign[n][d][s]
    assign = [
        [[model.NewBoolVar(f"a_{n}_{d}_{s}") for s in range(S)] for d in range(D)]
        for n in range(N)
    ]
    # works[n][d] = sum_s assign[n][d][s]
    works = [
        [model.NewIntVar(0, 1, f"works_{n}_{d}") for d in range(D)]
        for n in range(N)
    ]
    for n in range(N):
        for d in range(D):
            model.Add(works[n][d] == sum(assign[n][d][s] for s in range(S)))

    # HC1: coverage
    for d in range(D):
        for s in range(S):
            model.Add(sum(assign[n][d][s] for n in range(N)) == DEMAND[d][s])

    # HC2: at most one shift per day (redundant with Bool domain + works def, kept for clarity)
    for n in range(N):
        for d in range(D):
            model.Add(works[n][d] <= 1)

    # HC3: no day shift after night shift
    for n in range(N):
        for d in range(D - 1):
            model.Add(assign[n][d][2] + assign[n][d + 1][0] <= 1)

    # HC4: max working days per week
    for n in range(N):
        model.Add(sum(works[n][d] for d in range(D)) <= MAX_WORK_DAYS)

    # Objective: minimize max shifts per nurse
    max_shifts = model.NewIntVar(0, D, "max_shifts")
    for n in range(N):
        model.Add(sum(assign[n][d][s] for d in range(D) for s in range(S))
                  <= max_shifts)
    model.Minimize(max_shifts)

    solver = cp_model.CpSolver()
    solver.parameters.num_search_workers = 8
    status = solver.Solve(model)

    if status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        print("no solution; status =", solver.StatusName(status))
        return

    print(f"max_shifts = {int(solver.ObjectiveValue())}")
    labels = ["D", "E", "N"]
    for n in range(N):
        row = [f"N{n+1}"]
        for d in range(D):
            cell = "_"
            for s in range(S):
                if solver.BooleanValue(assign[n][d][s]):
                    cell = labels[s]
                    break
            row.append(cell)
        print(" ".join(row))


if __name__ == "__main__":
    solve_tiny_nsp()
```

Run:

```bash
cd apps/python/ch08-nsp-tiny
uv run main.py
```

Should print:

```
max_shifts = 5
N1 D _ E _ N _ D     <- example schedule, yours may differ
N2 _ D _ E _ N _
N3 E _ D _ E _ N
...
```

## 8. Kotlin port (`apps/kotlin/ch08-nsp-tiny/src/main/kotlin/Main.kt`)

```kotlin
import io.vanja.cpsat.*

private const val N = 5
private const val D = 7
private const val S = 3
private val DEMAND = Array(D) { IntArray(S) { 1 } }
private const val MAX_WORK_DAYS = 5

fun main() {
    val result = cpModel {
        // assign[n][d][s]
        val assign = List(N) { n ->
            List(D) { d ->
                List(S) { s -> boolVar("a_${n}_${d}_$s") }
            }
        }
        // works[n][d]
        val works = List(N) { n ->
            List(D) { d -> intVar("works_${n}_$d", 0..1) }
        }
        for (n in 0 until N) for (d in 0 until D) {
            constraint { works[n][d] eq sum(assign[n][d]) }
        }

        // HC1: coverage
        for (d in 0 until D) for (s in 0 until S) {
            constraint {
                sum((0 until N).map { n -> assign[n][d][s] }) eq DEMAND[d][s].toLong()
            }
        }

        // HC2: at most 1 shift/day (redundant but explicit)
        for (n in 0 until N) for (d in 0 until D) {
            constraint { works[n][d] le 1L }
        }

        // HC3: no day-after-night
        for (n in 0 until N) for (d in 0 until D - 1) {
            constraint { assign[n][d][2] + assign[n][d + 1][0] le 1L }
        }

        // HC4: max working days
        for (n in 0 until N) {
            constraint {
                sum((0 until D).map { d -> works[n][d] }) le MAX_WORK_DAYS.toLong()
            }
        }

        // Objective
        val maxShifts = intVar("max_shifts", 0..D)
        for (n in 0 until N) {
            constraint {
                val allShifts = (0 until D).flatMap { d -> (0 until S).map { s -> assign[n][d][s] } }
                sum(allShifts) le maxShifts
            }
        }
        minimize { maxShifts }
    }.solveBlocking { numSearchWorkers = 8 }

    when (result) {
        is SolveResult.Optimal, is SolveResult.Feasible -> {
            val obj = (result as? SolveResult.Optimal)?.objective
                ?: (result as SolveResult.Feasible).objective
            println("max_shifts = $obj")
            // schedule print omitted for brevity; see solutions/ for full version
        }
        else -> println("no solution: $result")
    }
}
```

*Note on `sum`*: `cpsat-kt` v0.1 exposes `sum(List<LinearExpr>)`. If in your local copy `sum(List<BoolVar>)` doesn't work because of Kotlin's variance rules, add an overload `fun sum(bools: Iterable<BoolVar>): LinearExpr = sum(bools.map { it as LinearExpr })` — or fix the generic signature to accept `Iterable<out LinearExpr>`. That's a one-line library improvement; commit it before the chapter code.

## 9. Three-way parity

Write `apps/python/ch08-nsp-tiny/parity.py` that:

1. Runs the Python solver; records `(obj, schedule)`.
2. Invokes `minizinc --solver com.google.or-tools nsp.mzn nsp.dzn` via subprocess; parses its output.
3. (Optionally) invokes the Kotlin jar via `java -jar apps/kotlin/ch08-nsp-tiny/build/libs/*.jar`; parses its output.
4. Asserts all three objectives equal.

```python
import subprocess, json, re
from pathlib import Path
from main import solve_tiny_nsp  # refactored to return (obj, schedule)

def run_minizinc(mzn, dzn):
    out = subprocess.check_output(
        ["minizinc", "--solver", "com.google.or-tools", mzn, dzn], text=True)
    match = re.search(r"max_shifts = (\d+)", out)
    return int(match.group(1))

def run_kotlin():
    out = subprocess.check_output(
        ["java", "-jar", "apps/kotlin/ch08-nsp-tiny/build/libs/ch08-nsp-tiny-all.jar"],
        text=True)
    match = re.search(r"max_shifts = (\d+)", out)
    return int(match.group(1))

py_obj, _ = solve_tiny_nsp()
mz_obj = run_minizinc("apps/mzn/ch08-nsp-tiny/nsp.mzn", "apps/mzn/ch08-nsp-tiny/nsp.dzn")
kt_obj = run_kotlin()
print(f"Python: {py_obj}, MiniZinc: {mz_obj}, Kotlin: {kt_obj}")
assert py_obj == mz_obj == kt_obj, "Parity failure!"
print("Parity OK.")
```

Expected: `max_shifts = 5` for all three.

## 10. Integration patterns: two ways to use MiniZinc from code

**Pattern A — MiniZinc as pure spec tool.** Write `.mzn` + `.dzn`, invoke `minizinc` CLI once during development, copy the model structure into CP-SAT manually. No runtime dependency on MiniZinc. This is what we did above.

**Pattern B — MiniZinc as runtime backend.** Bundle MiniZinc with your app; invoke at runtime via MiniZinc-Python or subprocess. You get solver-agnosticism (swap backends via config) but add deployment complexity.

```python
# Pattern B in Python
import minizinc

solver = minizinc.Solver.lookup("com.google.or-tools")
model = minizinc.Model("nsp.mzn")
inst = minizinc.Instance(solver, model)
inst["N"] = 5
inst["D"] = 7
inst["demand"] = [[1, 1, 1]] * 7
inst["max_work_days"] = 5

result = inst.solve()
print("assign =", result["assign"])
```

*When Pattern B makes sense:* research tools, academic benchmarking rigs, products where the user picks the solver. For most production optimization apps: Pattern A (prototype, port, drop MiniZinc runtime dependency).

## 11. Which constraints translate cleanly?

**Clean 1:1.**

- Linear sums, equalities, inequalities: `sum([...]) == n`, `<=`, `>=` → `model.Add(...)` directly.
- `all_different` → `model.AddAllDifferent`.
- `circuit`, `cumulative`, `element`, `inverse`, `lex_less_eq` — all have direct `Add*` equivalents.
- Boolean combinators: `\/` (or), `/\` (and) → `model.AddBoolOr`, `model.AddBoolAnd`.

**Awkward.**

- `if/then/else` in MiniZinc often compiles cleanly but is harder to reason about in CP-SAT's Python. Rewrite as reified implications with `OnlyEnforceIf`.
- `bool2int(...) * int_expr` — MiniZinc's implicit bool↔int coercion doesn't exist in CP-SAT's Python. Explicitly create `IntVar(0, 1)` and reify.
- `exists(i in X)(P(i))` works in both but expands to `AddBoolOr` in Python — easy, just verbose.

**Lossy / not direct.**

- `forall + set comprehensions with `where` conditions` that depend on *variables* (not parameters) — these need custom reifications.
- MiniZinc's `array[X, Y] of var int` multi-dim arrays — Python CP-SAT uses nested lists; Kotlin uses nested lists or flat arrays. Indexing math transfers but the storage differs.
- Search annotations (`solve :: int_search([...], first_fail, indomain_min)`) — not portable to CP-SAT's native API; its search strategy is its own thing.

**Rule.** If your `.mzn` uses only linear constraints, globals, and reified booleans, porting is mechanical. If it uses advanced features (tuples, sets of int as decision variables, search annotations), budget more time for the port.

## 12. Comparison & takeaways

| Aspect | `.mzn` | Python CP-SAT | Kotlin `cpsat-kt` |
|---|---|---|---|
| Lines (for this model) | ~50 | ~60 | ~65 |
| Time to write from scratch | ~15 min | ~25 min | ~25 min |
| Debuggability | low (flattened output opaque) | high (print any variable) | high |
| Integration with app | subprocess / MzN-Python | native | native |
| Runnable on 5 solvers | yes | no (CP-SAT only) | no (CP-SAT only) |

**Key insight.** *Writing the spec in MiniZinc is a forcing function for clarity.* If you can't write the rule in 1-2 lines of MiniZinc, it's probably a rule that needs decomposition. This alone often catches fuzzy requirements before they become coding bugs.

**Secondary insight.** *Parity testing is cheap insurance.* Once you have the `.mzn`, running it in parallel with your Python/Kotlin impls on a handful of instances catches bugs no unit test could find. Wire this into CI for all NSP instances once you have more than a toy.

**Tertiary insight.** *Don't wait to drop MiniZinc as a runtime dependency.* Use it during design. Ship the CP-SAT port. The `.mzn` stays in the repo as living documentation, regenerated if the rules change.

## 13. Exercises

### Exercise 8.1 — Add a coverage preference

**Problem.** Extend the MiniZinc model to prefer N1 and N2 on night shifts (they volunteered). Soft constraint: every N-shift assigned to N1 or N2 earns 1 bonus point; maximize total bonus while keeping all hard constraints. Make the objective lexicographic: primary = `max_shifts` (minimize), secondary = `bonus` (maximize).

**Expected output:** same `max_shifts = 5`, but N1/N2 get as many N-shifts as the structure allows.

**Acceptance criteria:** you can articulate the lex objective pattern for MiniZinc and both CP-SAT ports.

<details><summary>Hint</summary>

MiniZinc: `solve minimize max_shifts - 0.001 * bonus` works for small coefficients; or use `solve :: seq_search([...]) minimize max_shifts` with a secondary objective pass. CP-SAT Python: the "solve, fix, re-solve" pattern from Ch 5. MiniZinc doesn't natively support lex objectives — you either combine into a weighted sum (fragile) or do the two-pass pattern externally.

</details>

### Exercise 8.2 — Unequal demand

**Problem.** Change the demand so night shifts need 1 nurse but day shifts need 2. Total slots becomes 7 × (2+1+1) = 28. With 5 nurses × 5 max days = 25 max assignments. The model becomes infeasible. Confirm (a) MiniZinc reports `UNSATISFIABLE`, (b) Python CP-SAT reports `INFEASIBLE`, (c) Kotlin reports `SolveResult.Infeasible`.

**Acceptance criteria:** all three agree that no solution exists.

<details><summary>Hint</summary>

Change `demand = array2d(...)` to `[2,1,1, 2,1,1, ...]`. Increase `max_work_days` to 7 to see a feasible solution again.

</details>

### Exercise 8.3 — Port two more HC rules

**Problem.** Add two more hard constraints to the MiniZinc, then port to Python and Kotlin:

- **HC5 — Min 2 rest days per week.** Each nurse: `sum of days off >= 2` per 7-day block.
- **HC6 — No 3 consecutive nights.** For each nurse: the sub-array `assign[n, d, 2] for d in 1..D` has no three 1's in a row.

Re-run and confirm the new objective.

**Acceptance criteria:** all three implementations agree. Python and Kotlin can use either sliding-window sum or Automaton for HC6 — try both and compare.

<details><summary>Hint</summary>

HC5 MiniZinc: `constraint forall(n in 1..N) (sum(d in 1..D)(1 - works[n,d]) >= 2);`. HC6 MiniZinc with `regular`: build a DFA over {0,1} that rejects 111 substring; `include "regular.mzn";` and use `regular(assign_n_night_seq, states, alphabet, transitions, q0, F)`.

</details>

### Exercise 8.4 — Write the MiniZinc-Python driver

**Problem.** Write a `driver.py` that uses the MiniZinc-Python API (Pattern B from §10) to solve the same model. Have it iterate over 3 instances (5×7, 6×7, 7×14) and print results. No subprocess — use the Python package.

**Acceptance criteria:** all three instances solve; your driver is ~25 lines.

<details><summary>Hint</summary>

```python
import minizinc
solver = minizinc.Solver.lookup("com.google.or-tools")
model = minizinc.Model("nsp.mzn")
for (N, D) in [(5, 7), (6, 7), (7, 14)]:
    inst = minizinc.Instance(solver, model)
    inst["N"] = N
    inst["D"] = D
    inst["demand"] = [[1, 1, 1]] * D
    inst["max_work_days"] = 5
    result = inst.solve()
    print(f"{N}x{D}: max_shifts={result['max_shifts']}")
```

</details>

### Exercise 8.5 — Parity-test harness as a pytest

**Problem.** Wrap the §9 parity check in a pytest that asserts all three implementations return the same objective for the 5×7 instance.

**Acceptance criteria:** `uv run pytest -v` passes; failing the Kotlin run (by modifying the model) makes the test fail.

<details><summary>Hint</summary>

Use `@pytest.mark.parametrize` to cover multiple instances. Run `pytest -v -s` to see stdout.

</details>

Solutions live in `apps/*/ch08-nsp-tiny/solutions/`. Try first.

## 14. Self-check

**Q1.** What do you gain from writing MiniZinc first? What do you lose?

<details><summary>Answer</summary>

*Gain:* a clear, concise specification every stakeholder can read; solver-portability check before committing to one API; forced clarity on every constraint (nothing sneaks through as code logic); and a shared artifact for benchmarking. *Lose:* time (yet another file to maintain); solver-specific performance tuning; advanced CP-SAT features (callbacks, hints, custom search); and runtime integration needs a subprocess boundary if you want to keep using MiniZinc in production.

</details>

**Q2.** Which constraints translate cleanly from MiniZinc to CP-SAT, and which don't?

<details><summary>Answer</summary>

Clean: linear constraints, global constraints with native CP-SAT equivalents (`all_different`, `circuit`, `cumulative`, `element`, `inverse`, `lex`, `regular`/`automaton`), boolean combinators, simple `forall`/`exists`. Awkward: MiniZinc's `if/then/else` needs re-expression as reified implications in CP-SAT; `bool2int` coercions need explicit `IntVar(0, 1)` introduction. Lossy: decision-variable-indexed array conditions (need `Element`), set-of-int decision variables, `solve` annotations (no portable equivalent).

</details>

**Q3.** When would you use MiniZinc at runtime in production vs. dropping it after design?

<details><summary>Answer</summary>

Use MiniZinc at runtime when: (a) users pick the solver (benchmark harness, academic tools), (b) the solver choice depends on instance shape and can vary, or (c) the model changes so rapidly that re-porting to CP-SAT every time is untenable. Drop MiniZinc for production when: (a) you've validated the model, (b) you need solver-specific features (callbacks, hints, early-stop), (c) latency matters (subprocess overhead is ~50-100ms per solve; native is 0), or (d) deployment simplicity matters (one fewer binary to bundle).

</details>

**Q4.** Why is "parity test across three implementations" stronger than "unit tests on one implementation"?

<details><summary>Answer</summary>

A unit test can pass in all three implementations *while the model itself is wrong* — they all agree on the wrong answer. Parity across a declarative spec (MiniZinc) and two imperative impls (Python, Kotlin) catches porting bugs specifically: if the Python adds a constraint the `.mzn` doesn't, the objectives diverge. It catches what unit tests can't: *transcription errors between the spec and the implementation*. Not a replacement for unit tests — a complement.

</details>

**Q5.** The MiniZinc model used `works[n,d]` as a helper — why not inline `sum(s in 0..S-1)(assign[n, d, s])` everywhere?

<details><summary>Answer</summary>

Three reasons: (1) readability — `works[n,d] <= 1` reads like the constraint's name; (2) a single named variable is easier to inspect in solver logs or print statements; (3) some solvers' flatteners produce better FlatZinc when you name common subexpressions (the flattener will often introduce such variables automatically, but doing it explicitly guarantees the behavior). Trade-off: adds one variable per cell, but the solver merges them in presolve — negligible cost.

</details>

## 15. What this unlocks

You have a reproducible workflow: **MiniZinc spec → CP-SAT port → parity check**. You'll use it for every non-trivial NSP variant in Phases 4 and 5.

Chapter 9 leaves NSP for a chapter and tours **scheduling primitives**: `IntervalVar`, `NoOverlap`, `Cumulative`, `OptionalInterval` — the machinery for job-shop scheduling. You'll extend `cpsat-kt` with the interval DSL, which the full NSP will use in Phase 5.

## 16. Further reading

- Nethercote, [*MiniZinc Tutorial*](https://docs.minizinc.dev/en/stable/modelling.html) — chapters 2-6; the declarative-modeling playbook.
- Stuckey, [*Modeling in MiniZinc*](https://www.mzn.org.au/tutorial) — worked case studies including NSP variants.
- OR-Tools docs, [*CP-SAT Python API*](https://developers.google.com/optimization/reference/python/sat/python/cp_model) — when translating, keep open.
- [`docs/knowledge/nurse-scheduling/overview.md`](../knowledge/nurse-scheduling/overview.md) — full NSP taxonomy; you'll return to this in Ch 10+.
- Minizinc-Python docs: <https://minizinc-python.readthedocs.io/> — Pattern B reference.
- De Cauwer et al., [*A Study on Generating and Solving Nurse Scheduling Problem Instances*](https://link.springer.com/article/10.1007/s10479-016-2259-4) — good test-case library for later chapters.
