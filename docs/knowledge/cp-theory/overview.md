# Constraint Programming: A Primer

## 1. ELI5 Definition

Imagine you're trying to seat 10 guests at a dinner party, and some people refuse to sit next to each other, one couple must sit together, and grandma needs to be near the bathroom. Constraint Programming (CP) is a way of telling a computer the *rules* of your puzzle ("who can sit where") and letting it figure out a valid arrangement on its own — without you having to spell out *how* to search. You describe *what* must be true; the solver handles the *how*.

## 2. CSP vs COP

A **Constraint Satisfaction Problem (CSP)** asks: *is there any assignment of values to variables that satisfies all constraints?* A **Constraint Optimization Problem (COP)** asks: *among all valid assignments, which one minimizes (or maximizes) an objective function?*

- **CSP example:** Color a map of Europe with 4 colors so no two neighboring countries share a color.
- **COP example:** Schedule 50 jobs on 5 machines so all precedence rules are respected *and* the total makespan is minimized.

Formally, a CSP is a triple ⟨X, D, C⟩ where X is a set of variables, D the set of their domains, and C the set of constraints (https://en.wikipedia.org/wiki/Constraint_satisfaction_problem). A COP adds an objective function f: solutions → ℝ to be optimized.

## 3. Core Vocabulary

| Term | Definition |
|------|------------|
| **Variable** | An unknown whose value must be determined; e.g. `X = color of France`. |
| **Domain** | The finite (or interval) set of values a variable may take; e.g. `D(X) = {red, green, blue, yellow}`. |
| **Constraint** | A relation over a subset of variables restricting which value combinations are allowed; e.g. `X ≠ Y`. |
| **Assignment** | A mapping of (some or all) variables to specific values from their domains; *partial* if incomplete, *total* if every variable has a value. |
| **Solution** | A total assignment that satisfies every constraint simultaneously. |
| **Propagator** | An algorithm attached to a constraint that prunes values from domains that cannot appear in any solution; it narrows domains without enumerating assignments. |
| **Branching** | The decision step where the solver splits the problem (e.g. `X = 3` vs `X ≠ 3`) to explore alternatives. |
| **Search tree** | The implicit tree of all branching decisions explored during search; leaves are either solutions or dead ends. |
| **Consistency** | A property measuring how much local inference has been performed; stronger consistency ⇒ tighter domains ⇒ smaller search (https://en.wikipedia.org/wiki/Local_consistency). |
| **Global constraint** | A constraint capturing a combinatorial substructure over many variables (e.g. `AllDifferent(X₁…Xₙ)`), shipped with a dedicated efficient propagator (https://en.wikipedia.org/wiki/Global_constraint). |
| **Redundant constraint** | A constraint logically implied by others; it adds no solutions but can boost propagation and prune search faster. |
| **Symmetry breaking** | Extra constraints that eliminate equivalent-up-to-symmetry solutions (e.g. `X₁ ≤ X₂`) to avoid re-exploring the same essential configuration. |
| **Implied constraint** | Same idea as redundant: a deduced relation added explicitly to strengthen propagation. |
| **Channeling constraint** | A constraint linking two alternative viewpoints of the same problem (e.g. primal `X[i] = j` iff dual `Y[j] = i`) so propagation flows between them. |
| **Hall interval** | In AllDifferent reasoning, an interval `[a,b]` such that the number of variables whose domain fits inside `[a,b]` equals `b−a+1`; those variables must use exactly those values, forcing the rest to avoid them (Régin 1994; used by bounds-consistent AllDifferent). |

## 4. How a CP Solver Works Under the Hood

At its heart, a CP solver alternates two phases: **propagation** (cheap inference) and **search** (branching over remaining choices).

**The propagate-and-search loop:**
1. Push all constraints onto a propagation queue.
2. Run each propagator to remove impossible values from domains.
3. If propagation empties a domain → backtrack (this branch is dead).
4. If all variables are fixed → record solution.
5. Otherwise, *branch* on an unfixed variable (pick a variable, split its domain), and recurse.

**Key techniques:**

- **Forward checking** — the simplest propagation: after assigning `X = v`, remove `v` from the domains of variables directly constrained with `X`. Cheap but weak.
- **Arc consistency (AC-3)** — for every binary constraint `C(X,Y)`, ensure every value in `D(X)` has at least one supporting value in `D(Y)` (and vice versa). AC-3 is the textbook worst-case O(ed³) algorithm by Mackworth (1977); AC-4, AC-6, AC-2001 improve it (https://en.wikipedia.org/wiki/AC-3_algorithm).
- **Bounds consistency** — a weaker but cheaper form: only the min/max of each domain are checked for support. Standard for integer-interval domains.
- **Path consistency** — extends arc consistency to triples of variables; rarely enforced fully because it's expensive.
- **Branch-and-prune** — the overall paradigm: at each node, propagate to prune, then branch. Unlike MILP's branch-and-bound, CP's pruning is combinatorial (domain reduction), not LP-based.
- **Branch-and-bound for COP** — when optimizing, each new solution of cost `c*` adds a constraint `obj < c*` to prune the remaining search.
- **Restarts** — periodically abandon the current search tree and restart with a fresh variable ordering (often informed by conflicts). Critical for avoiding bad early decisions in large instances.
- **Conflict-driven learning / nogoods** — when a branch fails, analyze *why* and record a *nogood* (a forbidden partial assignment) to avoid re-exploring equivalent dead ends. This is borrowed from SAT solvers and matured into Lazy Clause Generation.

Variable and value ordering heuristics (e.g. `dom/wdeg`, `IBS`, activity-based) are often the single biggest determinant of solver performance.

## 5. Global Constraints

A **global constraint** captures a recurring combinatorial substructure and ships with a specialized filtering algorithm (https://en.wikipedia.org/wiki/Global_constraint). Why they matter: decomposing them into primitive binary/ternary constraints loses *structural* information, so the resulting propagation is much weaker. A dedicated propagator can, for instance, exploit matching theory, flow networks, or automata to prune domains that a decomposition cannot reach.

| Constraint | Encodes | Why a specialized propagator wins |
|------------|---------|-----------------------------------|
| **AllDifferent(X₁…Xₙ)** | All variables must take pairwise distinct values. | Régin's 1994 algorithm uses bipartite matching to enforce *domain consistency* in O(n²·d); a naive `O(n²)` clique of `≠` constraints only gives arc consistency on pairs and misses the global pigeonhole. |
| **Cumulative(tasks, capacity)** | At every time point, the sum of resource demands of running tasks ≤ capacity. | Time-table, edge-finding, and energetic reasoning propagators prune far more than summing per-time-point `≤` constraints; essential for scheduling. |
| **Circuit(next[1..n])** | Variables define a single Hamiltonian circuit over n nodes. | Uses graph reachability / SCC algorithms to forbid sub-tours; a decomposition cannot detect that a partial assignment already creates a small cycle. |
| **Element(i, array, v)** | `v = array[i]` where `i` is itself a variable (array indexing into a CP model). | Enables table-lookups and piecewise relations with tight propagation in both directions. |
| **Table(X, allowedTuples)** | The assignment of `X` must equal one of the listed tuples (extensional constraint). | Compressed-table, MDD-based, and STR propagators achieve domain consistency efficiently, often beating hand-coded decompositions. |
| **NoOverlap / Disjunctive(tasks)** | A set of intervals must not overlap on a single resource. | Edge-finding and not-first/not-last rules enforce strong pruning in O(n log n), crucial for job-shop. |
| **GCC — Global Cardinality Constraint** | For each value `v`, the number of variables taking `v` lies in `[l_v, u_v]`. | Generalizes AllDifferent; Régin's flow-based algorithm enforces domain consistency in polynomial time. |
| **Regular(X, automaton)** | The sequence X₁…Xₙ must be accepted by a given finite automaton. | Pesant's algorithm propagates via a layered graph in O(n·|Q|·|Σ|); perfect for rostering and sequencing rules that decomposition would blow up. |

MiniZinc and the Global Constraint Catalogue list ~400 named global constraints (https://sofdem.github.io/gccat/).

## 6. CP vs MILP vs SAT vs Heuristics

| Dimension | CP | MILP | SAT | Metaheuristics |
|-----------|----|----|-----|----------------|
| **Variables** | Discrete (int, bool, set, interval); small expressive domains | Continuous + integer | Boolean only | Any |
| **Objective** | Optional; many solvers bias toward feasibility | First-class (linear) | None (pure SAT); MaxSAT adds it | First-class |
| **Inference** | Domain propagation per constraint | LP relaxation + cutting planes | Unit propagation + CDCL learning | None (sample + move) |
| **Sweet spot** | Scheduling, rostering, routing with side constraints, configuration, puzzles with rich combinatorial structure | Problems with strong linear structure and tight LP relaxations | Pure boolean logic, verification, planning as SAT | Very large instances where proof of optimality isn't required |
| **Weak spot** | Weak objective bounding; struggles when the problem is "mostly linear" | Non-linear / disjunctive / global combinatorial structure explodes model size | No integer arithmetic; must encode everything to booleans | No proof, no completeness |

**Honest tradeoffs.** CP excels when the problem has rich, non-linear, scheduling-like structure that maps naturally to global constraints. MILP excels when a good LP relaxation exists and objective-driven pruning dominates. SAT excels on purely boolean combinatorial reasoning where CDCL's learning is unmatched.

**Lazy Clause Generation (LCG)** is the bridge between CP and SAT (Ohrimenko, Stuckey, Codish 2009). An LCG solver runs CP-style propagators, but each propagation step lazily generates SAT clauses explaining the deduction. Those clauses feed a SAT-style CDCL learning engine. The result: strong CP-style propagation *plus* SAT-style nogood learning and restarts. Chuffed and OR-Tools CP-SAT are the flagship LCG/CP-SAT solvers, and they dominate MiniZinc Challenge results in recent years.

## 7. Modeling Guidance: What Makes a Good CP Model

1. **Use global constraints whenever they fit.** `AllDifferent` over ten variables crushes ten binary `≠` constraints. If you have a matching, cycle, sequencing, or cumulative pattern, search the global-constraint catalogue first.
2. **Tight domains and bounds.** A variable declared `0..1000` when 0..10 would do wastes propagation effort — shrink domains with problem-specific reasoning before solving.
3. **Add redundant / implied constraints.** Sum constraints over rows *and* columns, total-work arguments in scheduling, pigeonhole arguments — these don't change solutions but supercharge propagation.
4. **Break symmetry.** If variables (or values) are interchangeable, add lex-ordering or value-precedence constraints to forbid permuted duplicates; otherwise the solver re-explores the same solution n! times.
5. **Consider dual / multiple viewpoints with channeling.** Modeling both "which job goes in which slot" and "which slot holds which job" with a channel constraint often combines the strengths of each model.
6. **Pick branching with care.** `first-fail` (smallest-domain-first) and `dom/wdeg` (conflict-weighted) are strong defaults. For scheduling, branch on precedences, not start times.
7. **Match search to structure.** For optimization, think about bounding: can you add a simple LP-style bound as a redundant constraint? Can you warm-start with a heuristic solution to prune early?

## 8. Canonical Teaching Problems

- **N-Queens** — place N non-attacking queens; teaches AllDifferent on rows, columns, and the two diagonal encodings, plus symmetry breaking.
- **Cryptarithmetic (SEND+MORE=MONEY)** — teaches domain modeling, AllDifferent over digits, and column-wise carry decomposition.
- **Graph coloring** — teaches value symmetry breaking and the interplay between clique reasoning and propagation.
- **Map coloring** — the 4-color theorem reduced to CSP; the textbook introduction to arc consistency.
- **Magic square** — sums of rows/columns/diagonals all equal; teaches redundant sum constraints and AllDifferent together.
- **Job-shop scheduling** — teaches NoOverlap/Disjunctive, Cumulative, precedence, and objective-driven branch-and-bound on makespan.
- **Bin packing** — teaches Cumulative as a relaxation, lower bounds from sum-of-weights, and the overlap with MILP.

## 9. Annotated Bibliography

Ordered from most beginner-friendly to most advanced.

1. **MiniZinc Handbook** — https://www.minizinc.org/doc-latest/en/ — The gentlest hands-on entry point: a modeling language that compiles to most solvers, with a tutorial-style reference. Start here.
2. **Wikipedia: Constraint programming** — https://en.wikipedia.org/wiki/Constraint_programming — Solid high-level definitions, history, and a list of systems. Good for vocabulary.
3. **Wikipedia: Constraint satisfaction problem** — https://en.wikipedia.org/wiki/Constraint_satisfaction_problem — Formal definition of ⟨X,D,C⟩, complexity notes, solution methods.
4. **Wikipedia: Local consistency** — https://en.wikipedia.org/wiki/Local_consistency — Clear explanations of node, arc, path, and k-consistency; a prerequisite for reading propagator papers.
5. **Wikipedia: Global constraint** — https://en.wikipedia.org/wiki/Global_constraint — Short but accurate; points to the catalogue.
6. **Global Constraint Catalogue (Beldiceanu et al.)** — https://sofdem.github.io/gccat/ — Reference catalogue of ~400 global constraints with semantics, propagators, and references. Dense but canonical.
7. **OR-Tools CP-SAT documentation** — https://developers.google.com/optimization/cp — Practical, well-written intro to Google's LCG-style solver; lots of worked examples in Python and C++.
8. **Pascal Van Hentenryck, *Constraint Satisfaction in Logic Programming*** (1989, MIT Press) — The foundational book that launched CP as a discipline; still valuable for principles, now dated on algorithms.
9. **Handbook of Constraint Programming**, Rossi, van Beek, Walsh (eds.), Elsevier 2006 — https://www.sciencedirect.com/handbook/handbook-of-constraint-programming — The definitive reference: chapters on consistency, search, global constraints, symmetry, applications. Long, but each chapter is standalone.
10. **Régin, J.-C., *A filtering algorithm for constraints of difference in CSPs*** (AAAI 1994) — The original domain-consistent AllDifferent propagator via bipartite matching; the prototypical "why global constraints win" paper.
11. **Ohrimenko, Stuckey, Codish, *Propagation via lazy clause generation*** (Constraints, 2009) — https://link.springer.com/article/10.1007/s10601-008-9064-x — The LCG paper that fused CP propagation with SAT-style learning; essential for understanding modern CP-SAT solvers.
12. **MiniZinc Challenge results** — https://www.minizinc.org/challenge/ — Annual benchmark comparing CP solvers; reading the results and winning submissions is the fastest way to see which techniques matter in practice today.

*Uncertainty note:* I was unable to confirm specific 2023–2026 CP survey papers in this research pass. The *Handbook of Constraint Programming* (2006) remains the canonical survey; for recent developments the MiniZinc Challenge results and proceedings of the CP conference (https://cp-conference.org/) are the best primary sources.
