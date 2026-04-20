---
title: "Chapter 7 — MiniZinc tour"
phase: 3
estimated: 2h
status: draft
last_updated: 2026-04-19
---

# Chapter 7 — MiniZinc tour

## 1. Goal

Switch perspective. Until now you've modeled directly in a solver's API — Python's CP-SAT or the `cpsat-kt` DSL. This chapter introduces **MiniZinc**, a *solver-agnostic* modeling language: you describe *what* the problem is, the MiniZinc compiler flattens your model, and any of a dozen backend solvers runs it. You'll:

- Install MiniZinc and understand its pipeline (`.mzn` + `.dzn` → FlatZinc → solver → solution).
- Re-solve problems from earlier chapters in MiniZinc: N-Queens, 0-1 knapsack, SEND+MORE=MONEY.
- Run the same model on two different backends (Gecode and CP-SAT) and compare timings.
- Know when declarative modeling helps you and when it gets in your way.

By the end you should be comfortable reading any MiniZinc file and making small edits. Chapter 8 takes this further — MiniZinc as a *spec tool* that you port to CP-SAT.

## 2. Before you start

- Chapters 1–6 complete; you've built `cpsat-kt` and used it on classic puzzles.
- Homebrew installed (macOS). On Linux, use your distro package manager or the tarball from minizinc.org.
- Read [`docs/knowledge/minizinc/overview.md`](../knowledge/minizinc/overview.md) sections 1–4 (what/why/pipeline/language tour).

**Install.**

```bash
brew install minizinc
minizinc --version         # confirm >= 2.9
minizinc --solvers         # list detected backends
```

The Homebrew formula ships Gecode, Chuffed, and HiGHS by default. For CP-SAT, install OR-Tools' `or-tools-flatzinc` binary and drop its `.msc` file into `~/.minizinc/solvers/`. See Appendix A at the end of this chapter.

## 3. Concepts introduced

- **.mzn file** — the *model*: variables, constraints, objective.
- **.dzn file** — the *data*: concrete values for parameters declared in the model.
- **FlatZinc (.fzn)** — the *compiled* model after flattening: primitive constraints only.
- **Solve item** — `solve satisfy;` (CSP), `solve minimize <expr>;`, `solve maximize <expr>;`.
- **`par` vs `var`** — compile-time constant vs decision variable.
- **`forall` / `exists` / `sum`** — comprehensions over index sets.
- **Global constraints** — `include "globals.mzn";` gives `all_different`, `circuit`, `cumulative`, etc.
- **Backend solver** — `minizinc --solver <name>`; each has a declaration (`.msc`) and a globals library.
- **MiniZinc Challenge** — annual benchmark where backends compete on common models. The winners rotate; recent champions include CP-SAT and Chuffed depending on category.

## 4. Intuition

*MiniZinc is LaTeX for optimization.* A LaTeX document doesn't know how it will be rendered — the same `.tex` produces a PDF via `pdflatex`, HTML via `tex4ht`, or EPUB via Pandoc. Every renderer has quirks, but the source is portable.

MiniZinc does the same for constraint models. You write one `.mzn` file; the compiler flattens it into FlatZinc; a backend solver consumes the FlatZinc and produces a solution. If the solver doesn't natively understand a global constraint, the compiler decomposes it into primitives. If it does, the solver gets the fast path.

The cost: you lose the ability to do solver-specific tricks (custom search strategies, low-level parameter tuning, callbacks streaming partial solutions). MiniZinc's flag passthrough is limited by design — it's deliberately portable, which means the lowest common denominator.

*The deal.* Write the model once. Try three backends. Pick the fastest for the instance class. Re-benchmark when instance sizes change. This is the MiniZinc value proposition.

## 5. Formal pipeline

```
your_model.mzn  +  data.dzn
        |
        v
  minizinc --compile → model.fzn      (plain text, solver-ready FlatZinc)
        |
        v
  <backend>.fzn-solver → raw output
        |
        v
  minizinc post-processor → formatted output (applying `output [...]` in the .mzn)
```

