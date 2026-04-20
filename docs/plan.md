# cp-deep-dive — Learning & Execution Plan

**Version:** v0.2 (draft — answers folded in, awaiting green-light)
**Last updated:** 2026-04-19
**Owner:** Vanja (student) + Claude (teacher/pair)

This is the living roadmap. Vanja iterates with Claude on this file, locks it, and only then does Claude scaffold the full repo (all chapters, `libs/cpsat-kt/`, `specs/nsp-app/`, app stubs). Once scaffolded, Vanja opens [`../README.md`](../README.md) and works chapter-by-chapter with Claude monitoring progress.

---

## 0. Shape of the journey

We climb a ladder from "never touched a solver" to "can design, model, tune, and ship a real constraint-optimization application." The ladder has **8 phases** and **18 chapters**. Each chapter fits in one sitting (1–4 hours), ends with runnable dual-language code, and builds on the previous one.

```
Phase 1  CP theory (ELI5 → vocab)             ch. 1       (1 chapter)
Phase 2  CP-SAT basics + build cpsat-kt       ch. 2-6     (5 chapters)
Phase 3  MiniZinc (declarative viewpoint)     ch. 7-8     (2 chapters)
Phase 4  Scheduling primitives                ch. 9-10    (2 chapters)
Phase 5  Nurse Scheduling (I → III)           ch. 11-13   (3 chapters)
Phase 6  Spec-driven app design               ch. 14      (1 chapter — write & LOCK spec)
Phase 7  End-to-end application               ch. 15-17   (3 chapters)
Phase 8  Ecosystem port + mastery retro       ch. 18      (1 chapter)
```

Per-chapter structure stays the same throughout:

- **Goal** — what you can do at the end that you couldn't before
- **New concepts** — vocabulary & mental models introduced
- **Hands-on** — the code we write together (Python + Kotlin via `cpsat-kt` + MiniZinc where relevant)
- **Deliverable** — files committed; a small PR-sized change
- **Self-check** — 3–5 questions you should be able to answer without help
- **Approx. time** — rough estimate; we recalibrate as we go
- **Reading** — which knowledge docs to read (we always read the relevant `overview.md` first, then QMD-query for depth)

End of each phase: short retrospective + plan-refresh commit.

---

## 1. Teaching principles (how we work together)

- **Intuition before formalism.** Every new concept starts with an ELI5 story/picture, *then* the formal definition.
- **Dual-language, one concept.** Python + Kotlin (via `cpsat-kt`). Whatever we do in one, we do in the other — the comparison is the learning.
- **Small runnable artefacts.** Every chapter commits working code. No "we'll clean this up later."
- **Problems, not tutorials.** Always have a concrete problem. No constraint is introduced abstractly — we hit the wall, then learn the tool that breaks it.
- **The solver is the oracle, not the teacher.** We read `cpmodel.Proto()`, inspect presolve logs, second-guess the solver. No magic.
- **Cite sources.** Each chapter references the definitive docs/paper/book section.
- **Cut scope aggressively.** If something isn't serving mastery, we drop it. Living plan, not a contract.
- **`cpsat-kt` is a first-class artifact.** It's not "helper code for chapters" — it's a publishable Kotlin library. We treat it as such (own build, tests, versioning, README, changelog).
- **Spec before app.** Phase 7 (the NSP app) does not start until `specs/nsp-app/` is written and locked in Phase 6.

---

## 2. Tool stack — LOCKED for v0.2 (change only via ADR)

### Languages & runtimes

| Layer | Choice | Notes |
|---|---|---|
| **Python** | 3.12+ managed with **uv** | Fast resolve, reproducible lockfile, no virtualenv ceremony |
| **JVM** | **JDK 25 (LTS)** | Current LTS (since Sept 2025); cadence: upgrade on next LTS when released |
| **Kotlin** | **2.1+** | Stable K2 compiler, context receivers, modern stdlib |
| **Node** | 22 LTS | For the frontend dev toolchain |
| **Gradle** | **9.x Kotlin DSL + version catalog** | `gradle/libs.versions.toml` |

### Solver & modeling

