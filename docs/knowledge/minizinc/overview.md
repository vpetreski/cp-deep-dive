# MiniZinc: A Solver-Agnostic Modeling Language

*Research date: 2026-04-19*

---

## 1. What is MiniZinc? (ELI5)

MiniZinc is a **high-level constraint modeling language**. You write down *what* the problem is — the variables, their domains, and the rules they must satisfy — without committing to *how* to solve it. The MiniZinc compiler then translates your model, together with data, into a much simpler intermediate language called **FlatZinc**, which any supported backend solver can consume: constraint programming solvers (Gecode, Chuffed, OR-Tools CP-SAT), mixed-integer programming solvers (Gurobi, CPLEX, HiGHS, CBC), and even SAT/SMT solvers. One model, many solvers.

Think of MiniZinc as the "LaTeX of combinatorial optimization": a portable, human-readable specification format that downstream tools can render into performant solver input.

Primary source: <https://www.minizinc.org/>

---

## 2. Why solver-agnostic matters

The promise is **"specify once, solve everywhere."** Combinatorial problems don't have a universal best solver — a scheduling problem might crush CP-SAT but choke Gurobi, and vice versa for a network-flow LP. Without MiniZinc, swapping solvers means rewriting the entire model in each solver's API (Python bindings, C++ constraints, LP matrices, etc.). With MiniZinc, you change one CLI flag.

This matters for three reasons:

1. **Portfolio solving** — run multiple solvers in parallel, take the first to finish.
2. **Benchmarking** — fair comparison of solver performance on an identical model (this is literally how the MiniZinc Challenge works).
3. **Future-proofing** — solver landscape shifts every year; your model doesn't become obsolete when a new winner emerges.

**Notable backends:**

| Backend | Type | Notes |
|---|---|---|
| Gecode | CP | The historical reference solver; bundled with MiniZinc |
| Chuffed | CP (lazy clause generation) | Strong on scheduling; frequent Challenge medalist |
| OR-Tools CP-SAT | CP-SAT hybrid | Google's solver; delivered via `or-tools-flatzinc` |
| HiGHS | LP/MIP | Open-source; increasingly popular default MIP |
| COIN-OR CBC | MIP | Open-source LP/MIP, mature but slower |
| Gurobi | MIP | Commercial; best-in-class MIP |
| CPLEX | MIP | Commercial; IBM's flagship |
| Picat-SAT, Yuck, OptiMathSAT, Choco | various | Also available through the solver catalog |

Sources: <https://www.minizinc.org/software.html>, <https://www.minizinc.org/challenge/>

---

## 3. Architecture: MiniZinc → FlatZinc → Solver

```
model.mzn + data.dzn
        |
        v
 [ MiniZinc compiler (mzn2fzn) ]
        |
        v
   model.fzn  (FlatZinc: flat, solver-ready)
        |
        v
 [ Backend solver: gecode / chuffed / or-tools / ... ]
        |
        v
   Solution (JSON or mzn-output format)
```

**FlatZinc** is a deliberately minimal, flat sub-language: only primitive variable types, no nested expressions, no function definitions, no user predicates. Every constraint is reduced to a small set of primitive or global-constraint calls that the solver recognises by name (e.g. `int_lin_eq`, `all_different`, `cumulative`).

**Why the split?** Solver authors don't want to parse a rich language — they want a well-defined, machine-friendly input. Modelers don't want to hand-flatten expressions — they want `forall`, `if/then/else`, algebraic expressions, sets, arrays. The compiler bridges the two. A solver "supports MiniZinc" if it ships a FlatZinc frontend and declares which global constraints it implements natively (via a `<solver>.msc` config plus a redefinitions library).

FlatZinc spec: <https://www.minizinc.org/doc-2.8/en/fzn-spec.html>

---

## 4. Language tour

MiniZinc is declarative, strongly typed, and Pythonic-ish.

