# cp-deep-dive — Overview

**Purpose:** become a practitioner of Constraint Programming / Constraint Optimization, using the Nurse Scheduling Problem as the running use case, with Google OR-Tools CP-SAT (Python + Kotlin) and MiniZinc.

This file is the always-loaded anchor. For the living plan see [`plan.md`](plan.md). For deep knowledge see [`knowledge/`](knowledge/) (QMD-indexed).

## The field in one paragraph

*Constraint Programming (CP)* is a way to describe a problem as **variables + constraints + an objective**, and let a solver search for assignments that satisfy the constraints (and optimize the objective, if any). You do not tell the solver *how* to search — you tell it *what* must be true. Modern CP solvers like Google's CP-SAT combine tree search with SAT-style clause learning and specialized propagators for global constraints (`AllDifferent`, `Cumulative`, `Circuit`, …). They're the right tool for discrete combinatorial problems — scheduling, routing, assignment, packing — where the search space is astronomically large but heavily structured.

## The tools we'll use

| Tool | Role | When |
|---|---|---|
| **OR-Tools CP-SAT** (Google) | Primary solver. Hybrid CP + SAT, handles large integer problems. Python + Java/Kotlin APIs. | Every example. |
| **MiniZinc** | Solver-agnostic modeling language. Compile to FlatZinc → many solvers. Great for prototyping a clean spec before encoding in CP-SAT. | When the declarative form clarifies the math. |
| **Choco / Gecode / Timefold** | Other CP / optimization engines. Studied for context, not primary. | Short comparison chapter late in the plan. |

## The problem we'll solve (the running example)

The **Nurse Scheduling Problem (NSP)** — assign nurses to shifts across a planning horizon subject to hard rules (coverage, labor law, skill mix) and soft rules (preferences, fairness, workload balance). It's the canonical real-world CP scheduling problem: well-studied, has standard benchmark datasets (NSPLib, INRC-I, INRC-II), and maps cleanly to CP-SAT's `Interval` + `Cumulative` + Boolean machinery. We'll build it progressively, from toy instances up to a full application with a web UI.

## Learning ladder (high level)

1. **CP theory** — what it is, why it beats MILP/heuristics for some problems, key vocabulary (domain, constraint, propagator, branching, global constraint).
2. **CP-SAT basics** — variables, constraints, objective, solver, `ModelBuilder`, the solve loop. Tiny problems (N-Queens, cryptarithmetic, SEND + MORE = MONEY).
3. **CP-SAT scheduling primitives** — `IntervalVar`, `Cumulative`, `NoOverlap`, resource constraints.
4. **MiniZinc** — same toy problems, declarative style. Compile to FlatZinc, plug into multiple solvers.
5. **Nurse Scheduling I** — toy NSP instance, hard constraints only, 3 nurses × 7 days.
6. **Nurse Scheduling II** — soft constraints, objective, preferences, fairness.
7. **Nurse Scheduling III** — realistic-scale instance from benchmark data, solver tuning, warm starts.
8. **End-to-end application** — FastAPI + Kotlin/Ktor twin backend, web UI for schedule visualization + what-if, deployment.
9. **Ecosystem tour** — how other solvers (Choco, Timefold) approach the same problem.

Detailed plan with chapter-by-chapter breakdown: [`plan.md`](plan.md).

## Knowledge base

Deep-dive notes already in the repo (indexed by QMD — ask about any topic and I'll pull the right file):

- [`knowledge/cp-theory/overview.md`](knowledge/cp-theory/overview.md) — ELI5 CP primer, CSP vs COP, how solvers work (propagation + search + LCG), global constraints, CP vs MILP vs SAT comparison, 12-source bibliography.
- [`knowledge/cp-sat/overview.md`](knowledge/cp-sat/overview.md) — OR-Tools CP-SAT reference: install, core API, constraints table, scheduling primitives, search tuning, best practices.
- [`knowledge/cp-sat/python-vs-kotlin.md`](knowledge/cp-sat/python-vs-kotlin.md) — Python and Kotlin side-by-side: API diff table, idioms, build tooling (uv / Gradle), testing, Kotlin gotchas.
- [`knowledge/minizinc/overview.md`](knowledge/minizinc/overview.md) — MiniZinc language tour, FlatZinc architecture, backends, how it fits with CP-SAT.
- [`knowledge/nurse-scheduling/overview.md`](knowledge/nurse-scheduling/overview.md) — NSP deep dive: NP-hardness, hard/soft constraint taxonomy, benchmarks (NSPLib, INRC-I/II), CP-SAT modeling sketches, seminal papers.
- [`knowledge/ecosystem/overview.md`](knowledge/ecosystem/overview.md) — Choco, Gecode, Chuffed, Timefold, Z3, MILP solvers, modeling layers, 2024–2026 trends, picking-a-tool mental model.

## Current status

Phase 0 (setup + research) complete. Plan drafted in [`plan.md`](plan.md), awaiting iteration + lock-in before execution.

## Key external references

- Wikipedia — [Constraint programming](https://en.wikipedia.org/wiki/Constraint_programming)
- Google OR-Tools — [docs home](https://developers.google.com/optimization), [intro](https://developers.google.com/optimization/introduction), [CP section](https://developers.google.com/optimization/cp), [CP-SAT solver](https://developers.google.com/optimization/cp/cp_solver), [scheduling](https://developers.google.com/optimization/scheduling), [employee scheduling](https://developers.google.com/optimization/scheduling/employee_scheduling)
- MiniZinc — [minizinc.org](https://www.minizinc.org/)

Full annotated reference lists live inside each `docs/knowledge/<area>/overview.md`.