**FlatZinc is a deliberately tiny language.** It has: primitive types (`int`, `bool`, `float`, `set of int`), arrays, named constraints (all in snake_case, like `int_lin_eq`, `all_different_int`), and a solve item. No function definitions, no comprehensions, no if/else — everything is flattened.

**`.msc` solver config** is a small JSON file declaring the backend: its `executable`, its `mznlib` (globals library), its `supportsMzn` flag, etc. MiniZinc uses this to find the backend and know which globals it implements natively vs which must be decomposed.

## 6. Worked examples

Three classics, each with `.mzn` + `.dzn`. Run each on Gecode and CP-SAT; compare.

### 6.1 N-Queens

**`apps/mzn/ch07-tour/queens.mzn`:**

```minizinc
include "globals.mzn";

int: n = 12;                           % parameter (overridable via -D or .dzn)
array[1..n] of var 1..n: q;            % q[i] = column of queen in row i

constraint all_different(q);                             % no two in same column
constraint all_different([q[i] + i | i in 1..n]);        % no two on \ diagonal
constraint all_different([q[i] - i | i in 1..n]);        % no two on / diagonal

solve satisfy;

output ["q = ", show(q), "\n"];
```

Run:

```bash
minizinc --solver gecode queens.mzn
minizinc --solver com.google.or-tools queens.mzn
```

Try also:

```bash
minizinc --solver chuffed queens.mzn -a        # -a: all solutions
minizinc --solver gecode queens.mzn -D "n=20;"
```

Gecode should be near-instant on `n=12`. Push to `n=50` and you'll feel the difference — CP-SAT (with its LP-based cuts and parallel workers) often beats Gecode on harder instances, while Gecode's pure CP engine is very fast on small ones.

### 6.2 0-1 Knapsack

**`apps/mzn/ch07-tour/knapsack.mzn`:**

```minizinc
int: n;
array[1..n] of int: weight;
array[1..n] of int: value;
int: capacity;

array[1..n] of var 0..1: x;

constraint sum(i in 1..n)(weight[i] * x[i]) <= capacity;

solve maximize sum(i in 1..n)(value[i] * x[i]);

output [
    "selected = ", show([i | i in 1..n where fix(x[i]) == 1]), "\n",
    "total_value = ", show(sum(i in 1..n)(value[i] * fix(x[i]))), "\n"
];
```

**`apps/mzn/ch07-tour/knapsack.dzn`:**

```minizinc
n = 15;
weight = [2,3,4,5,5,3,6,7,4,3,8,5,2,6,1];
value  = [7,5,3,12,10,8,4,20,9,6,18,8,4,11,2];
capacity = 20;
```

Run:

```bash
minizinc --solver gecode     knapsack.mzn knapsack.dzn
minizinc --solver com.google.or-tools knapsack.mzn knapsack.dzn
minizinc --solver highs      knapsack.mzn knapsack.dzn   # HiGHS (MIP) also works
```

Knapsack is a textbook MIP problem — HiGHS should do well. CP-SAT is also strong on 0-1 knapsacks (its LP engine kicks in). Gecode, being pure CP, has no LP relaxation and relies on bound propagation alone — it will often be slower for larger instances but still solve this 15-item one quickly.

### 6.3 SEND + MORE = MONEY

**`apps/mzn/ch07-tour/sendmore.mzn`:**

```minizinc
include "globals.mzn";

set of int: DIGITS = 0..9;
array[1..8] of var DIGITS: letters;   % [S, E, N, D, M, O, R, Y]

var int: S = letters[1];
var int: E = letters[2];
var int: N = letters[3];
var int: D = letters[4];
var int: M = letters[5];
var int: O = letters[6];
var int: R = letters[7];
var int: Y = letters[8];

constraint all_different(letters);
constraint S != 0;
constraint M != 0;
constraint
              1000*S + 100*E + 10*N + D
           +  1000*M + 100*O + 10*R + E
         ==  10000*M + 1000*O + 100*N + 10*E + Y;

solve satisfy;

output ["S=",show(S)," E=",show(E)," N=",show(N)," D=",show(D),
        " M=",show(M)," O=",show(O)," R=",show(R)," Y=",show(Y), "\n"];
```