**Core types and variables**

```minizinc
int: n = 8;                       % parameter (known before solving)
var 1..n: x;                      % decision variable over {1..8}
var bool: flag;
set of int: DAYS = 1..7;
array[1..n] of var 0..100: load;  % array of decision vars
```

- `int`, `float`, `bool`, `string` — base types.
- `par` (parameter, known at compile time) vs `var` (decision variable). `par` is the default.
- `set of <T>` — first-class finite sets.
- `array[<index set>] of <type>` — multi-dimensional arrays indexed by any enum/int range.

**Constraints and objective**

```minizinc
constraint sum(load) <= 500;
constraint forall(i in 1..n-1) (load[i] <= load[i+1]);
solve minimize max(load);
```

**Predicates, functions, includes**

```minizinc
include "globals.mzn";       % global constraint library
include "alldifferent.mzn";  % or include just what you need

predicate no_overlap(array[int] of var int: s, array[int] of int: d) =
    forall(i,j in index_set(s) where i < j)
      (s[i] + d[i] <= s[j] \/ s[j] + d[j] <= s[i]);
```

**Global constraints** (the killer feature) — high-level patterns with known efficient propagators:

- `all_different(xs)`, `all_equal(xs)`
- `cumulative(starts, durations, resources, capacity)` — classic scheduling
- `circuit(xs)` — Hamiltonian circuit (for TSP-shaped problems)
- `bin_packing`, `global_cardinality`, `table`, `regular`, `lex_less`

The solver either implements a global natively (fast) or the compiler decomposes it into primitives (correct, slower). This is the core of the MiniZinc value proposition.

Handbook reference: <https://docs.minizinc.dev/en/stable/>

---

## 5. Tiny worked example: N-Queens

Place N queens on an N×N board so that no two attack each other.

```minizinc
% n-queens.mzn
include "alldifferent.mzn";

int: n;
array[1..n] of var 1..n: q;  % q[i] = column of queen in row i

constraint alldifferent(q);                            % different columns
constraint alldifferent([q[i] + i | i in 1..n]);       % different / diagonals
constraint alldifferent([q[i] - i | i in 1..n]);       % different \ diagonals

solve :: int_search(q, first_fail, indomain_min) satisfy;

output ["Queens at columns: \(q)\n"];
```

With data file `n8.dzn`:

```minizinc
n = 8;
```

Run: `minizinc --solver gecode n-queens.mzn n8.dzn`

Note: three `alldifferent` constraints cleanly capture the entire problem; no index gymnastics, no nested loops, no manual symmetry handling. That's the MiniZinc elegance.

---

## 6. Data separation: `.mzn` vs `.dzn`

MiniZinc splits **structure** (model: `.mzn`) from **instance** (data: `.dzn` or `.json`):

```
scheduling.mzn          ← problem formulation: variables, constraints
  ├── small.dzn         ← 10 tasks
  ├── medium.dzn        ← 100 tasks
  └── large.dzn         ← 10,000 tasks
```

Why this matters:

- **Benchmarking**: run the same model against a whole directory of instances for performance curves.
- **Deployment**: non-programmers (domain experts) edit `.dzn` files without touching the model.
- **Testability**: small instances for unit tests, large ones for nightly runs.
- **Competitions**: the MiniZinc Challenge distributes problems exactly this way — model plus a pile of instances.

Data can also be passed via JSON or via the `--cmdline-data` flag for one-off overrides.

---

## 7. Solver backends as of 2026

The MiniZinc bundle includes Gecode, Chuffed, and COIN-BC by default. Others install via solver config files pointing at the backend's FlatZinc binary.

