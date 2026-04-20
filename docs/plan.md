# cp-deep-dive — Learning & Execution Plan

**Version:** v0.1 (draft for iteration)
**Last updated:** 2026-04-19
**Owner:** Vanja (student) + Claude (teacher/pair)

This is the living roadmap. Before we execute any chapter we lock the plan; once a chapter is underway the plan is the source of truth for what "done" means. Edits welcome at any time — when we change direction we update this file and commit the change.

---

## 0. The shape of the journey

We are climbing a ladder from "never touched a solver" to "can design, model, tune, and ship a real constraint-optimization application." The ladder has **seven phases** and **16 chapters**. Each chapter is small enough to fit in one sitting (1–3 hours), ends with a runnable artefact, and builds on the previous one.

```
Phase 1  CP Theory (ELI5 → vocab)            ch. 1
Phase 2  CP-SAT Basics (Py + Kt parallel)    ch. 2-5
Phase 3  MiniZinc (declarative viewpoint)    ch. 6-7
Phase 4  Scheduling Primitives               ch. 8-9
Phase 5  Nurse Scheduling (I → III)          ch. 10-12
Phase 6  End-to-End Application              ch. 13-15
Phase 7  Ecosystem & Mastery                 ch. 16
```

Every chapter has the same sections:

- **Goal** — what you can do at the end that you couldn't before
- **New concepts** — vocabulary & mental models introduced
- **Hands-on** — the code we write together (Python + Kotlin + MiniZinc where relevant)
- **Deliverable** — file(s) committed to the repo; a small PR
- **Self-check** — 3–5 questions you should be able to answer without help
- **Approx. time** — rough estimate; we'll recalibrate

At the end of each phase we do a **retrospective** — "what stuck, what didn't, what's next" — and edit this plan to match reality.

---

## 1. Teaching principles (how we work together)

- **Intuition before formalism.** Every new concept starts with an ELI5 story or picture, *then* the formal definition.
- **Two languages, one concept.** Whatever we do in Python, we immediately do in Kotlin. The goal is to build a mental model of the solver, not of one API.
- **Small runnable artefacts.** Every chapter commits working code. No "we'll clean this up later."
- **Problems, not tutorials.** We always have a concrete problem. No constraint is introduced in the abstract — we hit the wall, then learn the tool that breaks it.
- **The solver is the oracle, not the teacher.** We'll read `cpmodel.Proto()` output, inspect presolve logs, and second-guess the solver. No magic.
- **Cite sources.** Each chapter references the definitive docs/paper/book section so you can dig deeper on your own.
- **Cut scope aggressively.** If something isn't serving mastery, we drop it. This is a living plan, not a contract.

---

## 2. Tool stack (locked after this iteration)

Proposed modern stack (open to change before we start):

| Layer | Choice | Why |
|---|---|---|
| **Primary solver** | OR-Tools CP-SAT 9.15+ | Best-in-class hybrid CP/SAT/LP. Apache 2.0. Active. |
| **Modeling (declarative)** | MiniZinc 2.8+ | Solver-agnostic. Cleaner math, great for prototyping. |
| **Python** | 3.12+, managed with **uv** | Fast, reproducible. No virtualenv/pip-tools gymnastics. |
| **Kotlin** | 2.1+ on JDK 21, Gradle 9 Kotlin DSL + version catalog | Modern JVM defaults. |
| **Python test** | pytest + hypothesis | De facto standard + property-based tests for models. |
| **Kotlin test** | Kotest | BDD-style, expressive. |
| **Python backend** | FastAPI + Pydantic v2 + uvicorn | Typed, fast, async. |
| **Kotlin backend** | Ktor 3.x | Lightweight, Kotlin-idiomatic, co-routine-native. |
| **Frontend** | Next.js 15 (App Router, React 19) + TypeScript + Tailwind 4 + shadcn/ui | Modern React baseline. |
| **Schedule viz** | Custom grid (rows = nurses, cols = days) + Recharts for KPIs | Keeps it simple and domain-specific. |
| **Data / persistence** | SQLite (via Prisma or SQLModel) → optional Postgres later | Zero-ops to start. |
| **Orchestration** | Docker Compose for local multi-service | Boring and reliable. |
| **Benchmarks** | NSPLib + INRC-I + INRC-II instances | Standard, comparable numbers. |
| **CI** | GitHub Actions: lint + test both stacks | Catch regressions when we ship to the app. |