Run:

```bash
minizinc --solver gecode    sendmore.mzn
minizinc --solver com.google.or-tools sendmore.mzn
minizinc --solver chuffed   sendmore.mzn
```

All three return the unique solution in well under 100 ms. The point isn't speed — it's the model being 15 lines vs 40+ in CP-SAT's Python/Kotlin APIs. Declarative concision matters for initial prototyping.

## 7. Comparison: backends on the same model

Record wall times across backends for each model above. Typical observations (your numbers will vary by hardware; seed=default):

| Model | Instance | Gecode | Chuffed | CP-SAT | HiGHS |
|---|---|---|---|---|---|
| N-Queens | n=12 | <0.05s | <0.05s | ~0.1s (worker overhead) | N/A (MIP is weak here) |
| N-Queens | n=50 | ~0.3s | ~0.2s | ~0.05s (parallel) | 10s+ (unsuited) |
| Knapsack | 15-item | <0.05s | <0.05s | <0.05s | <0.05s |
| Knapsack | 1000-item (synthetic) | ~10s | ~5s | <1s | <1s |
| SEND+MORE | — | <0.05s | <0.05s | <0.05s | ~0.2s |

**Takeaway.** *No single backend wins everywhere.* Gecode is great on small-to-medium pure CP. CP-SAT's hybrid engine dominates on harder optimization. MIP solvers (HiGHS, Gurobi) own classic LP-relaxation-friendly problems. The fact that the *same `.mzn`* ran everywhere means you can A/B any new model with a one-flag change.

## 8. Kotlin / Python interop (preview — details in Ch 8)

MiniZinc-Python (official package `minizinc`) lets you run `.mzn` from Python:

```python
import minizinc

solver = minizinc.Solver.lookup("com.google.or-tools")
model = minizinc.Model()
model.add_file("queens.mzn")
inst = minizinc.Instance(solver, model)
inst["n"] = 12
result = inst.solve()
print(result["q"])
```

From Kotlin, the equivalent is calling `minizinc` as a subprocess (no official JVM binding yet). A sketch for the appendix:

```kotlin
val proc = ProcessBuilder("minizinc", "--solver", "com.google.or-tools", "queens.mzn")
    .redirectErrorStream(true)
    .start()
val output = proc.inputStream.bufferedReader().readText()
proc.waitFor()
println(output)
```

In Chapter 8 you'll use MiniZinc as a *design tool*: prototype quickly, then port to CP-SAT once the model is right.

## 9. When declarative helps / hurts

**Helps:**

- *Early prototyping.* You explore whether a problem is solvable before committing to an implementation.
- *Spec sharing.* Send `.mzn` to a colleague — they can read it without knowing your solver.
- *Benchmarking.* One model, many solvers, fair comparison.
- *Archival.* Your model is portable forever; solver bindings change yearly.

**Hurts:**

- *Fine-grained control.* No custom search strategies, no callbacks, no parameter tuning per branch.
- *Runtime integration.* Subprocess boundary adds latency and awkward data marshalling.
- *Debugging.* When the flattener optimizes your expression into something unexpected, it can be hard to trace back.
- *Rich data types.* MiniZinc has `set of int`, arrays, tuples, enums — but nothing like user-defined records or inheritance.

**Rule of thumb.** *Prototype in MiniZinc; ship in CP-SAT.* Unless solver portability is itself a feature (academic benchmarks, research projects), production deployments pick one backend and commit.

## 10. Exercises

### Exercise 7.1 — Run the tour

**Problem.** Install MiniZinc, confirm CP-SAT is registered (`minizinc --solvers` lists `com.google.or-tools`), then run each of the three `.mzn` files above on Gecode and CP-SAT.

**Expected output:** same answer from both backends for each model.

**Acceptance criteria:** you can paste the command + output for each of the 6 runs.