| Solver | Paradigm | One-line |
|---|---|---|
| **Gecode** | Finite-domain CP | Reference solver; broad global support; comes bundled |
| **Chuffed** | Lazy clause generation CP | SAT-like learning on CP; dominates many scheduling benchmarks |
| **OR-Tools CP-SAT** | CP-SAT hybrid | Google's workhorse; excellent on real-world instances |
| **HiGHS** | LP/MIP | Fast open-source MIP; good default for linear models |
| **COIN-BC** | MIP | Mature open-source MIP; bundled |
| **Gurobi** | MIP | Commercial state-of-the-art for MIP |
| **CPLEX** | MIP | IBM's flagship commercial MIP solver |
| **Picat-SAT, Yuck** | SAT-based / local search | Strong on specific problem classes |

**MiniZinc Challenge top-3 (recent years)** — CP-SAT (OR-Tools) has been dominant since 2018, frequently sweeping gold across the free, parallel, and open categories. Chuffed remains a repeat medalist especially on scheduling. Gecode is the historical anchor. Exact current standings: <https://www.minizinc.org/challenge/>.

**Practical takeaway:** if you don't know which solver fits, try CP-SAT first for scheduling/assignment/routing-flavored CP, HiGHS or Gurobi for pure MIP, Chuffed as a strong CP alternative.

---

## 8. MiniZinc + OR-Tools CP-SAT: two integration paths

### Path A — Use CP-SAT as a MiniZinc backend

Install OR-Tools' `fzn-ortools` (packaged as `or-tools-flatzinc`) and register it as a MiniZinc solver:

```bash
minizinc --solver com.google.or-tools model.mzn data.dzn
```

CP-SAT receives FlatZinc, runs its CP-SAT engine, and returns solutions. Zero Python code; you get CP-SAT's speed through the MiniZinc model.

**When this makes sense:** research, benchmarking, or when the MiniZinc model is already "production-enough" and you just want the best solver underneath.

### Path B — Prototype in MiniZinc, reimplement in CP-SAT Python API

1. Write the model in MiniZinc, validate correctness on small instances, iterate fast.
2. Once the formulation is right, port it to CP-SAT's Python (or Java/C++) API: `CpModel`, `NewIntVar`, `Add*` constraint methods, search strategies.
3. Deploy the Python version in production.

**Why bother porting?** The CP-SAT Python API gives you:

- Direct control of search strategies, hints, warm starts, and callbacks.
- Programmatic model construction (loops over real data, DB/Kafka connections).
- Incremental re-solving (keep the solver alive, update constraints).
- Tighter integration: logging, metrics, deployment, error handling.
- FlatZinc loses some semantics; the native API lets you use every CP-SAT feature.

**Heuristic:** MiniZinc is your *whiteboard*. CP-SAT Python is your *production system*. Most serious users do both: prototype and benchmark in MiniZinc, ship in CP-SAT.

---

## 9. Running MiniZinc

**IDE** — MiniZinc bundles a full IDE (editor, solver picker, solution browser, profiler). Great for learning and exploration. <https://www.minizinc.org/ide/>

**CLI** — the workhorse:

```bash
# basic solve
minizinc --solver gecode model.mzn data.dzn

# list available solvers
minizinc --solvers

# all solutions (for satisfaction problems)
minizinc --solver chuffed --all-solutions nqueens.mzn n8.dzn

# time limit and statistics
minizinc --solver com.google.or-tools --time-limit 60000 -s model.mzn data.dzn
```

**Python API** — `minizinc-python` lets you drive MiniZinc from Python:

```python
from minizinc import Instance, Model, Solver

model = Model("./nqueens.mzn")
gecode = Solver.lookup("gecode")
inst = Instance(gecode, model)
inst["n"] = 8
result = inst.solve()
print(result["q"])
```

This is how you glue MiniZinc into a pipeline: construct data in Python, solve via MiniZinc, post-process results in Python. It does *not* replace the CP-SAT Python API — they're different tools.

Docs: <https://python.minizinc.dev/>

---

## 10. Gotchas