**Alternatives we considered & rejected for now** (can revisit in Phase 7):
- Timefold instead of CP-SAT (better for full local-search-heavy production systems, but we want to *learn CP*, not consume a framework).
- Spring Boot instead of Ktor (heavier, less Kotlin-idiomatic for a learning project).
- SvelteKit/SolidStart instead of Next.js (equally good; Next wins on shadcn/ui + docs volume).
- Choco as primary (Java-only, smaller ecosystem than CP-SAT).

---

## 3. The phases, chapter by chapter

### Phase 1 — CP theory (ELI5 → vocabulary)

#### Chapter 1 — What is Constraint Programming?

- **Goal:** Explain CP to a smart friend. Know when to reach for CP vs MILP vs brute force vs heuristics.
- **New concepts:** variable, domain, constraint, assignment, feasible solution, optimal solution, CSP vs COP, search space, propagation, global constraint (as a concept, no code yet), NP-hardness in one paragraph.
- **Hands-on:**
  1. Solve a 9×9 Sudoku **by hand** using propagation rules. Name the rules.
  2. Draw the search tree for a 4-queens problem on paper.
  3. Write a plain-English spec for "schedule 3 nurses for 3 days" — just constraints, no code.
- **Deliverable:** `docs/chapters/01-what-is-cp.md` (your notes + drawings + one-page summary in your own words).
- **Self-check:**
  - Why is `AllDifferent(x1..xn)` better than n*(n-1)/2 inequality constraints?
  - What's the difference between a CSP and a COP?
  - Name three problem shapes CP eats for breakfast and three where MILP wins.
- **Time:** ~2h.
- **Reading:** `knowledge/cp-theory/overview.md` sections 1–4.

---

### Phase 2 — CP-SAT basics (Python + Kotlin parallel)

#### Chapter 2 — Hello, CP-SAT

- **Goal:** Have both Python and Kotlin projects building, running, and solving an equation.
- **New concepts:** `CpModel`, `CpSolver`, `IntVar`, `BoolVar`, status codes (`OPTIMAL`, `FEASIBLE`, `INFEASIBLE`, `MODEL_INVALID`, `UNKNOWN`), `solver.Value(x)`.
- **Hands-on:**
  - **Python** (`apps/py-cp-sat/`): uv project. Solve `3x + 2y = 12`, `x + y ≤ 5`, `x,y ∈ [0,10]`.
  - **Kotlin** (`apps/kt-cp-sat/`): Gradle project with `ortools-java`. Same problem, same answer.
  - Compare: API differences, build times, native-loader gotcha.
- **Deliverable:** two scaffolded projects under `apps/`, both committed + CI green.
- **Self-check:**
  - What does `Loader.loadNativeLibraries()` do, and when does forgetting it bite?
  - Why does CP-SAT only accept integer variables? How do you encode fractions?
  - What's the difference between status `OPTIMAL` and `FEASIBLE`?
- **Time:** ~3h.
- **Reading:** `knowledge/cp-sat/overview.md` sections 1–3, `knowledge/cp-sat/python-vs-kotlin.md` intro + install.

#### Chapter 3 — Classic puzzles: N-Queens, SEND+MORE=MONEY, Cryptarithmetic

- **Goal:** Build fluency with integer vars, bool vars, basic global constraints.
- **New concepts:** `AddAllDifferent`, `AddAbsEquality`, `OnlyEnforceIf`, linearization patterns ("how do I express XOR / max / abs / OR?").
- **Hands-on:** Solve N-Queens (N=8, 12, 50, 200) in Python and Kotlin. Solve SEND+MORE=MONEY. Write one cryptarithmetic puzzle of your choice.
- **Deliverable:** `apps/py-cp-sat/puzzles/`, `apps/kt-cp-sat/puzzles/`, tests for each.
- **Self-check:**
  - How does `AllDifferent` scale differently than pairwise `!=`?
  - What's `OnlyEnforceIf` for, and when is a `half-reified` constraint not enough?
  - Time how long N=200 queens takes — what dominates?
- **Time:** ~3h.

#### Chapter 4 — Optimization: objectives, bounds, callbacks

