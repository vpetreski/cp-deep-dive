# Chapter 01 — What is Constraint Programming?

> **Phase 1: CP theory (ELI5 → vocabulary)** · Estimated: ~2h · Status: ready-to-start · Last updated: 2026-04-19

## Goal

After this chapter you can explain constraint programming to a smart friend in three sentences, distinguish a CSP from a COP, and recognize when a problem is "CP-shaped" versus when it belongs to MILP, SAT, or a metaheuristic. No code — just mental models and vocabulary you'll carry through the rest of the project.

## Before you start

- **Prerequisites:** curiosity; basic high-school algebra. No solver experience required.
- **Required reading:** [`knowledge/cp-theory/overview.md §1-5`](../knowledge/cp-theory/overview.md). Skim §7 and §8. You don't need §9 (bibliography) now — come back for it later.
- **Environment:** nothing to install. Pen and paper. Optionally, the MiniZinc IDE or paper-and-pencil Sudoku.

## Concepts introduced this chapter

- **Variable** — an unknown whose value a solver must choose (e.g. "what row does the queen in column 3 go on?").
- **Domain** — the finite set of legal values a variable can take (`{1, 2, ..., n}` for an n-queens row).
- **Constraint** — a rule saying which combinations of values are allowed (`rowA != rowB`).
- **Assignment** — a (possibly partial) mapping of variables to values, candidate for being a solution.
- **Feasible solution** — a total assignment satisfying every constraint.
- **CSP vs COP** — "is there a solution?" vs "find the best solution" (the latter adds an *objective function*).
- **Search space** — the product of all domains; the raw sea of candidates the solver slices up.
- **Propagation** — cheap local inference that eliminates values no feasible solution can use.
- **Search / branching** — the decision tree the solver explores when propagation alone can't finish.
- **Global constraint** — a named pattern over many variables (like `AllDifferent`) with a specialized, more powerful propagator than the logical equivalent in small pieces.
- **NP-hard** — shorthand for "in the worst case, there's no known algorithm that scales polynomially." Most interesting CP problems are NP-hard; the solver finesses, it doesn't cheat.

## 1. Intuition — you, the nurse manager, with a spreadsheet

It's Wednesday morning. You run a small ward: **3 nurses** — Alice, Bob, Carol — across **3 days** — Mon, Tue, Wed. Each day needs **exactly one nurse on shift**. Easy, right? Now layer on the rules:

- Alice refuses to work Monday (dentist).
- Bob can't work two days in a row.
- Carol prefers weekends — too bad, but she's willing to cover Wednesday.

You grab a spreadsheet. Cells are `(nurse, day)`. You try to fill it by hand.

**Attempt 1 — brute force.** You could enumerate every possible way to put one of {A, B, C} into each of {Mon, Tue, Wed}. That's 3 × 3 × 3 = 27 candidates. For each, you check the rules. Fine for this toy; terrible for 30 nurses × 28 days (4^840 candidates — more than atoms in the observable universe). Brute force doesn't scale.

**Attempt 2 — rule-first reasoning.** Look at Monday. Alice is out. So Monday has to be **B or C**. Now Tuesday. If you chose B for Monday, Bob can't work Tuesday (no back-to-back) — so Tuesday has to be A or C. See what happened? Choosing B on Monday shrinks Tuesday's options from 3 down to 2, without you exhaustively trying every Tuesday candidate. That's *propagation*: a cheap local inference from one decision to another.

**Attempt 3 — a solver.** You describe the problem — three variables `Mon, Tue, Wed ∈ {A, B, C}`, a few constraints — and hand it to a constraint solver. The solver alternates two phases on its own: **propagate** (shrink domains with inferences like the one above) and **branch** (when no more inferences are available, try `Mon = B`, recurse; if that fails, backtrack and try `Mon = C`). It never enumerates the whole 27-candidate cube. On a 30-nurse problem, it does the same trick and finishes in seconds, because the constraints heavily *prune* the search.

That's constraint programming in one sentence: **you describe what the rules are; the solver figures out which values the rules allow, pruning as it goes.**