<details><summary>Hint</summary>

Registering CP-SAT: download the `or-tools-flatzinc` binary from <https://github.com/google/or-tools/releases>; drop `com.google.or-tools.msc` into `~/.minizinc/solvers/`; confirm with `minizinc --solvers`. See Appendix A.

</details>

### Exercise 7.2 — Push N-Queens to n=50

**Problem.** Modify the model (or pass `-D "n=50;"`). Time Gecode vs CP-SAT with `num_search_workers=8` (via `--solver-flags "-cpSatWorkers=8"` or similar backend-specific flag).

**Expected output:** both find a solution; CP-SAT (parallel) is faster than Gecode (single-threaded) once n is large enough.

**Acceptance criteria:** timings table, brief interpretation.

<details><summary>Hint</summary>

CP-SAT's MiniZinc flag for workers: check `minizinc --solver com.google.or-tools --help` — the flag is usually `-p 8` (parallel) or exposed via a solver-specific options namespace. Gecode parallelism: `-p 8` for parallel portfolio.

</details>

### Exercise 7.3 — Port your knapsack data

**Problem.** Take the 15-item knapsack `.dzn` and add 5 more items. Run on Gecode, CP-SAT, HiGHS. Report timings and whether all three find the same optimum (they should — optimization is deterministic given the same model).

**Acceptance criteria:** a `timings.md` in the deliverable showing the runs.

### Exercise 7.4 — Write an all_different-free N-Queens

**Problem.** Re-express the N-Queens model using only pairwise `!=` constraints (no `include "globals.mzn";`, no `all_different`). Compare solve time on n=12 and n=20 between global and decomposed versions on Gecode.

**Expected output:** the decomposed version is noticeably slower at n=20.

