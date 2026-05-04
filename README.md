# cp-deep-dive

*A practical, top-to-bottom deep dive into Constraint Programming with
OR-Tools CP-SAT — in Python **and** Kotlin — culminating in a full
end-to-end Nurse Scheduling Problem (NSP) application.*

> Why does this repo exist? Read the companion essay:
> **[The field that schedules your world](https://vanja.io/the-field-that-schedules-your-world/)**
> — a plain-English tour of Constraint Programming and why the Nurse Scheduling
> Problem is the perfect way to learn it.

[![CI](https://github.com/vpetreski/cp-deep-dive/actions/workflows/ci.yml/badge.svg)](https://github.com/vpetreski/cp-deep-dive/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Python](https://img.shields.io/badge/python-3.12%2B-informational)](https://www.python.org/)
[![Kotlin](https://img.shields.io/badge/kotlin-2.3%2B-7F52FF)](https://kotlinlang.org/)
[![JDK](https://img.shields.io/badge/JDK-25_LTS-orange)](https://adoptium.net/)

---

## Estimated time to mastery

| What | Hours |
|---|---|
| Reading + coding the 18 chapters (one concept → Python → Kotlin → MiniZinc) | **~66 h** |
| Working through end-of-chapter exercises | **+20–30 h** |
| Reading the linked papers and the OR-Tools / MiniZinc docs | **+5–10 h** |
| **Total — cold start to "I can model and solve a real production problem"** | **~100–120 h** |

Most people finish the 18-chapter spine in **2–3 weeks full-time**, or
**about 3 months** at 8–10 hours a week. Time compresses significantly if you
already know one of the two languages or have prior OR / SAT experience.

See [`docs/plan.md`](docs/plan.md) for per-chapter hour targets and
[`PROGRESS.md`](PROGRESS.md) for a personal tracker you can fork.

---

## Table of contents

- [What you'll learn](#what-youll-learn)
- [Who this is for](#who-this-is-for)
- [The 18-chapter ladder](#the-18-chapter-ladder)
- [What you'll build](#what-youll-build)
- [Quick start](#quick-start)
- [How to learn with this repo](#how-to-learn-with-this-repo)
- [Track your progress](#track-your-progress)
- [Using Claude Code (optional but recommended)](#using-claude-code-optional-but-recommended)
- [Repo layout](#repo-layout)
- [Tech stack](#tech-stack)
- [Status](#status)
- [Contributing, licence, citation](#contributing-licence-citation)

---

## What you'll learn

- The vocabulary of Constraint Programming: variables, domains, propagation,
  search, global constraints, symmetry breaking, linear relaxations.
- How **OR-Tools CP-SAT** — Google's hybrid CP + SAT + LP solver — actually
  works, and how to coax it into solving your problem *fast*.
- How to model in **Python** with the first-party bindings, and in **Kotlin**
  with our idiomatic DSL [`cpsat-kt`](libs/cpsat-kt/) that wraps the
  OR-Tools Java API without leaking its Java-isms.
- How to use **MiniZinc** as a declarative companion — write the model
  solver-agnostically, then port it to CP-SAT by hand.
- How to build a full **Nurse Scheduling Problem** solver and ship it behind
  twin FastAPI / Ktor 3 backends with a Vite + React web UI.
- How to **compare** CP-SAT against **Timefold** (ex-OptaPlanner local search)
  and **Choco** (classic CP backtracking) on the same instances, so you know
  when each solver wins.

## Who this is for

- **Software engineers** who have a real scheduling / allocation / routing
  problem and want to learn CP well enough to model it.
- **Data scientists and ML engineers** exploring combinatorial decision-making
  beyond gradient-based optimisation.
- **CS students** who've seen SAT / ILP in class and want to see CP at
  production scale.
- **Polyglots** who are comfortable in either Python or Kotlin and want to
  build fluency in the other.

You don't need prior CP experience. You do need comfort with one modern
programming language (Python or Kotlin), basic git, and a willingness to read
runnable code.

## The 18-chapter ladder

```
Phase 1  Intuition + vocabulary           ch. 1–3     first working solve
Phase 2  Core modelling                   ch. 4–6     puzzles, optimization, global constraints
Phase 3  Bridges                          ch. 7–8     MiniZinc → CP-SAT
Phase 4  Scheduling primitives            ch. 9–10    job-shop + shifts
Phase 5  Nurse Scheduling I / II / III    ch. 11–13   hard → soft → benchmarks
Phase 6  Your own DSL library             ch. 14      cpsat-kt internals
Phase 7  End-to-end NSP application       ch. 15–17   backends, frontend, deploy
Phase 8  Ecosystem                        ch. 18      Timefold, Choco, retro
```

Full breakdown with hours and concrete deliverables per chapter:
[`docs/plan.md`](docs/plan.md).

## What you'll build

Working by the end of the curriculum:

1. **`cpsat-kt`** — an idiomatic Kotlin DSL over OR-Tools CP-SAT, published
   as v0.1.0 in this repo under `libs/cpsat-kt/` (41+ Kotest specs passing,
   ready for Maven Central).
2. **`nsp-core`** — a shared NSP solver library in both Python
   (`apps/py-cp-sat/nsp-core/`) and Kotlin (`apps/kt-cp-sat/nsp-core/`).
3. **`py-api` / `kt-api`** — two production-grade backends for the same
   [OpenAPI 3.1 contract](apps/shared/openapi.yaml): FastAPI + SQLModel +
   SSE, and Ktor 3 + Exposed + SSE. Same endpoints, same schemas,
   byte-for-byte identical `/openapi.yaml` output, 30 and 17 tests respectively.
4. **`web`** — a Vite + React 19 + React Router v7 frontend: instance upload,
   live solve view (SSE-streamed incumbents), schedule / coverage / Gantt
   views, infeasibility explorer, backend switch, dark mode.
5. **Timefold and Choco ports** — the same NSP, solved two other ways, with
   a benchmark harness that runs all three solvers on every instance and
   emits CSV + per-run JSON under `benchmarks/results/`.

## Quick start

```bash
# 1. Clone
git clone https://github.com/vpetreski/cp-deep-dive.git
cd cp-deep-dive

# 2. Read the overview + the plan (30 min)
$EDITOR docs/overview.md docs/plan.md

# 3. Open Chapter 1 and follow from there
$EDITOR docs/chapters/01-what-is-cp.md
```

Per-language prerequisites (install only when you reach the phase that needs
them — each chapter README tells you):

- **Python chapters (Phase 2+):** Python 3.12+, [uv](https://github.com/astral-sh/uv).
  Then in the chapter directory: `uv sync --all-packages && uv run pytest`.
- **Kotlin chapters (Phase 2+):** JDK 25 ([Temurin via SDKMAN](https://sdkman.io/)).
  Then: `./gradlew test` (Gradle comes from the wrapper).
- **MiniZinc chapters (Phase 3):** [MiniZinc 2.8+](https://www.minizinc.org/software.html).
- **App build (Phase 7):** Node 22 LTS, Docker (for containerisation demo).

To run the finished end-to-end app today:

```bash
# Python backend
cd apps/py-api && uv sync && uv run uvicorn py_api.main:app --reload

# Kotlin backend (in another shell)
cd apps/kt-api && ./gradlew run

# Web UI (in a third shell)
cd apps/web && npm ci && npm run dev
```

Open `http://localhost:5173`, upload `data/nsp/toy-01.json`, and watch it solve.

## How to learn with this repo

Linear, one chapter at a time:

1. Read `docs/chapters/NN-*.md` — intuition → formal → worked example.
2. Run the **Python** code: `cd apps/py-cp-sat/chNN-* && uv run python -m <pkg>`.
3. Run the **Kotlin** twin: `cd apps/kt-cp-sat/chNN-* && ./gradlew run`.
4. Skim the MiniZinc model if the chapter has one in `apps/mzn/`.
5. Do the 3–5 exercises at the end of the chapter; hidden answers live in
   `solutions/` next to the exercise.
6. Tick the chapter off in your fork's `PROGRESS.md`, commit, move on.

Don't skip the Kotlin twin even if you're only here for Python (or vice
versa): translating a model into a second language is the single best trick
for understanding *what the model actually means* rather than what it looks
like in one dialect.

## Track your progress

[`PROGRESS.md`](PROGRESS.md) is a personal, markdown-only tracker. Fork the
repo, open that file in your copy, and tick items off as you go. It has
per-chapter checkboxes, space for estimated vs actual hours, a log for
exercise answers, and prompts for end-of-phase reflection.

Because it's ordinary markdown, you can:

- edit it in your editor or on GitHub directly;
- commit it on your own branch (`learn/<you>`) to have a history of your
  progress;
- ask Claude Code (see below) to "update `PROGRESS.md` with what I just
  finished" — Claude can edit it the same way you can.

## Using Claude Code (optional but recommended)

This repo was written alongside [Claude Code](https://claude.com/claude-code),
and is instrumented for agentic collaboration:

- **[`CLAUDE.md`](CLAUDE.md)** declares project-wide rules and routing
  (where to write docs, which files are authoritative, commit conventions)
  so Claude behaves consistently across sessions.
- **[QMD](https://github.com/tobi/qmd)** indexes `docs/knowledge/` so Claude
  can semantically retrieve chunks instead of reading whole files. The
  `.mcp.json` in the repo root wires QMD in as an MCP server. A post-commit
  hook re-indexes automatically.
- **`claude-memory/`** (mirrored from `~/.claude/projects/<slug>/memory/`
  per machine via `tools/setup-memory-hook.sh`) stores conversation-spanning
  facts about you and how you like to work. Lives outside `.claude/` because
  the latter is a hardcoded protected-path prefix that prompts on every edit.

You can absolutely learn the material without any of this — it's all plain
markdown and code. But with Claude Code attached, you can:

- ask "why does CP-SAT prefer `AddAllowedAssignments` here?" and get an
  explanation grounded in the current chapter's knowledge docs;
- paste an error from your exercise attempt and get a focused hint;
- say "mark Chapter 7 done in PROGRESS.md and open Chapter 8" and have it
  happen.

Per-machine setup if you want the full experience:

```bash
./tools/setup-memory-link.sh     # once per machine
./tools/setup-qmd-hook.sh        # once per machine
qmd collection add docs --name cp-deep-dive-docs
qmd update && qmd embed
```

## Repo layout

```
cp-deep-dive/
├── README.md                    ← you are here
├── CLAUDE.md                    ← project rules + agent routing
├── PROGRESS.md                  ← personal learner tracker (fork & fill)
├── CONTRIBUTING.md CODE_OF_CONDUCT.md SECURITY.md CHANGELOG.md CITATION.cff
├── LICENSE (Apache-2.0) NOTICE
├── docs/
│   ├── overview.md              ← the field in one page
│   ├── plan.md                  ← 18-chapter roadmap, hours, deliverables
│   ├── chapters/                ← per-chapter teaching notes
│   ├── knowledge/               ← reference encyclopedia (QMD-indexed)
│   │   ├── cp-theory/ cp-sat/ cpsat-kt/ minizinc/
│   │   ├── nurse-scheduling/ ecosystem/
│   └── adr/                     ← architecture decision records
├── libs/cpsat-kt/               ← our Kotlin DSL over OR-Tools CP-SAT
├── specs/nsp-app/               ← locked v1.0 NSP app spec (11 files)
├── apps/
│   ├── py-cp-sat/               ← Python chapter code (ch02–ch13 + nsp-core)
│   ├── kt-cp-sat/               ← Kotlin chapter code (ch02–ch13 + nsp-core)
│   ├── mzn/                     ← MiniZinc teaching models
│   ├── py-api/                  ← FastAPI NSP backend (Phase 7)
│   ├── kt-api/                  ← Ktor 3 NSP backend (Phase 7)
│   ├── web/                     ← Vite + React 19 + RR7 NSP frontend
│   ├── shared/                  ← OpenAPI 3.1 + JSON Schemas
│   └── alt-solver/              ← Timefold + Choco NSP ports
├── benchmarks/                  ← cross-solver benchmark runner + baselines
├── data/nsp/                    ← NSP instances (toy + NSPLib + INRC pointers)
├── tools/                       ← Claude Code + QMD setup scripts
└── .github/workflows/           ← CI (Python, Kotlin, Web jobs)
```

## Tech stack

| Layer | Choice |
|---|---|
| Python | 3.12+ via [uv](https://github.com/astral-sh/uv) |
| JVM | JDK 25 LTS |
| Kotlin | 2.3+, Gradle 9.4+ Kotlin DSL |
| Node | 22 LTS |
| Primary solver | OR-Tools CP-SAT 9.15+ |
| Kotlin DSL | [`cpsat-kt`](libs/cpsat-kt/) (ours, published from this repo) |
| Modelling companion | MiniZinc 2.8+ |
| Python backend | FastAPI + SQLModel + sse-starlette + Pydantic v2 |
| Kotlin backend | Ktor 3.x + Exposed + kotlinx.coroutines |
| Frontend | Vite + React 19 + React Router v7 + TypeScript 5 + Tailwind + shadcn/ui + TanStack Query |
| Testing | pytest + Hypothesis (Py), Kotest 5 (Kt), Vitest + Playwright (Web) |
| Lint | ruff + mypy --strict (Py), ktlint + detekt (Kt), eslint + `tsc --noEmit` (Web) |
| Containers | Docker, Docker Compose |
| CI | GitHub Actions |

## Status

**v0.1.0 — the curriculum is complete and runnable end-to-end.**

- ✅ 18 chapters written (`docs/chapters/`)
- ✅ `cpsat-kt` v0.1.0 with tests green
- ✅ Python nsp-core + Kotlin nsp-core libraries
- ✅ FastAPI backend (13 endpoints, 30 tests) and Ktor backend (13 endpoints, 17 tests)
- ✅ Web UI (9 routes, instance upload, live solve, schedule / coverage / Gantt / infeasibility views)
- ✅ Timefold + Choco ports
- ✅ Benchmark harness with baseline results for CP-SAT, Choco, Timefold on `toy-01` and `toy-02`
- ✅ NSP spec v1.0 locked (`specs/nsp-app/`)
- ✅ GitHub Actions CI green (Python, Kotlin, Web)

See [`CHANGELOG.md`](CHANGELOG.md) for the full release notes.

## Contributing, licence, citation

- **Contributing:** see [`CONTRIBUTING.md`](CONTRIBUTING.md). Typo fixes and
  clearer explanations are especially welcome; bigger changes should start as
  an issue. The `cpsat-kt` library accepts feature PRs under semver.
- **Code of Conduct:** [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) (Contributor
  Covenant 2.1).
- **Security:** private report flow in [`SECURITY.md`](SECURITY.md).
- **Licence:** [Apache-2.0](LICENSE) — same as OR-Tools, with third-party
  attributions in [`NOTICE`](NOTICE).
- **Citation:** [`CITATION.cff`](CITATION.cff) — the "Cite this repository"
  button on GitHub uses it.

If this curriculum helped you ship a real-world optimisation project, a
one-line note in the issue tracker would make the author's week.