- **Goal:** Move from CSP (feasible) to COP (optimal). Understand bounds and how to read them.
- **New concepts:** `Minimize`/`Maximize`, objective value vs best bound, optimality gap, `SolutionCallback`, enumerating all solutions, time limits, `num_search_workers`.
- **Hands-on:**
  - Knapsack (0-1 and bounded) in Py + Kt.
  - Solve and enumerate all solutions of a small CSP via callback.
  - Plot (matplotlib) objective + bound over time.
- **Deliverable:** `apps/*/optimization/`, plus a chapter note `docs/chapters/04-optimization.md` with the convergence plot.
- **Self-check:**
  - What does "bound" actually mean and how does CP-SAT compute it?
  - When is it OK to stop early with a gap, and how do you measure gap?
  - Why can parallel search make the log look non-monotonic?
- **Time:** ~3h.

#### Chapter 5 — The global-constraints tour

- **Goal:** Know which global constraints exist and when to reach for them.
- **New concepts:** `Circuit`, `Table`, `Element`, `Automaton`, `Inverse`, `LexicographicLessEqual`, `Reservoir`. Quick hands-on each.
- **Hands-on:**
  - TSP on 8 cities via `Circuit` (Py + Kt).
  - Shift-pattern allowed transitions via `Automaton` (sneak preview of NSP).
  - Allowed assignments lookup via `Table`.
- **Deliverable:** `apps/*/globals/` — one tiny program per constraint.
- **Self-check:**
  - Why is `Circuit` faster than encoding TSP as MTZ constraints?
  - What's `Automaton` really doing under the hood?
  - When would you prefer `Table` over inline logical constraints?
- **Time:** ~3h.
- **Reading:** `knowledge/cp-theory/overview.md` global-constraints section + `knowledge/cp-sat/overview.md` constraints table.

---

### Phase 3 — MiniZinc (the declarative viewpoint)

#### Chapter 6 — MiniZinc tour

- **Goal:** Read and write MiniZinc. Understand what FlatZinc is.
- **New concepts:** `.mzn` / `.dzn`, `array of var`, `forall`, `sum`, `constraint`, `solve minimize / satisfy`, the MiniZinc IDE, picking a backend (Gecode, Chuffed, CP-SAT, HiGHS).
- **Hands-on:** Re-solve N-Queens, knapsack, and SEND+MORE=MONEY in MiniZinc. Run each against two backends, compare times.
- **Deliverable:** `apps/mzn/` with `.mzn` + `.dzn` files; a `README` comparing to CP-SAT.
- **Self-check:**
  - What's the compile pipeline `.mzn → FlatZinc → solver`?
  - Why does the same `.mzn` give very different runtimes on different solvers?
  - When does declarative modeling *hurt* performance?
- **Time:** ~2h.
- **Reading:** `knowledge/minizinc/overview.md` full.

#### Chapter 7 — MiniZinc ↔ CP-SAT: prototype then port

- **Goal:** Use MiniZinc as a *specification tool* — prototype there, port to CP-SAT Python/Kotlin.
- **New concepts:** MiniZinc-Python API, using CP-SAT as a MiniZinc backend, the two integration patterns.
- **Hands-on:** Take a small NSP instance, write it in MiniZinc first, then implement in CP-SAT Py + Kt. Check same objective value.
- **Deliverable:** `apps/mzn/nsp-tiny.mzn` + matching Py + Kt implementations.
- **Self-check:**
  - What do you gain from writing MiniZinc first? What do you lose?
  - Which constraints translate cleanly, which don't?
- **Time:** ~2h.

---

### Phase 4 — Scheduling primitives

#### Chapter 8 — Intervals, NoOverlap, Cumulative (Job-Shop)

- **Goal:** Model a scheduling problem with real time and resources.
- **New concepts:** `NewIntervalVar`, `NewOptionalIntervalVar`, `AddNoOverlap`, `AddCumulative`, makespan, critical path.
- **Hands-on:**
  - Classic Job-Shop Scheduling Problem (JSSP) from OR-Tools docs, Py + Kt.
  - Add resource capacity via `Cumulative`.
  - Visualize the schedule as a Gantt chart (matplotlib).