- **`par` vs `var`** — using a `var` where a `par` is expected (e.g. as an array index) triggers cryptic flattening errors. The compiler has to "unroll" over the variable's domain, which can explode model size.
- **Search annotations matter**. Default search is often poor. `solve :: int_search(vars, first_fail, indomain_min) satisfy` is a sensible starting point. Solvers may or may not honor annotations — CP-SAT largely ignores them and uses its own search.
- **Channeling** — when you model the same thing two ways (e.g. array-of-positions and array-of-assignments) you need channeling constraints (`inverse`, `int2bool`) to keep them in sync. Done right: fast. Done wrong: slow or incorrect.
- **Float/int mixing** — MIP backends handle floats; pure CP backends often don't. If you need continuous variables, pick an MIP-capable solver.
- **Global constraints may be decomposed**. Check your solver's `.msc` to see which globals are native vs decomposed — a decomposed `cumulative` can be orders of magnitude slower.
- **Debugging flattening failures** — use `minizinc --compile` to inspect the generated FlatZinc, and `--verbose` for compiler diagnostics. The profiler in the IDE shows which constraints generated the most FlatZinc.
- **Set-of-int domain sizes** — MiniZinc set variables are bitmap-backed; huge sets blow up memory.
- **Output formatting** — `output` blocks are string-based and can swallow errors silently if indices are wrong.

Handbook: <https://docs.minizinc.dev/en/stable/>

---

## 11. MiniZinc vs straight CP-SAT: when to use which

| Use MiniZinc when… | Use CP-SAT Python directly when… |
|---|---|
| Specifying/prototyping a new problem | Shipping a production optimization service |
| Teaching CP/MIP concepts | Needing incremental re-solving or warm starts |
| Benchmarking across solvers | Needing callbacks, hints, custom search |
| Writing a solver-agnostic reference model | Integrating with Python data pipelines / APIs |
| Entering the MiniZinc Challenge | Tuning for maximum performance on one solver |
| Communicating the model to non-Python teammates | Needing full control over the solver lifecycle |

**Summary heuristic:** MiniZinc is a **spec + prototyping + teaching + benchmarking** tool. CP-SAT Python is a **production** tool. A mature workflow uses both: MiniZinc to nail down the formulation and compare solvers, CP-SAT to deploy.

---

## 12. Version & version note (April 2026)

MiniZinc ships frequent minor releases. As of this research, the current stable series is **MiniZinc 2.8.x** (documentation branch at <https://www.minizinc.org/doc-2.8/en/index.html>), with the 2.9 / 2.10 lines potentially available as newer releases — **verify against <https://github.com/MiniZinc/libminizinc/releases> for the exact latest tag before installing**. The language itself has been stable for years; the deltas are compiler performance, new globals, solver catalog updates, and IDE improvements, not breaking language changes.

---

## 13. Resources

- **Official site** — <https://www.minizinc.org/>
- **Documentation (latest)** — <https://docs.minizinc.dev/en/stable/>
- **Documentation (2.8)** — <https://www.minizinc.org/doc-2.8/en/index.html>
- **The MiniZinc Handbook** — the canonical tutorial + reference, bundled with the docs above.
- **Free Coursera courses** by Peter Stuckey & Jimmy Lee: *Basic Modeling for Discrete Optimization* and *Advanced Modeling for Discrete Optimization* — <https://www.minizinc.org/courses.html> (also on Coursera).
- **Source** — <https://github.com/MiniZinc/libminizinc> (check Releases for current version).
- **MiniZinc Challenge** — <https://www.minizinc.org/challenge/> (annual solver competition; archive includes models, instances, and results — a goldmine for real-world formulations).
- **FlatZinc specification** — <https://www.minizinc.org/doc-2.8/en/fzn-spec.html>
- **Python API** — <https://python.minizinc.dev/>
- **Key researchers** — Peter Stuckey, Guido Tack, Kim Marriott (Monash University); see publications on their academic pages for the language's design rationale.
