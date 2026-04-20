# cp-deep-dive — Overview

**Purpose:** become a practitioner of Constraint Programming / Constraint Optimization, using the Nurse Scheduling Problem as the running use case, with Google OR-Tools CP-SAT via **Python** and an idiomatic **Kotlin DSL we build ourselves** (`cpsat-kt`), plus **MiniZinc** as a teaching companion.

This file is the always-loaded anchor. Vanja's entry point is [`../README.md`](../README.md). For the living plan see [`plan.md`](plan.md). For deep knowledge see [`knowledge/`](knowledge/) (QMD-indexed).

## The field in one paragraph

*Constraint Programming (CP)* is a way to describe a problem as **variables + constraints + an objective**, and let a solver search for assignments that satisfy the constraints (and optimize the objective, if any). You do not tell the solver *how* to search — you tell it *what* must be true. Modern CP solvers like Google's CP-SAT combine tree search with SAT-style clause learning and specialized propagators for global constraints (`AllDifferent`, `Cumulative`, `Circuit`, …). They're the right tool for discrete combinatorial problems — scheduling, routing, assignment, packing — where the search space is astronomically large but heavily structured.

## The tools we'll use

| Tool | Role | When |
|---|---|---|
| **OR-Tools CP-SAT** (Google, v9.15+) | Primary solver. Hybrid CP + SAT + LP, handles large integer problems. Python first-party, Java wrapper for JVM. | Every example. |
| **`cpsat-kt`** (this project) | Our idiomatic Kotlin DSL wrapper over OR-Tools Java API. First-class artifact, eventually publishable. | Every Kotlin example in the repo. |
| **Python (uv + 3.12+)** | First-party CP-SAT bindings + the app's Python backend (FastAPI). | Every chapter; `apps/py-*`. |
| **Kotlin 2.1+ on JDK 25 (LTS), Gradle 9** | JVM half of dual-language work; `cpsat-kt` library + Ktor 3 backend. | Every chapter via `cpsat-kt`; `apps/kt-*`. |
| **MiniZinc (v2.8+)** | Solver-agnostic declarative modeling. Teaching tool for Phase 3; used during NSP prototyping. | When the declarative form clarifies the math. |
| **Ktor 3.x** | Lightweight, coroutine-native Kotlin server framework for the NSP app backend. | Phase 7 (app). |
| **Vite + React 19 + React Router v7 (framework mode) + Tailwind 4 + shadcn/ui** | Frontend for NSP app. Fast, modern, lightweight. | Phase 7 (app). |
| **Choco / Timefold / Gecode / Z3** | Other CP / optimization engines. Studied for context, ported-to in Phase 8. | Final comparison chapter. |

## The problem we'll solve (the running example)

The **Nurse Scheduling Problem (NSP)** — assign nurses to shifts across a planning horizon subject to hard rules (coverage, labor law, skill mix) and soft rules (preferences, fairness, workload balance). It's the canonical real-world CP scheduling problem: well-studied, has standard benchmark datasets (NSPLib, INRC-I, INRC-II), and maps cleanly to CP-SAT's `Interval` + `Cumulative` + Boolean machinery. We'll build it progressively, from toy instances up to a full application with a web UI.

## Learning ladder (8 phases, 18 chapters)

1. **Phase 1 — CP theory** (ch. 1) — what it is, why it beats MILP/heuristics for some problems, core vocabulary.
2. **Phase 2 — CP-SAT basics + build `cpsat-kt`** (ch. 2–6) — hello world, build our Kotlin DSL, puzzles (N-Queens/cryptarithmetic), optimization, global-constraints tour.
3. **Phase 3 — MiniZinc** (ch. 7–8) — declarative modeling tour, then prototype-then-port pattern.
4. **Phase 4 — Scheduling primitives** (ch. 9–10) — `IntervalVar`, `Cumulative`, `NoOverlap`, job-shop; calendar time, shifts, transitions.
5. **Phase 5 — Nurse Scheduling I/II/III** (ch. 11–13) — toy NSP → soft constraints/fairness → INRC-II benchmark-scale + tuning.
6. **Phase 6 — Spec-driven app design** (ch. 14) — write and lock `specs/nsp-app/` before any app code.
7. **Phase 7 — End-to-end application** (ch. 15–17) — FastAPI + Ktor twin backends, Vite+RR7 frontend, containerized, deployed.
8. **Phase 8 — Ecosystem port** (ch. 18) — reimplement a subset on Timefold and Choco, compare.

Detailed plan with chapter-by-chapter breakdown: [`plan.md`](plan.md).

## Knowledge base

Deep-dive notes already in the repo (indexed by QMD — ask about any topic and I'll pull the right file):

- [`knowledge/cp-theory/overview.md`](knowledge/cp-theory/overview.md) — ELI5 CP primer, CSP vs COP, how solvers work (propagation + search + LCG), global constraints, CP vs MILP vs SAT comparison, 12-source bibliography.
- [`knowledge/cp-sat/overview.md`](knowledge/cp-sat/overview.md) — OR-Tools CP-SAT reference: install, core API, constraints table, scheduling primitives, search tuning, best practices.
- [`knowledge/cp-sat/python-vs-kotlin.md`](knowledge/cp-sat/python-vs-kotlin.md) — Python vs raw Java/Kotlin API, motivating the `cpsat-kt` wrapper.
- [`knowledge/cpsat-kt/overview.md`](knowledge/cpsat-kt/overview.md) — design doc for our idiomatic Kotlin DSL library (API surface, package layout, testing, publishing plan).
- [`knowledge/minizinc/overview.md`](knowledge/minizinc/overview.md) — MiniZinc language tour, FlatZinc architecture, backends, how it fits with CP-SAT.
- [`knowledge/nurse-scheduling/overview.md`](knowledge/nurse-scheduling/overview.md) — NSP deep dive: NP-hardness, hard/soft constraint taxonomy, benchmarks (NSPLib, INRC-I/II), CP-SAT modeling sketches, seminal papers.
- [`knowledge/ecosystem/overview.md`](knowledge/ecosystem/overview.md) — Choco, Gecode, Chuffed, Timefold, Z3, MILP solvers, modeling layers, 2024–2026 trends, picking-a-tool mental model.

## Current status

Phase 0 (setup + research) complete. **Plan v0.2** in [`plan.md`](plan.md) with Vanja's stack choices folded in — awaiting final green-light before Claude scaffolds all artifacts (`libs/cpsat-kt/`, `specs/nsp-app/`, `apps/*/`, chapter docs). After green-light, Vanja works chapter-by-chapter from [`../README.md`](../README.md).

## Key external references

- Wikipedia — [Constraint programming](https://en.wikipedia.org/wiki/Constraint_programming)
- Google OR-Tools — [docs home](https://developers.google.com/optimization), [intro](https://developers.google.com/optimization/introduction), [CP section](https://developers.google.com/optimization/cp), [CP-SAT solver](https://developers.google.com/optimization/cp/cp_solver), [scheduling](https://developers.google.com/optimization/scheduling), [employee scheduling](https://developers.google.com/optimization/scheduling/employee_scheduling)
- MiniZinc — [minizinc.org](https://www.minizinc.org/)

Full annotated reference lists live inside each `docs/knowledge/<area>/overview.md`.