- **Deliverable:** `apps/*/scheduling/jobshop/`.
- **Self-check:**
  - What's the difference between `NoOverlap` and `Cumulative(capacity=1)`?
  - When do you need `OptionalInterval`?
  - How do you minimize makespan vs minimize weighted tardiness?
- **Time:** ~3h.

#### Chapter 9 — Time, shifts, transitions

- **Goal:** Encode *calendar* time and *shift transition rules* (the heart of NSP).
- **New concepts:** Boolean shift-grid vs interval formulation; using `AddAutomaton` for "you can't do a night shift then a morning shift"; sliding-window constraints; minimum rest between shifts.
- **Hands-on:** A 1-nurse, 14-day toy with: 3 shifts/day, max 5 consecutive working days, 11h rest rule, weekends-as-a-block preference.
- **Deliverable:** `apps/*/scheduling/shifts/` + a short write-up comparing the two formulations.
- **Self-check:**
  - When is the Boolean-grid formulation better than intervals? When worse?
  - Sketch the automaton for "no morning after night."
  - How do you encode "exactly 2 weekends off in 4 weeks"?
- **Time:** ~3h.
- **Reading:** `knowledge/nurse-scheduling/overview.md` formal-model + constraints-taxonomy sections.

---

### Phase 5 — The Nurse Scheduling Problem

This is where everything we learned comes together.

#### Chapter 10 — NSP v1: toy instance, hard constraints only

- **Goal:** Solve a fully-specified toy NSP (5 nurses × 14 days × 3 shifts) with every hard constraint in the textbook.
- **New constraints covered:** daily coverage, nurse availability, skill mix, min/max shifts per week, min rest, forbidden transitions, max consecutive days, fixed days off.
- **Hands-on:**
  - Python implementation (`apps/py-cp-sat/nsp/v1/`).
  - Kotlin implementation (`apps/kt-cp-sat/nsp/v1/`).
  - Shared JSON instance format (decide the schema together).
  - Output: ASCII schedule + validation script.
- **Deliverable:** both v1 solvers + shared `data/nsp/toy-01.json` + `tools/validate-schedule.py`.
- **Self-check:**
  - Is your schedule a certificate you can hand to a nurse manager?
  - Can you *prove* infeasibility when you make coverage too tight?
  - How long does it take? Is the model in presolve logs the size you expected?
- **Time:** ~4h.

#### Chapter 11 — NSP v2: soft constraints, preferences, fairness

- **Goal:** Add the objective — nurse preferences, shift-pattern quality, fairness across the team.
- **New concepts:** soft constraints as penalties in the objective; weighted objective design; lexicographic objectives (coverage dominates preference); max-min fairness vs sum-of-deviations.
- **Hands-on:**
  - Extend v1 → v2: ask for weekend-off preferences, hard-no overnight for 1 nurse, preferred partner shifts.
  - Introduce a workload-balance term: `max(hours_i) - min(hours_i)`.
  - Experiment with weightings; plot Pareto-ish tradeoffs.
- **Deliverable:** `apps/*/nsp/v2/` + a short experiment log `docs/chapters/11-nsp-v2.md`.
- **Self-check:**
  - Why is "sum of preference violations" often a terrible objective?
  - What's the trick for lexicographic optimization in CP-SAT?
  - How sensitive is the solution to weight tuning?
- **Time:** ~4h.

#### Chapter 12 — NSP v3: benchmark-scale & solver tuning

- **Goal:** Tackle a realistic instance from **INRC-II** (30–120 nurses, 4-week horizon) and push performance.
- **New concepts:** solution hints (warm start), `num_search_workers`, `linearization_level`, `cp_model_presolve`, Large Neighborhood Search, diversified portfolios, reading the search log.
- **Hands-on:**
  - Loader for INRC-II XML/JSON instances.
  - Baseline solve: record time-to-first-feasible, time-to-optimal-or-gap.
  - Tune parameters; document what moves the needle.
  - Optional: write a decomposition (solve week-by-week, stitch).
- **Deliverable:** `apps/*/nsp/v3/` + `docs/chapters/12-nsp-v3-benchmarks.md` (parameter table, timings).
- **Self-check:**
  - What's the single parameter that helped most? Why?
  - How do you know when you've squeezed the model dry vs need search help?
  - Could a MILP solver (HiGHS) solve this? Try and compare.
- **Time:** ~6h (including reading).