Analogy: Sudoku. When you see a row with an 8, you cross 8 off every other cell in that row, column, and box. You haven't solved anything — you've *propagated*. Most of solving a Sudoku is propagation; only in tough puzzles do you have to guess-and-check (that's *branching*). CP solvers formalize and automate both.

## 2. Formal definition — CSPs and COPs

A *Constraint Satisfaction Problem* is a triple `⟨X, D, C⟩`:

| Symbol | Meaning | Example (3-nurse problem) |
|---|---|---|
| `X` | Set of variables | `{Mon, Tue, Wed}` |
| `D` | Domain function: for each `x ∈ X`, `D(x)` is the set of legal values | `D(Mon) = D(Tue) = D(Wed) = {A, B, C}` |
| `C` | Set of constraints (relations over subsets of `X`) | `Mon != A`; `(Mon = B) ⇒ (Tue != B)`; etc. |

A **solution** is an assignment `X → ⋃ D` giving every variable a value such that every constraint holds.

A *Constraint Optimization Problem* (COP) adds a fourth piece: an **objective function** `f : solutions → ℝ` to minimize or maximize. Now "solved" means not just feasible, but *the best* feasible assignment (or, more honestly, the best the solver can prove within time budget — see Chapter 5).

```
CSP:  ⟨X, D, C⟩           →  "any solution"
COP:  ⟨X, D, C, f⟩         →  "best solution"
```

**Search space size.** Without constraints, the space of total assignments has `∏ₓ |D(x)|` candidates. For 3 variables × 3 values, that's 27. For 30 × 28 × 4 shifts, `4^(30·28) ≈ 10^506` — astronomical. The entire purpose of constraints and propagation is to *never visit* most of this space.

### How a CP solver works (zoomed out)

```
┌────────────────────────────────────────────────────────┐
│  Solve(CSP):                                           │
│    loop:                                               │
│      propagate()                  ← shrink domains     │
│      if any D(x) = ∅:   backtrack  ← dead end          │
│      if every x fixed:   record & (for COP) improve f  │
│      else:              branch on some x               │
└────────────────────────────────────────────────────────┘
```

**Propagation** is the engine. Each constraint has a *propagator*: a small algorithm that, given current domains, removes values that clearly can't participate in any solution. Stronger propagation = more values killed per step = smaller search.

**Global constraints** package common patterns (`AllDifferent(x₁, …, xₙ)`, `Cumulative(tasks, capacity)`, `Circuit(next)`) with specialized propagators that are dramatically stronger than writing the same rule in small pieces. More in §3 and in [`knowledge/cp-theory/overview.md §5`](../knowledge/cp-theory/overview.md).

## 3. Worked example by hand — 4-Queens

Place 4 non-attacking queens on a 4×4 board: one queen per column, no two in the same row, no two on a diagonal.

**Model it:**

- Variables: `Q₁, Q₂, Q₃, Q₄` — one per column. `Qᵢ` is the row of the queen in column `i`.
- Domains: `D(Qᵢ) = {1, 2, 3, 4}`.
- Constraints:
  - `AllDifferent(Q₁, Q₂, Q₃, Q₄)` — no two queens share a row.
  - `AllDifferent(Q₁+1, Q₂+2, Q₃+3, Q₄+4)` — no two on the `\` diagonal.
  - `AllDifferent(Q₁−1, Q₂−2, Q₃−3, Q₄−4)` — no two on the `/` diagonal.

**Solve it by hand with *naked single* + *forward checking*.** Start with everyone at `{1,2,3,4}`.

```
Initial:  Q₁∈{1,2,3,4}  Q₂∈{1,2,3,4}  Q₃∈{1,2,3,4}  Q₄∈{1,2,3,4}
```

Branch: **try `Q₁ = 1`**. Forward-check each constraint:

- Rows differ → remove `1` from `Q₂, Q₃, Q₄`.
- `\` diagonals: `Q₁+1 = 2`. Remove: `Q₂+2 ≠ 2` means `Q₂ ≠ 0` (no effect); `Q₃+3 ≠ 2` means `Q₃ ≠ −1` (no effect); `Q₄+4 ≠ 2` means `Q₄ ≠ −2` (no effect). We also need `Q₂ ≠ Q₁ + (2-1) = 2`, so remove `2` from `Q₂`. Similarly `Q₃ ≠ 1+2 = 3`, `Q₄ ≠ 1+3 = 4`.
- `/` diagonals: `Q₂ ≠ Q₁ − 1 = 0` (no effect); `Q₃ ≠ -1`, `Q₄ ≠ -2` (no effect).

```
After Q₁=1:
Q₂∈{3,4}   Q₃∈{2,4}   Q₄∈{2,3}
```

Branch: **try `Q₂ = 3`**. Propagate:

- Rows → remove `3` from `Q₃, Q₄`. `Q₃∈{2,4}`, `Q₄∈{2}`.
- `\` diagonal: `Q₂+2 = 5`, so `Q₃ ≠ 5-3=2`, `Q₄ ≠ 5-4=1`. `Q₃∈{4}`, `Q₄∈{2}`.
- `/` diagonal: `Q₂−2 = 1`, so `Q₃ ≠ 1+3=4`. `Q₃ = ∅` → **dead end**.

Backtrack: **try `Q₂ = 4`**. Propagate:

- Rows → remove `4`. `Q₃∈{2}`, `Q₄∈{2,3}`.
- `\`: `Q₂+2 = 6`, so `Q₃ ≠ 6-3=3` (already gone), `Q₄ ≠ 6-4=2`. `Q₄∈{3}`.
- `/`: `Q₂−2 = 2`, so `Q₃ ≠ 2+3=5` (no effect), `Q₄ ≠ 2+4=6` (no effect).

```
After Q₁=1, Q₂=4:
Q₃ = 2   Q₄ = 3
```

Check all three `AllDifferent`s: rows `{1,4,2,3}` — distinct. `\`-diag `{2,6,5,7}` — distinct. `/`-diag `{0,2,-1,-1}` — oops, `Q₃−3 = -1` and `Q₄−4 = -1`. Dead end. Backtrack further (try `Q₁ = 2`, etc.), and you'll eventually find `(2, 4, 1, 3)` and `(3, 1, 4, 2)` — the two 4-queens solutions.

You just ran the CP algorithm by hand. A real solver does the same thing, millions of times, with much stronger propagators and much smarter variable-ordering heuristics (see Chapter 4 for the solver's version of this story, and the Chapter 4 exercise "N=200 queens — what dominates?").

## 4. Python implementation

No code this chapter. You haven't installed a solver yet — that's Chapter 2. The goal here is vocabulary and intuition; running CP-SAT prematurely only confuses "what is propagation" with "what does `model.add` do." Next chapter you'll run real models in both languages.

## 5. Kotlin implementation

Same — no code yet. Chapter 2 is where both languages come to life.

## 6. MiniZinc implementation (optional curiosity)

If you want to see what the 4-queens model looks like in a declarative modeling language, skip ahead to [`knowledge/minizinc/overview.md §5`](../knowledge/minizinc/overview.md). You'll meet this formally in Chapter 7 — no need to run it now.

## 7. Comparison & takeaways

**What you learned:**

- **CP flips programming on its head.** In imperative code you write *how* to compute. In CP you write *what must be true* and hand it off.
- **Constraints are cheap; search is expensive.** A good model leans on propagation to kill the search space before branching starts. Most CP skill is about writing models that propagate well.
- **Global constraints are secret weapons.** `AllDifferent(x₁…xₙ)` is not the same as `n·(n−1)/2` pairwise `≠` constraints — it propagates more, because the specialized algorithm knows about *pigeonhole* and *bipartite matching* globally; pairwise `≠` only sees two variables at a time.
- **NP-hard doesn't mean hopeless.** Modern solvers routinely handle problems with millions of variables *when structure exists*. The trick is to expose the structure via tight domains, redundant constraints, and global constraints.
- **CP isn't always the answer.** Problems with strong linear relaxations (network flow, LP-shaped scheduling) often go to MILP. Pure boolean logic with no integer arithmetic often goes to SAT. Massive problems where proof-of-optimality is unaffordable go to metaheuristics. CP earns the job when the problem has rich, non-linear, combinatorial structure — exactly the nurse scheduling problem we're headed for.

**Key insight.** *The search space is enormous; the set of feasible solutions inside it is (usually) tiny; propagation is the tool that gets you from the first to the second without visiting everything in between.* Everything else in CP — variables, global constraints, search heuristics — is in service of that one idea.

## 8. Exercises

### Exercise 1.1 — Sudoku by hand, naming the rules

**Problem.** Take the following mini-Sudoku (4×4 grid with 2×2 boxes, digits 1-4):

```
| 1 . | . 4 |
| . 3 | 1 . |
+-----+-----+
| . 1 | 3 . |
| 4 . | . 2 |
```

Solve it by hand. Every time you fill a cell, write down which inference rule you applied. Recognized rules:

- **Naked single** — only one digit fits in this cell given its row/column/box.
- **Hidden single** — this digit has only one legal cell in its row (or column, or box).
- **Elimination** — ruling out a digit in a cell because some constraint excludes it.

**Acceptance criteria:** you finish the puzzle with a justified reason for every cell, and no guesses (if you reach a step with no rule applying, state that — that's branching territory).

<details><summary>Hint</summary>

Start with the column containing `.1., .3., .1., 4.` — column 2 in 1-indexed. Which digits are missing? Where can each go?

</details>

### Exercise 1.2 — Write a plain-English spec for a tiny NSP

**Problem.** Describe, in plain English (no code, no math), the constraints for scheduling 3 nurses over 3 days with exactly one nurse on shift per day, Alice unable to work Mondays, Bob unable to work two days in a row. Write it as if handing it to another person who'll implement it.

**Expected output shape:** a bulleted list with sections `Variables:`, `Constraints:`, `What counts as a solution:`. Two paragraphs maximum.

**Acceptance criteria:** another human reads it and can hand-check any candidate schedule for validity using only your spec.

<details><summary>Hint</summary>

For each day ask "who's on?" That's your variable. Each domain is {Alice, Bob, Carol}. Then write down what combinations of (day, nurse) are and aren't allowed.

</details>

### Exercise 1.3 — Why `AllDifferent` beats pairwise `!=`

**Problem.** Consider three variables `x, y, z ∈ {1, 2}`. In a pairwise-`!=` model you post `x ≠ y`, `y ≠ z`, `x ≠ z`. In a global-constraint model you post `AllDifferent(x, y, z)`.

Argue in one paragraph:

(a) Why is the pairwise model *satisfiable* after trivial propagation but actually *infeasible*?
(b) What does `AllDifferent`'s propagator detect that pairwise `≠` doesn't?

**Acceptance criteria:** you mention the *pigeonhole principle* and *domain consistency* explicitly. Answer is 4-8 sentences.

<details><summary>Hint</summary>

Count: three variables, but only two possible values. What happens in a pairwise model before any branching? What happens when `AllDifferent` looks at all three variables and their combined domains at once?

</details>

### Exercise 1.4 — Picking the tool

**Problem.** For each of the following, name whether you'd reach for **CP-SAT**, **MILP (HiGHS/Gurobi)**, **SAT**, or a **metaheuristic (simulated annealing / tabu)** first. One-sentence justification each.

1. Assign 200 students to 50 project teams with compatibility preferences and team-size caps.
2. Solve a Boolean formula encoding "does this CPU pipeline have a race condition?" (300,000 clauses).
3. Price optimization over continuous quantities with a few hundred linear constraints.
4. Route 15 trucks over 200 delivery stops to minimize total distance, with time-windows and vehicle capacities.
5. Schedule 3000 jobs over a week across 50 parallel machines with precedence rules.
6. Solve a 9×9 Sudoku.

**Acceptance criteria:** your pick isn't "CP-SAT for everything" — at least two different tools appear in your answers.

<details><summary>Hint</summary>

SAT is for pure boolean problems. MILP shines when variables are continuous and constraints are linear. Metaheuristics shine at massive scale where proof of optimality is a luxury. CP-SAT wins when combinatorial structure + integers + side constraints dominate.

</details>

Solutions for these are in `apps/py-cp-sat/ch01-what-is-cp/solutions/` (only 1.1 has a deterministic solution; the rest are short-answer — discussion-style rubrics live there for self-grading). Try them yourself before peeking.

## 9. Self-check

**Q1.** In one sentence, what's the difference between a CSP and a COP?

<details><summary>Answer</summary>

A CSP asks "is there any assignment satisfying all constraints?"; a COP adds an objective function and asks "which feasible assignment minimizes (or maximizes) it?"

</details>

**Q2.** What does *propagation* do that *search* doesn't?

<details><summary>Answer</summary>

Propagation shrinks variable domains by cheap, constraint-local inference *without branching* — it eliminates values that cannot participate in any solution given the current state. Search, by contrast, makes a decision (a guess), commits to a branch, and recurses; if propagation can't eliminate all non-solutions on its own, search is what explores the remaining possibilities.

</details>

**Q3.** Give three problem shapes where CP-SAT is the right first tool, and three where a MILP solver would probably win.

<details><summary>Answer</summary>

*CP-SAT wins:* job-shop scheduling (rich disjunctive/cumulative structure), nurse rostering (lots of combinatorial side constraints), configuration/puzzles like N-queens or cryptarithmetic (dense global-constraint structure). *MILP wins:* network-flow LPs (strong LP relaxations), portfolio optimization with continuous quantities and linear risk constraints, facility-location problems where LP bounds dominate combinatorial reasoning. The rule of thumb: the stronger the LP relaxation, the more MILP shines; the richer the combinatorial side structure, the more CP shines.

</details>

**Q4.** Why is `AllDifferent(x₁, …, xₙ)` better than a pile of pairwise `!=` constraints?

<details><summary>Answer</summary>

`AllDifferent` has a specialized propagator (Régin's 1994 algorithm, based on bipartite matching) that enforces *domain consistency*: it looks at all n variables and their domains globally and removes any value that cannot appear in any solution of the whole `AllDifferent` constraint. Pairwise `!=` constraints only ever see two variables at a time — they miss pigeonhole arguments like "three variables, combined domain size 2, therefore infeasible" that the global propagator catches instantly.

</details>

**Q5.** A friend says, "Just write a for-loop that tries every assignment." What's wrong with that for nurse scheduling with 30 nurses, 28 days, 4 shift types?

<details><summary>Answer</summary>

Brute force visits `4^(30·28) ≈ 10^506` assignments — astronomically more than could ever be examined. Worse, each one must be checked against every constraint. CP avoids this by *propagating* constraints to shrink the effective search space before any branching, and by branching only where propagation alone can't finish. In practice a CP-SAT solver handles this instance in seconds because the constraints are tight.

</details>

## 10. What this unlocks

With vocabulary and intuition locked in, Chapter 2 is where you build and run your first real CP-SAT model in both Python and Kotlin.

## 11. Further reading

- Wikipedia, [Constraint programming](https://en.wikipedia.org/wiki/Constraint_programming) — the high-level landing page; start here if a term in this chapter is unfamiliar.
- Wikipedia, [Constraint satisfaction problem](https://en.wikipedia.org/wiki/Constraint_satisfaction_problem) — formal `⟨X, D, C⟩` definition, complexity notes, standard solution methods.
- MiniZinc Handbook, [Getting Started chapter](https://docs.minizinc.dev/en/stable/modelling.html) — the gentlest, most hands-on introduction to CP; even without installing MiniZinc, the first two chapters are excellent conceptual reading.
- Rossi, van Beek, Walsh (eds.), [*Handbook of Constraint Programming*](https://www.sciencedirect.com/handbook/handbook-of-constraint-programming), Elsevier 2006 — chapters 1 and 2 are the canonical academic intro if you want the full depth later.
- Régin, "[A filtering algorithm for constraints of difference in CSPs](https://www.aaai.org/Papers/AAAI/1994/AAAI94-055.pdf)," AAAI 1994 — the prototypical "global constraint with a smart propagator" paper. Worth skimming *after* Chapter 4 when you have `AllDifferent` under your belt.
- Google OR-Tools, [CP overview](https://developers.google.com/optimization/cp) — short marketing-ish page; useful for tomorrow's install-and-hello-world.