**Acceptance criteria:** timings; one-sentence interpretation of why (hint: Régin's matching algorithm).

<details><summary>Hint</summary>

Decomposed: `constraint forall(i, j in 1..n where i < j) (q[i] != q[j]);`. Repeat for the two diagonal arrays.

</details>

### Exercise 7.5 — Add an output array that groups solutions

**Problem.** For SEND+MORE, enumerate all solutions (`minizinc -a`). Get 0 or 1 (there's exactly one). Now modify to allow digits up to 11 (invalid puzzle but relax the constraint); run with `-a` and count how many solutions exist in that relaxation. Use the output section to print each assignment.

**Acceptance criteria:** you can explain what `-a` does and how MiniZinc emits multiple solutions.

<details><summary>Hint</summary>

`-a` = all solutions. For `solve satisfy`, MiniZinc prints every satisfying assignment, each separated by `----------`, with a final `==========` marker. For `solve minimize/maximize`, `-a` means "all intermediate incumbents" (see the MiniZinc docs on `-a` semantics).

</details>

Solutions live in `apps/mzn/ch07-tour/solutions/`. Try first.

## 11. Self-check

**Q1.** What does the MiniZinc → FlatZinc → solver pipeline look like, in three sentences?

<details><summary>Answer</summary>

You write a high-level `.mzn` model, optionally separating data into a `.dzn` file. The MiniZinc compiler *flattens* the model — substituting data, evaluating parameters, expanding comprehensions, decomposing globals the backend doesn't natively support — producing a `.fzn` file in a minimal language of primitive constraints. A backend solver (Gecode, Chuffed, CP-SAT, HiGHS, etc.) reads the `.fzn`, solves it, and outputs an assignment, which MiniZinc post-processes using the model's `output` section.

</details>

**Q2.** Why do the same `.mzn` timings differ across backends?

<details><summary>Answer</summary>

Different engines: Gecode is pure finite-domain CP with propagation-based search; Chuffed adds lazy clause generation (SAT-style learning); CP-SAT combines CP + SAT + LP with parallel portfolio; MIP solvers (HiGHS, Gurobi) operate on the LP relaxation and branch-and-cut. Each excels at different problem structures — LP-friendly problems go fast on MIP solvers; pure combinatorial with many small domains goes fast on Gecode; complex scheduling often needs CP-SAT's hybrid engine. The FlatZinc is the same; what happens after it isn't.

</details>

**Q3.** When does declarative modeling hurt performance?

<details><summary>Answer</summary>

When you need solver-specific features: custom branching strategies, warm-starts from hints, callback-based early stopping, probing queries, or incremental solve (fix a value, keep solving from where you left off). MiniZinc's cross-solver portability means it only exposes the lowest common denominator of features. If you need low-level control, drop down to the solver's native API. Also: the flattener sometimes introduces redundant variables the solver has to propagate over; aggressive manual modeling in CP-SAT's native API can be tighter.

</details>

**Q4.** Why does MiniZinc need both `.mzn` and `.dzn` files?

<details><summary>Answer</summary>

Separation of concerns: the `.mzn` is the model (the combinatorial structure — variables, constraints, objective), the `.dzn` is the instance data (the specific numbers). You can reuse one `.mzn` with hundreds of `.dzn` files (one per benchmark instance) without editing the model. The alternative — hardcoding values into the model — works for tutorials but not for real benchmarks or production.

</details>

**Q5.** What does `include "globals.mzn";` give you, and why might you include *only specific* globals instead?

<details><summary>Answer</summary>

It makes every global constraint in the MiniZinc library available (`all_different`, `cumulative`, `circuit`, `regular`, `inverse`, `lex_less`, `bin_packing`, ...). Including only specific ones (`include "alldifferent.mzn";`) keeps the flattening faster on huge models and avoids name collisions with user predicates. In practice most models do the blanket include — the cost is negligible.

</details>

## 12. What this unlocks

You can read and write MiniZinc well enough to prototype problems declaratively. Chapter 8 takes this further: write a small Nurse Scheduling instance in MiniZinc first — validate the *specification* there — then port it to CP-SAT (Python + Kotlin). That workflow becomes the default for anything non-trivial.

## 13. Further reading

- Nethercote et al., [*MiniZinc: Towards a Standard CP Modelling Language*](https://www.minizinc.org/paper-2007.pdf) — the original 2007 paper introducing the language.
- Stuckey, [*MiniZinc Handbook*](https://docs.minizinc.dev/en/stable/) — definitive reference; read chapters 2 (tour), 3 (basic modeling), 6 (globals).
- [MiniZinc Challenge](https://www.minizinc.org/challenge.html) — annual solver bake-off; the problem statements are great modeling exercises.
- [`docs/knowledge/minizinc/overview.md`](../knowledge/minizinc/overview.md) — our own tour (this chapter's source material).
- Marriott & Stuckey, [*Programming with Constraints*](https://mitpress.mit.edu/9780262133418/) — foundational textbook; chapters 1–3 establish CP modeling.

## Appendix A — Registering CP-SAT as a MiniZinc backend

1. Download the matching OR-Tools release: <https://github.com/google/or-tools/releases>. The archive contains `fzn-cp-sat` (the FlatZinc driver) and a `mznlib/` directory.
2. Copy `fzn-cp-sat` somewhere on your PATH (e.g. `/usr/local/bin`).
3. Create `~/.minizinc/solvers/com.google.or-tools.msc`:
   ```json
   {
     "id": "com.google.or-tools",
     "name": "OR Tools CP-SAT",
     "description": "Google OR-Tools",
     "version": "9.15.6755",
     "mznlib": "/usr/local/share/ortools/mznlib",
     "executable": "/usr/local/bin/fzn-cp-sat",
     "tags": ["cp", "int", "sat"],
     "stdFlags": ["-a","-n","-p","-s","-t"],
     "supportsMzn": false,
     "needsSolns2Out": true,
     "supportsFzn": true
   }
   ```
4. Confirm: `minizinc --solvers | grep or-tools`.
5. Test: `minizinc --solver com.google.or-tools queens.mzn`.

Copy `mznlib` from the release archive so CP-SAT's native globals are picked up over the default library; otherwise you'll get correct-but-slower decomposition.