---

### Phase 6 — End-to-end application

Ship a small but real app that somebody could actually use.

#### Chapter 13 — The two backends

- **Goal:** Expose the NSP v3 solver over HTTP with both Python and Kotlin, sharing the same JSON contract.
- **New concepts:** solving in a background worker; cancellation / timeouts over HTTP; streaming solutions back (SSE/WebSocket); Pydantic v2 model ↔ Kotlin data class parity.
- **Hands-on:**
  - `apps/py-api/` (FastAPI): `POST /solve`, `GET /solution/{id}`, `GET /solutions/{id}/stream` (SSE).
  - `apps/kt-api/` (Ktor): same endpoints, same JSON contract.
  - Shared contract definition in `apps/shared/openapi.yaml`.
  - Test: same instance, both backends produce equivalent-quality schedules.
- **Deliverable:** two running APIs + a contract spec + integration test that hits both.
- **Self-check:**
  - How do you cancel a running solve cleanly in each runtime?
  - What's the right status-code for "still thinking, partial solution available"?
- **Time:** ~5h.

#### Chapter 14 — The web UI

- **Goal:** Beautiful, usable roster visualization + what-if editing.
- **New concepts:** Next.js 15 App Router; React 19 Suspense for streaming results; the roster grid UI pattern; constraint-violation highlighting; what-if (manual edit → re-validate).
- **Hands-on:**
  - `apps/web/` (Next.js 15 + Tailwind 4 + shadcn/ui).
  - Screens: (a) upload/select instance, (b) solve (shows progress + streaming bound), (c) schedule grid with coverage/fairness KPIs, (d) manual edit → re-validate → re-solve.
  - Backend toggle: talk to Python or Kotlin API.
- **Deliverable:** `apps/web/` + a screenshot in `docs/chapters/14-web.md`.
- **Self-check:**
  - How do you show the user *why* a cell is highlighted red?
  - How does streaming improve UX vs a "loading" spinner?
- **Time:** ~6h.

#### Chapter 15 — Polish, containerize, deploy

- **Goal:** Run it end-to-end, locally and on a cheap cloud. Observability and basic ops.
- **New concepts:** Docker multi-stage builds (Python + Kotlin); `docker compose up`; OpenTelemetry basics; instance library/seed data; CI with GH Actions.
- **Hands-on:**
  - Dockerfiles for all three services (py-api, kt-api, web).
  - `docker-compose.yml` with both backends + UI.
  - Simple auth (API key) + request logging.
  - Deploy: fly.io or Railway (pick one together).
- **Deliverable:** one-liner `docker compose up`. Live deploy URL (or a "how-to-deploy" doc if you prefer local-only).
- **Self-check:**
  - What's the biggest thing that surprised you shipping a CP app?
  - Where are the latency / memory / cost cliffs?
- **Time:** ~4h.

---

### Phase 7 — Ecosystem & mastery

#### Chapter 16 — The wider landscape

- **Goal:** Know the neighbors. You should be able to say "for that problem I'd reach for X, not CP-SAT, because Y."
- **New concepts:** Choco (Java), Gecode + Chuffed, Timefold (LS + incremental), Z3 (SMT), HiGHS (MILP), Pyomo/CVXPY/JuMP (modeling layers), LLMs as modeling assistants (2025 trend).
- **Hands-on:** Pick **one** of these and port NSP v1 or a small subset. Write a short comparison.
- **Deliverable:** `apps/alt-solver/<choice>/` + `docs/chapters/16-ecosystem.md` (timings, ergonomics, would-use-when).
- **Self-check:**
  - Where does CP-SAT win over all of these? Where does it lose?
  - What would you pick for a production rostering SaaS today?
- **Time:** ~3h.
- **Reading:** `knowledge/ecosystem/overview.md` full.

---

## 4. Cross-cutting deliverables

Built incrementally across chapters:

- `apps/py-cp-sat/` — Python solver library + puzzles + NSP
- `apps/kt-cp-sat/` — Kotlin solver library + puzzles + NSP
- `apps/mzn/` — MiniZinc models
- `apps/py-api/` — FastAPI service
- `apps/kt-api/` — Ktor service
- `apps/web/` — Next.js UI
- `data/nsp/` — toy instances + INRC-II loaders
- `tools/` — validate-schedule, instance converters, benchmark runner
- `docs/chapters/NN-*.md` — per-chapter write-ups (your notes, not regurgitated material)
- `docs/knowledge/` — reference encyclopedia (already seeded; grows as we hit new topics)
- `docs/adr/` — architecture decision records for non-obvious choices
- `benchmarks/` — baseline + tuned results on INRC-I/II

