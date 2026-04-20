# Constraint & Optimization Ecosystem Overview

A survey of the tools that surround Google OR-Tools CP-SAT and MiniZinc, so you know what exists and when each shines. Maintenance status verified against GitHub on 2026-04-19.

## 1. Choco Solver

Java-native constraint programming library developed at IMT Atlantique. Ships a rich global-constraint catalogue (AllDifferent, Cumulative, Regular, Circuit, etc.), an explanation engine, and a black-box search with large neighborhood search built-in. Distinct because it's the most serious CP solver that's idiomatic in Java rather than bolted on — you get first-class Java APIs, Maven artifacts, and no JNI dance.

- **Languages:** Java (primary), Kotlin, Scala interop; no official Python bindings (unofficial PyChoco exists).
- **License:** BSD 4-Clause.
- **Maintenance (2026):** Active — last push 2026-04-17, 764 stars ([github.com/chocoteam/choco-solver](https://github.com/chocoteam/choco-solver)).
- **vs CP-SAT:** CP-SAT generally wins on raw performance (it's a CP/SAT hybrid with lazy clause generation and aggressive presolve) and on optimization with large integer-heavy objectives. Choco is the better pick when you live in the JVM, need explanations/justifications for decisions, or want fine-grained control over search strategy. CP-SAT has no explanation API; Choco does.

## 2. Gecode

C++ generic constraint development environment, historically one of the reference implementations in CP research. Used as a backend for many MiniZinc installations for years. Architecturally notable for its copying-based search (instead of trailing) and its propagator-event model.

- **Languages:** C++ (primary), MiniZinc (as a solver), Python via experimental `gecode-python`.
- **License:** MIT.
- **Maintenance (2026):** Still maintained — last push 2026-02-23, 323 stars ([github.com/Gecode/gecode](https://github.com/Gecode/gecode)) — but the cadence has slowed compared to its 2010s heyday. Development is now more caretaker than headline; major research effort moved elsewhere.
- **vs CP-SAT:** Gecode is a textbook CP solver; CP-SAT is a CP/SAT hybrid with learning. On pure-CP problems Gecode can be competitive, but on anything where nogood learning helps (scheduling, rostering, packing) CP-SAT usually dominates. Gecode remains important as a research baseline and a MiniZinc backend.

## 3. Chuffed

Lazy Clause Generation (LCG) constraint solver — a CP front-end with a SAT-style conflict-driven clause-learning engine underneath. Originated in Peter Stuckey's group at Monash. Has repeatedly topped the MiniZinc Challenge, especially on scheduling-shaped benchmarks.

- **Languages:** C++; used almost exclusively through MiniZinc (FlatZinc interface).
- **License:** MIT.
- **Maintenance (2026):** Active but low-volume — last push 2026-03-17, 122 stars ([github.com/chuffed/chuffed](https://github.com/chuffed/chuffed)). A small team keeps it alive; it is not an industrial product.
- **vs CP-SAT:** Chuffed and CP-SAT are philosophically the closest pair in this list — both combine CP propagators with SAT learning. CP-SAT has more engineering muscle behind it (Google team, parallel search, LP relaxations, symmetry breaking), better Python ergonomics, and a commercial-friendly license. Chuffed is still worth a try as a MiniZinc backend on problems where LCG-style learning on specific global constraints pays off; on many benchmarks they're within 2x of each other.

## 4. Timefold (formerly OptaPlanner)

Metaheuristic/local-search planner for JVM applications. Fork of OptaPlanner, created after Red Hat wound down OptaPlanner investment in 2023. Not a pure CP solver — it's constraint-based scheduling via incremental score calculation, construction heuristics, and local search (tabu, simulated annealing, late acceptance). The "Constraint Streams" API expresses constraints as Java streams with penalties/rewards.

- **Languages:** Java, Kotlin; Python support added in Timefold Solver for Python.
- **License:** Apache 2.0 (community edition); Timefold Inc. sells a commercial "Enterprise" edition with additional features.
- **Maintenance (2026):** Very active — last push 2026-04-17, 1,589 stars ([github.com/TimefoldAI/timefold-solver](https://github.com/TimefoldAI/timefold-solver)). Backed by a VC-funded company.
- **vs CP-SAT:** Different paradigm. CP-SAT is exact and can prove optimality; Timefold is heuristic and returns best-found solutions. For vehicle routing, large-scale employee rostering with soft constraints, and problems where you need a good solution fast on 10k+ entities, Timefold often wins in practice because it scales better and the Java integration is painless. CP-SAT wins when you need a proof, when the model has lots of tricky logical structure, or when the problem is small-to-medium and you want the guaranteed best answer.

## 5. Z3

Microsoft Research's SMT (Satisfiability Modulo Theories) solver. Not primarily a CP tool, but it competes in the same space when problems blend logic, arithmetic, bitvectors, arrays, and uninterpreted functions. Workhorse of program verification, symbolic execution (angr, KLEE), and some puzzle/config problems. Optimization via Z3's `Optimize` interface (MaxSMT, Pareto).

- **Languages:** C++, Python (via `z3-solver` PyPI), .NET, Java, OCaml, Julia.
- **License:** MIT.
- **Maintenance (2026):** Extremely active — last push 2026-04-19, 12,170 stars ([github.com/Z3Prover/z3](https://github.com/Z3Prover/z3)).
- **vs CP-SAT:** Different target. Z3 shines when the problem naturally speaks the language of quantifiers, bitvectors, or uninterpreted functions — classic scheduling with integer cost functions and large domains is not its sweet spot. CP-SAT beats Z3 comfortably on integer scheduling, bin packing, and most combinatorial optimization. Z3 beats CP-SAT when you need first-order logic, theory combinations, or you're verifying software.

## 6. MILP Solvers: SCIP, HiGHS, CBC, Gurobi, CPLEX

Mixed-Integer Linear Programming. Different modeling paradigm: everything becomes linear inequalities over continuous and integer variables. The solvers use branch-and-cut with heavy LP relaxations.

| Solver | License | Status (2026) |
|---|---|---|
| **Gurobi** | Commercial (free academic) | The speed leader; dominant in industry |
| **CPLEX** | Commercial (IBM) | Still shipping, slowly ceding ground to Gurobi |
| **SCIP** | Apache 2.0 (since v9) | Active, leading open-source MILP+CP hybrid ([github.com/scipopt/scip](https://github.com/scipopt/scip), last push 2026-04-17) |
| **HiGHS** | MIT | Very active ([github.com/ERGO-Code/HiGHS](https://github.com/ERGO-Code/HiGHS), last push 2026-04-17); now the default MILP backend in many Python stacks |
| **CBC** | EPL 2.0 | Maintained but dated; HiGHS has largely replaced it as the open-source default ([github.com/coin-or/Cbc](https://github.com/coin-or/Cbc)) |

**Used for nurse scheduling?** Yes. There's a long MILP literature on nurse rostering — classic papers from the 2000s formulate it as a set-covering or set-partitioning MILP and solve with CPLEX/Gurobi. Commercial workforce-management products still often use MILP under the hood.

**When CP beats MILP on rostering:**
- Dense logical constraints ("no more than 2 night shifts in any 7-day window") blow up MILP formulations with auxiliary variables and big-M constraints; CP expresses them natively via global constraints like `sliding_sum` or `regular`.
- Shift-pattern constraints expressed as automata/regular expressions — trivial in CP, painful in MILP.
- Propagation on `AllDifferent`, `Cumulative`, `Circuit` is stronger than LP relaxation for small-domain integer problems.

**When MILP still wins:** Large problems where the LP relaxation is tight (classic bin packing with many items, network flow, facility location). If your objective is purely linear and continuous relaxation is informative, Gurobi will likely beat CP-SAT.

## 7. Modeling Layers: Pyomo, CVXPY, JuMP

None of these are solvers — they are modeling front-ends that generate problem files and dispatch to a backend.

- **Pyomo** (Python) — Most general; supports LP, MILP, NLP, MINLP, SP, PDE. Works with HiGHS, CBC, GLPK, Gurobi, CPLEX, Ipopt, and more. Very active ([github.com/Pyomo/pyomo](https://github.com/Pyomo/pyomo), 2,443 stars, last push 2026-04-16). Sandia-backed.
- **CVXPY** (Python) — Specialized for convex optimization (DCP). Excellent for LP/QP/SOCP/SDP; not a good fit for combinatorial CP. Very active ([github.com/cvxpy/cvxpy](https://github.com/cvxpy/cvxpy), 6,182 stars, last push 2026-04-19).
- **JuMP** (Julia) — The Julia community's answer. Clean syntax, solver-agnostic. Very active ([github.com/jump-dev/JuMP.jl](https://github.com/jump-dev/JuMP.jl), 2,429 stars, last push 2026-04-07).

**Relevance to CP-SAT:** These are MILP/convex-first worlds. CP-SAT does have a MathOpt/Pyomo-style pathway (via OR-Tools' Python API or the `ortools` MathOpt interface), but if you're doing pure CP, the Python `cp_model` module or MiniZinc remain the idiomatic front-ends.

## 8. What's New in 2024-2026

- **CP-SAT itself** kept evolving — the OR-Tools team landed further parallel-search improvements, better LP integration, and the MathOpt unification that lets you swap CP-SAT/HiGHS/Gurobi behind a single modeling API ([github.com/google/or-tools](https://github.com/google/or-tools), last push 2026-04-17).
- **HiGHS** crossed into mainstream adoption — now the default open MILP backend for SciPy's `linprog`/`milp`, Pyomo, JuMP, and most Python OR tutorials.
- **Exact CP solvers in Rust** — a small but growing crop of Rust CP engines (e.g. `pumpkin`, `copper`) appeared as research/experimentation projects. None are production-ready competitors to CP-SAT in 2026, but they signal momentum.
- **SCIP went Apache 2.0** (v9.0, 2024) — removed the historical academic-license friction and makes it a real open alternative to Gurobi for MILP.
- **Answer Set Programming (ASP) via clingo** remains a live ecosystem for combinatorial problems with heavy logical structure — not a CP-SAT replacement but worth knowing ([github.com/potassco/clingo](https://github.com/potassco/clingo), active).
- **Timefold** spun out of Red Hat/OptaPlanner in 2023 and has shipped fast since (see above).

## 9. ML + CP / Learning to Search

Growing research direction; not yet production-default for anyone. Key strands:

- **Learning to branch / learning variable ordering** — Gasse et al. "Exact Combinatorial Optimization with Graph Convolutional Neural Networks" (NeurIPS 2019) started the modern wave; Nair et al. at DeepMind followed with "Solving Mixed Integer Programs Using Neural Networks" (2020). Implementations still land in Ecole ([github.com/ds4dm/ecole](https://github.com/ds4dm/ecole), last push 2025-12-20 — slower cadence, not dead but not thriving).
- **Neural network guidance for CP-SAT-style solvers** — Cappart, Chételat, Khalil, Lodi, Morris, Veličković, "Combinatorial optimization and reasoning with graph neural networks" (JMLR 2023) is the canonical survey.
- **Large-neighborhood search with learned destroy operators** — active thread 2023-2026 in papers at ICLR/NeurIPS/CPAIOR.
- **LLMs as modeling assistants** — 2024-2026 saw multiple papers on using LLMs to translate natural-language problem descriptions into MiniZinc or CP-SAT code. Not a solver advancement, but relevant to how practitioners will *author* models.

Honest take: in 2026, none of this is beating tuned CP-SAT or Gurobi in production for classic scheduling. It's a direction to watch, not a tool to adopt now.

## 10. Comparison Table

| Tool | Language bindings | License | Paradigm | Active (2026)? | Good for |
|---|---|---|---|---|---|
| **OR-Tools CP-SAT** | C++, Python, Java, C#, Go | Apache 2.0 | CP/SAT hybrid | Yes (very active) | General-purpose CP & combinatorial optimization; scheduling, rostering, packing |
| **MiniZinc** | DSL + many backends | MPL 2.0 | Modeling language | Yes (active) | Solver-agnostic modeling, research, teaching, benchmarking |
| **Choco** | Java, Kotlin | BSD 4-Clause | Pure CP | Yes (active) | JVM apps, explanations, custom search strategies |
| **Gecode** | C++, MiniZinc backend | MIT | Pure CP | Maintained (slower) | Research baseline, MiniZinc backend |
| **Chuffed** | MiniZinc backend | MIT | LCG (CP+SAT) | Maintained (low volume) | MiniZinc Challenge–style scheduling; LCG-friendly problems |
| **Timefold** | Java, Kotlin, Python | Apache 2.0 + commercial | Metaheuristic LS | Yes (very active) | Large-scale rostering, VRP, real-time re-planning in JVM stacks |
| **Z3** | C++, Python, .NET, Java, OCaml, Julia | MIT | SMT | Yes (very active) | Program verification, logic-heavy constraints, mixed theories |
| **SCIP** | C, Python (PySCIPOpt) | Apache 2.0 | MILP + CP (CIP) | Yes (active) | Open-source MILP, constraint-integer programming |
| **HiGHS** | C++, Python, Julia, R | MIT | LP/MILP | Yes (very active) | Fast open-source LP/MILP; default in Python OR stack |
| **Gurobi / CPLEX** | Most majors | Commercial | LP/MILP/QP | Yes | Industrial MILP when licensing budget allows |
| **Pyomo / JuMP / CVXPY** | Python / Julia / Python | BSD / MPL / Apache | Modeling layer | Yes (all active) | Author-once, swap-solver; MILP and convex worlds |
| **clingo (ASP)** | C++, Python | MIT | Answer Set Programming | Yes (active) | Logic-heavy combinatorial problems, knowledge representation |

## Mental model for picking

1. **Small-to-medium combinatorial problem, want proven-optimal, have Python or Java:** CP-SAT first. MiniZinc+Chuffed as a comparison point.
2. **Scheduling/rostering at 10k+ entities, need solution in seconds, soft constraints dominate:** Timefold (JVM) or CP-SAT with LNS.
3. **Pure linear with continuous relaxations:** MILP — HiGHS if open-source is required, Gurobi if you have a license.
4. **Heavy logic, bitvectors, program verification:** Z3.
5. **Academic research / paper reproduction:** MiniZinc as the modeling layer, swap backends (CP-SAT, Chuffed, Gecode) to compare.
6. **Logical / knowledge-representation problems with rules:** clingo.
7. **Need explanations, provenance, or custom search in CP:** Choco.

Everything else on this page is a specialization of one of those seven buckets.