| Layer | Choice | Notes |
|---|---|---|
| **Primary solver** | OR-Tools **CP-SAT 9.15+** | Best-in-class hybrid CP/SAT/LP. Apache 2.0. |
| **Python bindings** | `ortools` via uv | First-party |
| **Kotlin bindings** | **`cpsat-kt`** (our DSL wrapper over `com.google.ortools:ortools-java`) | Built in Chapter 3, used everywhere thereafter |
| **Modeling (declarative)** | MiniZinc 2.8+ | Teaching + prototyping tool. Not in production app unless a concrete case proves value. |

### Testing & quality

| Layer | Choice |
|---|---|
| **Python test** | pytest + hypothesis |
| **Kotlin test** | Kotest 5.x |
| **Python lint/format** | ruff + mypy |
| **Kotlin lint/format** | ktlint + detekt |
| **CI** | GitHub Actions: lint + test both stacks on every push |

### Application stack (Phase 7)

| Layer | Choice | Notes |
|---|---|---|
| **Python backend** | **FastAPI + Pydantic v2 + uvicorn** | Typed, fast, async |
| **Kotlin backend** | **Ktor 3.x** (Vanja's pick) | Lightweight, Kotlin-idiomatic, coroutine-native |
| **Frontend** | **Vite 6 + React 19 + React Router v7 (framework mode) + TypeScript 5 + Tailwind 4 + shadcn/ui** | Modern lightweight SPA/SSR, no vendor lock-in. (RR7 framework mode is the Remix successor.) |
| **Data layer (web)** | TanStack Query 5 | Cache + async state for the UI |
| **Schedule viz** | Custom roster grid (rows=nurses, cols=days) + Recharts for KPIs | Keep it domain-specific |
| **Persistence** | SQLite via SQLModel (Python) / Exposed (Kotlin) | Zero-ops to start; swap to Postgres if we outgrow |
| **API contract** | OpenAPI 3.1 spec in `apps/shared/openapi.yaml` | Both backends implement identically |
| **Orchestration** | Docker Compose (local) | Boring and reliable |
| **Deploy** | Fly.io (decided at Ch. 17) | Open; can swap to Railway / local-only |
| **Obs** | OpenTelemetry + structured JSON logs | Basic but real |

### Benchmarks (Phase 5)

- **NSPLib** + **INRC-I (2010)** + **INRC-II (2015)** instances
- Ambition: match published baselines, push for improvements where the model allows

**Rejected-for-now alternatives** (revisit in Phase 8 via ADR if they come up):
- Next.js 15 instead of Vite+RR7 — heavier, Vercel-leaning.
- Spring Boot 3 instead of Ktor — heavier, less Kotlin-idiomatic.
- Timefold as primary solver — we're learning CP, not consuming a framework.
- Choco as primary — smaller ecosystem than CP-SAT, JVM-only.
- Raw Java-in-Kotlin instead of `cpsat-kt` — explicitly rejected per user feedback.

---

## 3. Answered open questions (v0.1 → v0.2)

Vanja's answers to v0.1's 10 questions, now locked:

| # | Question | Answer |
|---|---|---|
| 1 | Kotlin parity | **Both languages in every chapter + doc + example**, Kotlin via `cpsat-kt`. |
| 2 | Frontend | **Claude's choice: Vite 6 + React 19 + React Router v7 framework mode + Tailwind 4 + shadcn/ui + TanStack Query.** Fast, modern, lightweight. |
| 3 | Kotlin backend | **Ktor 3.x.** |
| 4 | Workflow | **Scaffolding-first, README-led chapters.** After plan lock, Claude scaffolds everything; Vanja opens `README.md` and works with Claude monitoring + answering + updating. |
| 5 | Benchmark ambition | **"Do our best."** Match baseline; push for improvements where reachable. |
| 6 | MiniZinc weight | **Claude decides** → keep as teaching tool (Phase 3, 2 chapters); **not in production app** (unclear ROI for shipping a MiniZinc layer when CP-SAT natively does the job). |
| 7 | Phase 8 port target | **Both Timefold + Choco.** (Shorter port per solver.) |

New additions from Vanja's feedback:
- JDK 25 LTS (was 21).
- **New Chapter 3**: build `cpsat-kt` v0.1 — the Kotlin DSL library — as a first-class artifact.
- **New Phase 6 (Chapter 14)**: spec-driven app design. Full markdown spec in `specs/nsp-app/`, locked before Phase 7 implementation.
- README.md upgraded to canonical entry point.

---

## 4. The phases, chapter by chapter

### Phase 1 — CP theory (ELI5 → vocabulary)

#### Chapter 1 — What is Constraint Programming?

- **Goal:** Explain CP to a smart friend. Know when to reach for CP vs MILP vs brute force vs heuristics.
- **New concepts:** variable, domain, constraint, assignment, feasible solution, optimal solution, CSP vs COP, search space, propagation, global constraint (as a concept, no code yet), NP-hardness in one paragraph.
- **Hands-on:**
  1. Solve a 9×9 Sudoku by hand using propagation rules. Name the rules.
  2. Draw the search tree for 4-queens on paper.
  3. Write a plain-English spec for "schedule 3 nurses for 3 days" — constraints only, no code.
- **Deliverable:** `docs/chapters/01-what-is-cp.md` (your notes + drawings + one-page summary in your own words).
- **Self-check:**
  - Why is `AllDifferent(x1..xn)` better than n*(n-1)/2 inequality constraints?
  - Difference between a CSP and a COP?
  - Three problem shapes CP eats for breakfast; three where MILP wins.
- **Time:** ~2h.
- **Reading:** [`docs/knowledge/cp-theory/overview.md`](knowledge/cp-theory/overview.md) sections 1–4.

---

### Phase 2 — CP-SAT basics + build `cpsat-kt`

This phase has 5 chapters. Chapter 3 is the pivotal one: we **build the DSL library itself**, then every subsequent Kotlin example uses it.

#### Chapter 2 — Hello, CP-SAT (Python first-class + raw Java-in-Kotlin pain)

- **Goal:** Both languages building and solving an equation. *Experience the raw-Java-in-Kotlin pain* so the motivation for `cpsat-kt` lands.
- **New concepts:** `CpModel`, `CpSolver`, `IntVar`, `BoolVar`, status codes (`OPTIMAL`, `FEASIBLE`, `INFEASIBLE`, `MODEL_INVALID`, `UNKNOWN`), `solver.Value(x)`, native library loading on JVM.
- **Hands-on:**
  - **Python** (`apps/py-cp-sat/ch02-hello/`): uv project. Solve `3x + 2y = 12`, `x + y ≤ 5`, `x,y ∈ [0,10]`.
  - **Kotlin** (`apps/kt-cp-sat/ch02-hello/`): Gradle project, **raw `com.google.ortools.sat.*`**, same problem. Feel the ergonomic gap.
- **Deliverable:** two scaffolded projects + a short writeup `docs/chapters/02-hello.md` listing the Kotlin friction points (operator overloading missing, verbose constraint construction, no sealed result, nullable platform types, etc.).
- **Self-check:**
  - What does `Loader.loadNativeLibraries()` do?
  - Why does CP-SAT only accept integer variables?
  - `OPTIMAL` vs `FEASIBLE`?
  - Name three Kotlin ergonomic misses in the raw Java API.
- **Time:** ~3h.
- **Reading:** `knowledge/cp-sat/overview.md` sections 1–3; `knowledge/cp-sat/python-vs-kotlin.md` intro + install.

#### Chapter 3 — Build `cpsat-kt` v0.1 (the Kotlin DSL library)

- **Goal:** Design and implement the core of our idiomatic Kotlin wrapper. At chapter-end, the N-Queens from Chapter 2 is rewritten in clean DSL Kotlin and all subsequent Kotlin chapters use this library.
- **New concepts:** Kotlin DSL builders (`@DslMarker`, receiver lambdas), operator overloading, sealed classes for solve results, `IntRange` for domains, extension functions, composite Gradle builds, Gradle version catalogs, Dokka basics.
- **Hands-on:**
  - Scaffold `libs/cpsat-kt/` (Gradle 9, JDK 25, Kotlin 2.1, version catalog, Kotest).
  - Implement **v0.1 API surface**:
    - `cpModel { }` builder
    - `intVar`, `boolVar`, `constant`
    - `LinearExpr` with `+`, `-`, `*` (with Long), `unaryMinus`
    - Infix: `eq`, `neq`, `le`, `lt`, `ge`, `gt`
    - `constraint { ... }` block
    - `allDifferent`, `exactlyOne`, `atMostOne`
    - `minimize { }` / `maximize { }`
    - `solve { params }` returning sealed `SolveResult` (`Optimal`, `Feasible`, `Infeasible`, `Unknown`, `ModelInvalid`)
    - `.toJava()` escape hatch on every wrapper
  - Parity tests: same model via raw Java + DSL, assert equal solutions.
  - Rewrite Chapter 2's Kotlin example using the DSL — compare the two side-by-side.
  - Wire `apps/kt-cp-sat/` as a composite build consuming `libs/cpsat-kt/`.
- **Deliverable:** `libs/cpsat-kt/` with working v0.1, full tests passing, `docs/knowledge/cpsat-kt/overview.md` updated with "shipped" section, ADR `docs/adr/0001-cpsat-kt.md` locking package name + coords.
- **Self-check:**
  - What's the difference between `constraint { x eq y }` and `x eq y` at top level? Why the block?
  - Why return a sealed `SolveResult` instead of letting `solve()` throw?
  - What's a composite build and why does it beat `mvn install` during development?
- **Time:** ~4h.
- **Reading:** [`docs/knowledge/cpsat-kt/overview.md`](knowledge/cpsat-kt/overview.md), Kotlin DSL guide: https://kotlinlang.org/docs/type-safe-builders.html

#### Chapter 4 — Classic puzzles: N-Queens, SEND+MORE=MONEY, Cryptarithmetic

- **Goal:** Build fluency with integer vars, bool vars, basic global constraints. Extend `cpsat-kt` with what's missing.
- **New concepts:** `AddAllDifferent`, `AddAbsEquality`, `OnlyEnforceIf`, linearization patterns (XOR, max, abs, OR).
- **Hands-on:**
  - N-Queens (N=8, 12, 50, 200) in Python and Kotlin (`cpsat-kt`).
  - SEND+MORE=MONEY.
  - One cryptarithmetic puzzle of your choice.
  - If `cpsat-kt` lacks something (likely `OnlyEnforceIf` → `enforceIf`), add it to the library in a separate commit.
- **Deliverable:** `apps/py-cp-sat/ch04-puzzles/`, `apps/kt-cp-sat/ch04-puzzles/`, tests.
- **Self-check:**
  - How does `AllDifferent` scale differently than pairwise `!=`?
  - What's `OnlyEnforceIf` for, when is a half-reified constraint not enough?
  - Time N=200 queens — what dominates?
- **Time:** ~3h.

#### Chapter 5 — Optimization: objectives, bounds, callbacks

- **Goal:** Move from CSP (feasible) to COP (optimal). Understand bounds and how to read them.
- **New concepts:** `Minimize`/`Maximize`, objective value vs best bound, optimality gap, `SolutionCallback` (in `cpsat-kt`: `Flow<Solution>`), enumerating all solutions, time limits, `numSearchWorkers`.
- **Hands-on:**
  - Knapsack (0-1 and bounded) in Py + Kt.
  - Solve + enumerate via callback / Flow.
  - Plot objective + bound over time.
  - Extend `cpsat-kt` with `solveFlow` if not yet implemented.
- **Deliverable:** `apps/*/ch05-optimization/` + `docs/chapters/05-optimization.md` with convergence plot.
- **Self-check:**
  - What does "bound" actually mean, how is it computed?
  - When is it OK to stop early with a gap?
  - Why can parallel search make the log look non-monotonic?
- **Time:** ~3h.

#### Chapter 6 — The global-constraints tour

- **Goal:** Know which global constraints exist, when to reach for each. Extend `cpsat-kt` with the remaining ones.
- **New concepts:** `Circuit`, `Table`, `Element`, `Automaton`, `Inverse`, `LexicographicLessEqual`, `Reservoir`.
- **Hands-on:**
  - TSP on 8 cities via `Circuit` (Py + Kt).
  - Shift-pattern allowed transitions via `Automaton` (sneak preview of NSP).
  - Allowed assignments lookup via `Table`.
  - Add any missing global to `cpsat-kt` (probably `automaton`, `circuit`, `table`, `element`, `inverse`, `lexLeq`, `reservoir`).
- **Deliverable:** `apps/*/ch06-globals/` — one tiny program per constraint.
- **Self-check:**
  - Why is `Circuit` faster than MTZ for TSP?
  - What's `Automaton` doing under the hood?
  - When prefer `Table` over inline logic?
- **Time:** ~3h.

At this point `cpsat-kt` has **~80% of CP-SAT's surface** covered. Good enough for Phases 3–5. We'll add scheduling primitives in Phase 4 and NSP-specific helpers as needed.

---

### Phase 3 — MiniZinc (the declarative viewpoint)

#### Chapter 7 — MiniZinc tour

- **Goal:** Read and write MiniZinc. Understand what FlatZinc is.
- **New concepts:** `.mzn` / `.dzn`, `array of var`, `forall`, `sum`, `constraint`, `solve minimize / satisfy`, MiniZinc IDE, backends (Gecode, Chuffed, CP-SAT, HiGHS).
- **Hands-on:** Re-solve N-Queens, knapsack, SEND+MORE in MiniZinc. Run each on 2 backends, compare times.
- **Deliverable:** `apps/mzn/ch07-tour/` with `.mzn` + `.dzn` files; a `README` comparing to CP-SAT.
- **Self-check:**
  - The `.mzn → FlatZinc → solver` pipeline?
  - Why do the same `.mzn` timings differ across backends?
  - When does declarative modeling *hurt* performance?
- **Time:** ~2h.
- **Reading:** [`docs/knowledge/minizinc/overview.md`](knowledge/minizinc/overview.md) full.

#### Chapter 8 — MiniZinc ↔ CP-SAT: prototype-then-port

- **Goal:** Use MiniZinc as a *specification tool* — prototype there, port to CP-SAT Py + Kt.
- **New concepts:** MiniZinc-Python API, CP-SAT as a MiniZinc backend, two integration patterns.
- **Hands-on:** Small NSP instance — write in MiniZinc first, then Py + Kt. Confirm same objective.
- **Deliverable:** `apps/mzn/ch08-nsp-tiny.mzn` + matching Py + Kt implementations.
- **Self-check:**
  - What do you gain from writing MiniZinc first? What do you lose?
  - Which constraints translate cleanly, which don't?
- **Time:** ~2h.

---

### Phase 4 — Scheduling primitives

#### Chapter 9 — Intervals, NoOverlap, Cumulative (Job-Shop)

- **Goal:** Model a scheduling problem with real time and resources.
- **New concepts:** `IntervalVar`, `OptionalInterval`, `NoOverlap`, `Cumulative`, makespan, critical path. Extend `cpsat-kt` with interval DSL (`interval { start; size; end }`, `noOverlap`, `cumulative`).
- **Hands-on:**
  - Classic Job-Shop (JSSP) from OR-Tools docs, Py + Kt.
  - Add resource capacity via `Cumulative`.
  - Gantt chart (matplotlib Py / simple SVG Kt).
- **Deliverable:** `apps/*/ch09-jobshop/`.
- **Self-check:**
  - Difference between `NoOverlap` and `Cumulative(capacity=1)`?
  - When do you need `OptionalInterval`?
  - Minimize makespan vs minimize weighted tardiness?
- **Time:** ~3h.

#### Chapter 10 — Time, shifts, transitions

- **Goal:** Encode *calendar* time and *shift transition rules* (heart of NSP).
- **New concepts:** Boolean shift-grid vs interval formulation; `AddAutomaton` for forbidden transitions ("no morning after night"); sliding-window; minimum rest.
- **Hands-on:** 1-nurse, 14-day toy with 3 shifts/day, max 5 consecutive working days, 11h rest rule, weekends-as-block preference.
- **Deliverable:** `apps/*/ch10-shifts/` + writeup comparing the two formulations.
- **Self-check:**
  - When is Boolean-grid better than intervals? When worse?
  - Sketch the automaton for "no morning after night."
  - Encode "exactly 2 weekends off in 4 weeks"?
- **Time:** ~3h.
- **Reading:** [`docs/knowledge/nurse-scheduling/overview.md`](knowledge/nurse-scheduling/overview.md) formal-model + constraints-taxonomy sections.

---

### Phase 5 — The Nurse Scheduling Problem

Where everything converges.

#### Chapter 11 — NSP v1: toy instance, hard constraints only

- **Goal:** Solve a fully-specified toy NSP (5 nurses × 14 days × 3 shifts) with every hard constraint in the textbook.
- **New constraints covered:** daily coverage, nurse availability, skill mix, min/max shifts per week, min rest, forbidden transitions, max consecutive days, fixed days off.
- **Hands-on:**
  - Python v1 (`apps/py-cp-sat/ch11-nsp-v1/`).
  - Kotlin v1 (`apps/kt-cp-sat/ch11-nsp-v1/`) via `cpsat-kt`.
  - Shared JSON instance schema in `apps/shared/nsp-instance.schema.json`.
  - Output: ASCII schedule + validator.
- **Deliverable:** both v1 solvers + `data/nsp/toy-01.json` + `tools/validate-schedule.py`.
- **Self-check:**
  - Is your schedule a certificate you could hand to a nurse manager?
  - Can you *prove* infeasibility when coverage is too tight?
  - How long does it take? Is the presolved model size what you expected?
- **Time:** ~4h.

#### Chapter 12 — NSP v2: soft constraints, preferences, fairness

- **Goal:** Add the objective — nurse preferences, shift-pattern quality, fairness across the team.
- **New concepts:** soft constraints as penalties; weighted objective design; lexicographic objectives (coverage dominates preference); max-min fairness vs sum-of-deviations. Extend `cpsat-kt` with `lexicographic { }` helper.
- **Hands-on:**
  - v1 → v2: weekend-off preferences, hard-no overnight for 1 nurse, preferred partner shifts.
  - Workload-balance term: `max(hours_i) - min(hours_i)`.
  - Weighting experiments; plot Pareto-ish tradeoffs.
- **Deliverable:** `apps/*/ch12-nsp-v2/` + `docs/chapters/12-nsp-v2.md` experiment log.
- **Self-check:**
  - Why is "sum of preference violations" often a terrible objective?
  - How does lexicographic optimization work in CP-SAT?
  - How sensitive is the solution to weight tuning?
- **Time:** ~4h.

#### Chapter 13 — NSP v3: benchmark-scale & solver tuning

- **Goal:** Tackle a realistic instance from **INRC-II** (30–120 nurses, 4-week horizon) and push performance.
- **New concepts:** solution hints (warm start), `num_search_workers`, `linearization_level`, `cp_model_presolve`, Large Neighborhood Search, diversified portfolios, reading the search log.
- **Hands-on:**
  - INRC-II instance loader (XML/JSON) in `tools/nsp-loader/` (Py + Kt).
  - Baseline solve: time-to-first-feasible, time-to-optimal-or-gap.
  - Tune parameters; document what moves the needle.
  - Optional: week-by-week decomposition.
  - Compare Py vs Kt wall-clock (they should be within noise — the solver is C++).
- **Deliverable:** `apps/*/ch13-nsp-v3/` + `docs/chapters/13-nsp-v3-benchmarks.md` (parameter table, timings).
- **Self-check:**
  - Which parameter helped most? Why?
  - How do you know when you've squeezed the model dry vs need search help?
  - Could a MILP solver (HiGHS) solve this? Try and compare.
- **Time:** ~6h (including reading).

---

### Phase 6 — Spec-driven app design

#### Chapter 14 — Write & LOCK the NSP app spec

- **Goal:** Produce a complete, locked markdown specification for the end-to-end NSP app. No app code written until this is locked by Vanja.
- **New concepts:** spec-driven development, structured markdown specs, acceptance criteria, API-first design, OpenAPI 3.1.
- **Hands-on:**
  - Write `specs/nsp-app/` with these files:
    - `00-overview.md` — what we're building, who it's for, the one-liner
    - `01-vision-and-goals.md` — north-star + non-goals
    - `02-user-stories.md` — primary flows as Given/When/Then
    - `03-domain-model.md` — Nurse, Shift, Instance, Schedule, Constraint, Preference entities + relationships
    - `04-functional-requirements.md` — FR-1..FR-N
    - `05-non-functional-requirements.md` — perf (solve ≤30s for toy, ≤5min for INRC-II mid-size), a11y, security, logging
    - `06-api-contract.md` — endpoint list + full OpenAPI 3.1 YAML in `apps/shared/openapi.yaml`
    - `07-ui-ux.md` — wireframes (ASCII or hand-drawn), user flows, color/typography choices
    - `08-data-model.md` — JSON schemas, DB ERD, instance file format
    - `09-acceptance-criteria.md` — how we know the app is "done"
    - `README.md` — spec index + version history
  - Review together, iterate, Vanja explicitly says "locked v1.0".
- **Deliverable:** `specs/nsp-app/` at v1.0, tagged in git (`spec-nsp-app-v1.0`).
- **Self-check:**
  - Could another engineer implement the app from only the spec without asking you anything? If no, what's missing?
  - Does every functional requirement map to at least one acceptance criterion?
  - Does the OpenAPI contract cover every user story?
- **Time:** ~4h.

---

### Phase 7 — End-to-end application

Built strictly from the locked spec.

#### Chapter 15 — The two backends

- **Goal:** FastAPI + Ktor backends, identical OpenAPI contract, solve in background.
- **New concepts:** Pydantic v2 ↔ Kotlin `@Serializable` data class parity; coroutine cancellation (Ktor) / async cancellation (FastAPI); SSE/WebSocket streaming; background job queue.
- **Hands-on:**
  - `apps/py-api/` (FastAPI): implement the OpenAPI. Solving runs in a thread-pool (CP-SAT releases GIL).
  - `apps/kt-api/` (Ktor 3): same endpoints, coroutine + `Dispatchers.Default` for solves.
  - `apps/shared/openapi.yaml` as the source of truth; generate client SDKs for the web.
  - Integration test: same instance, both backends produce equivalent-quality schedules.
- **Deliverable:** two running APIs + the shared contract + integration test.
- **Self-check:**
  - How do you cancel a running solve cleanly in each runtime?
  - Right status-code for "still thinking, partial solution available"?
- **Time:** ~5h.

#### Chapter 16 — The web UI

- **Goal:** Beautiful, usable roster visualization + what-if editing.
- **New concepts:** Vite 6 setup; React Router v7 framework mode (file-system routing, loaders, actions); React 19 Suspense + use() for streamed solutions; TanStack Query; constraint-violation highlighting; manual edit → re-validate.
- **Hands-on:**
  - `apps/web/` (Vite + RR7 + Tailwind 4 + shadcn/ui).
  - Screens: (a) upload/select instance, (b) solve with progress + streaming bound, (c) schedule grid with coverage/fairness KPIs, (d) manual edit → re-validate → re-solve.
  - Backend toggle: talk to Python or Kotlin API.
- **Deliverable:** `apps/web/` + screenshots in `docs/chapters/16-web.md`.
- **Self-check:**
  - How do you show *why* a cell is highlighted red?
  - How does streaming improve UX vs a spinner?
- **Time:** ~6h.

#### Chapter 17 — Polish, containerize, deploy

- **Goal:** End-to-end run locally and on a cheap cloud. Observability + basic ops.
- **New concepts:** Docker multi-stage builds (Python + Kotlin + Node); `docker compose up`; OpenTelemetry basics; seed data; GH Actions CI.
- **Hands-on:**
  - Dockerfiles for py-api, kt-api, web.
  - `docker-compose.yml` with both backends + UI.
  - Simple auth (API key) + request logging.
  - Deploy: Fly.io (default) or local-only (if Vanja prefers).
- **Deliverable:** `docker compose up` works end-to-end. Deploy URL or deploy runbook.
- **Self-check:**
  - Biggest surprise shipping a CP app?
  - Where are the latency / memory / cost cliffs?
- **Time:** ~4h.

---

### Phase 8 — Ecosystem & mastery retro

#### Chapter 18 — Port to Timefold + Choco, write the retro

- **Goal:** Know the neighbors. Port NSP v1 (just hard constraints) to **Timefold** and to **Choco**; write a comparison. Close with a mastery retrospective.
- **New concepts:** Timefold's local-search-first architecture (`PlanningSolution`, `PlanningEntity`, `ConstraintCollectors`); Choco's Kotlin-from-Java similar pain (argument for `cpsat-kt` style wrapper there too).
- **Hands-on:**
  - `apps/alt-solver/timefold/` (Kotlin + Timefold) — NSP v1 port.
  - `apps/alt-solver/choco/` (Java/Kotlin + Choco) — NSP v1 port.
  - Comparison table: model-size LOC, solve-time, ergonomics, debugging affordances.
  - Write `docs/chapters/18-ecosystem-retro.md`: when I'd pick CP-SAT vs Timefold vs Choco vs MiniZinc-on-Chuffed.
  - **Mastery retrospective:** what stuck, what didn't, what's next (advanced CP? routing? schedule optimization at scale?).
- **Deliverable:** both ports + the ecosystem retro + overall mastery writeup.
- **Self-check:**
  - Where does CP-SAT win over all of these? Where does it lose?
  - What would you pick for a production rostering SaaS today?
- **Time:** ~5h.
- **Reading:** [`docs/knowledge/ecosystem/overview.md`](knowledge/ecosystem/overview.md) full.

---

## 5. Cross-cutting deliverables

Built incrementally:

- `libs/cpsat-kt/` — the Kotlin DSL library (first-class artifact, ~Ch. 3 onwards)
- `specs/nsp-app/` — locked app spec (Ch. 14)
- `apps/py-cp-sat/` — Python learning chapter code
- `apps/kt-cp-sat/` — Kotlin learning chapter code (uses `cpsat-kt`)
- `apps/mzn/` — MiniZinc models
- `apps/py-api/` / `apps/kt-api/` — FastAPI + Ktor NSP app backends
- `apps/web/` — Vite + RR7 frontend
- `apps/shared/` — OpenAPI contract + JSON schemas
- `apps/alt-solver/` — Timefold + Choco ports (Ch. 18)
- `data/nsp/` — toy instances + NSPLib/INRC-II loaders
- `tools/` — validators, instance converters, benchmark runner
- `benchmarks/` — baseline + tuned results
- `docs/chapters/NN-*.md` — per-chapter write-ups (your words)
- `docs/knowledge/` — reference encyclopedia (grows as we hit new topics)
- `docs/adr/` — architecture decision records
- `.github/workflows/` — CI

---

## 6. Target repo layout (after post-lock scaffolding)

```
cp-deep-dive/
├── README.md                    <- canonical entry point
├── CLAUDE.md
├── AGENTS.md
├── .claude/memory/              <- symlinked
├── .mcp.json
├── docs/
│   ├── overview.md
│   ├── plan.md                  <- this file
│   ├── chapters/                <- per-chapter teaching notes
│   ├── knowledge/               <- reference encyclopedia
│   └── adr/                     <- ADRs
├── libs/
│   └── cpsat-kt/                <- Kotlin DSL (built in Ch. 3)
├── specs/
│   └── nsp-app/                 <- locked app spec (written in Ch. 14)
├── apps/
│   ├── py-cp-sat/               <- Python chapters
│   ├── kt-cp-sat/               <- Kotlin chapters (uses cpsat-kt)
│   ├── mzn/                     <- MiniZinc models
│   ├── py-api/                  <- FastAPI backend
│   ├── kt-api/                  <- Ktor backend
│   ├── web/                     <- Vite + RR7 frontend
│   ├── shared/                  <- OpenAPI + JSON schemas
│   └── alt-solver/              <- Timefold + Choco ports
├── data/nsp/
├── benchmarks/
├── tools/
└── .github/workflows/
```

---

## 7. Ground rules & working agreement

- **Vanja drives, Claude teaches.** Vanja types; Claude explains, suggests, reviews, unblocks.
- **Pause any time.** If a concept isn't clicking, we rewind, find a smaller example, or drop a rabbit hole.
- **No hand-waving.** Skipping a step requires a follow-up question.
- **Every chapter ends with a committed deliverable.** No WIP rotting on branches.
- **Every confusing moment becomes a chapter note.** Vanja's words, for Vanja's future self.
- **Disagreements become ADRs.** `docs/adr/NNNN-slug.md`.
- **Plan is a living doc.** We edit when reality disagrees. No pride.
- **`cpsat-kt` evolves with chapters.** If a chapter needs an API the wrapper lacks, we add it (tested, docstringed, committed) *before* the chapter code.
- **Spec is frozen before app code.** Spec amendments follow an explicit review step.

---

## 8. What's already in the repo (Phase 0 ✅)

- Repo skeleton + `CLAUDE.md` + README + AGENTS + `.gitignore`
- `.mcp.json` with QMD HTTP at `localhost:8181`
- `.claude/memory/` symlink plumbing + 6 memory files
- QMD collection `cp-deep-dive-docs` indexed on `docs/`
- 7 knowledge docs under `docs/knowledge/`: cp-theory, cp-sat (overview + python-vs-kotlin), cpsat-kt (design doc), minizinc, nurse-scheduling, ecosystem
- This plan (v0.2)

---

## 9. Next step

Vanja reviews this plan and either:
- Says "**locked, green-light v1.0**" → Claude scaffolds everything in one pass (creates `libs/cpsat-kt/` skeleton, `specs/nsp-app/` skeleton, all chapter starter folders under `apps/`, `docs/chapters/NN-*.md` stubs with full explanations + exercises, CI, `data/nsp/` with toy instance), pushes, then Chapter 1 is ready to open from `README.md`.
- Or pushes back on any chapter / phase / tooling choice → we iterate to v0.3 and loop.