---

## 5. Repo layout we're aiming for

```
cp-deep-dive/
├── CLAUDE.md
├── README.md
├── AGENTS.md
├── .claude/
│   └── memory/         # symlinked, see tools/setup-memory-link.sh
├── docs/
│   ├── overview.md
│   ├── plan.md         # this file
│   ├── chapters/       # per-chapter notes
│   ├── knowledge/      # reference material (CP theory, CP-SAT, MiniZinc, NSP, ecosystem)
│   └── adr/
├── apps/
│   ├── py-cp-sat/
│   ├── kt-cp-sat/
│   ├── mzn/
│   ├── py-api/
│   ├── kt-api/
│   ├── web/
│   └── shared/         # OpenAPI, JSON schemas
├── data/
│   └── nsp/            # instances
├── benchmarks/
├── tools/
└── .github/workflows/  # CI
```

---

## 6. Ground rules & working agreement

- **You drive, I teach.** You type; I explain, suggest, review, and unblock. When you want to try something before I explain it, say so — exploration > lecture.
- **Pause any time.** If a concept isn't clicking we rewind, find a smaller example, or drop a rabbit hole.
- **No hand-waving.** If I skip over a step, ask. If you skip over a step, I'll ask.
- **Every chapter ends with a committed deliverable.** No WIP rotting on branches.
- **Every confusing moment becomes a chapter note.** Written in your words, for your future self.
- **When we disagree, we write an ADR.** `docs/adr/NN-<decision>.md`. Two paragraphs: what, why, alternatives, trade.
- **Plan is a living doc.** We edit it when reality disagrees. No pride.

---

## 7. Open questions for you before we lock the plan

Please look over the plan and push back on anything that feels off. Specific decisions I want your input on:

1. **Frontend framework.** Next.js 15 is the default pick. If you prefer SvelteKit / SolidStart / plain Vite-React, say so now.
2. **Kotlin backend framework.** Ktor 3.x (lighter, Kotlin-native) vs Spring Boot 3.x (heavier, mainstream). Default: Ktor.
3. **Persistence.** SQLite → Postgres when we outgrow it. OK?
4. **Deployment target.** fly.io vs Railway vs Vercel+Render vs local-only. Default: pick at Chapter 15 together.
5. **Chapter depth vs breadth.** I've designed 16 chapters, ~50–60h of work total. If you want fewer chapters with more depth, or more chapters covering edge cases (preemption, stochastic demand, rolling-horizon re-planning), say so now.
6. **MiniZinc weight.** Currently 2 chapters, used as a thinking tool rather than a production piece. Want more (build MiniZinc as a first-class citizen of the app)? Want less (drop to a single chapter)?
7. **Research / benchmarks ambition.** Do you want to *beat* a published INRC-II result, or is "match baseline" good enough?
8. **Cadence.** One chapter per session? Per week? Continuous mode where we burn through 3–4 in a sitting?
9. **Kotlin parity.** Do we really do every chapter twice (Py + Kt), or do we go Python-first through Phase 5 and port the interesting pieces to Kotlin afterward? (Current plan: everything in both. Real cost: ~30% more time. Real benefit: you internalize the solver, not the API.)
10. **Phase 7 solver to port to.** My guess: **Timefold** (most production-relevant for rostering) or **Choco** (closest to CP-SAT in spirit). You pick.

Once you answer these we lock v1.0 of the plan and start **Chapter 1**.

---

## 8. What's in the repo already (Phase 0 ✅)

- Repo skeleton + `CLAUDE.md` + README
- `.mcp.json` with QMD HTTP at `localhost:8181`
- `.claude/memory/` symlink plumbing
- QMD collection `cp-deep-dive-docs` indexed on `docs/`
- 6 knowledge docs under `docs/knowledge/` (cp-theory, cp-sat, cp-sat/python-vs-kotlin, minizinc, nurse-scheduling, ecosystem)
- This plan 👋
