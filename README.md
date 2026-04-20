# cp-deep-dive

> **👋 Vanja — start here.** This README is your map. Read it top-to-bottom, then follow the numbered links.

A structured, long-form deep dive into **Constraint Programming / Constraint Optimization**, using the **Nurse Scheduling Problem (NSP)** as the running use case. The goal is mastery, not coverage — **18 chapters across 8 phases**, everything in Python AND Kotlin, culminating in a full end-to-end NSP app with a web UI.

---

## 1. What this project is

- **Topic:** Constraint Programming / Constraint Optimization, in depth.
- **Running problem:** Nurse Scheduling Problem (NSP) — the canonical real-world CP scheduling problem.
- **Primary solver:** [Google OR-Tools **CP-SAT**](https://developers.google.com/optimization) — hybrid CP + SAT + LP, best-in-class for large integer combinatorial problems.
- **Languages:** **Python** (first-party CP-SAT bindings) **+ Kotlin** (via our own idiomatic DSL library, `cpsat-kt`). Dual-language parity is mandatory in every chapter.
- **Teaching companion:** [MiniZinc](https://www.minizinc.org/) — solver-agnostic declarative modeling, used to clarify the math.
- **Capstone:** a full NSP app — FastAPI backend + Ktor 3 backend (same OpenAPI contract) + Vite + React 19 + React Router v7 frontend, built from a locked markdown spec.

This is a **private learning repo**. Public artifacts (talks, blog posts, the `cpsat-kt` library) may emerge later.

---

## 2. Your path through this repo

Follow these links **in order** on your first pass:

1. **[docs/overview.md](docs/overview.md)** — the field, the tools, where we are *(5-min read)*
2. **[docs/plan.md](docs/plan.md)** — the full 18-chapter roadmap with chapter-by-chapter breakdown *(20-min read)*
3. **[docs/knowledge/](docs/knowledge/)** — reference encyclopedia (don't binge; query via QMD or ask Claude)
4. **[CLAUDE.md](CLAUDE.md)** — how Claude works in this project (teaching mode, routing, autonomous behaviors)

Then, **chapter by chapter from Chapter 1**:

- Open [`docs/chapters/01-what-is-cp.md`](docs/chapters/01-what-is-cp.md)
- Read the chapter, do the exercises in `apps/py-cp-sat/chNN-*/` and `apps/kt-cp-sat/chNN-*/`
- Ask Claude when stuck; Claude updates docs/plan as you progress
- Mark complete in `docs/plan.md`; move to Chapter 2

---

## 3. The 18-chapter ladder at a glance

```
Phase 1  CP theory                          ch. 1
Phase 2  CP-SAT basics + build cpsat-kt     ch. 2–6     ← Chapter 3 builds our Kotlin DSL library
Phase 3  MiniZinc                           ch. 7–8
Phase 4  Scheduling primitives              ch. 9–10
Phase 5  Nurse Scheduling I / II / III      ch. 11–13
Phase 6  Spec-driven app design             ch. 14      ← write + lock the spec before any app code
Phase 7  End-to-end NSP application         ch. 15–17
Phase 8  Port to Timefold + Choco + retro   ch. 18
```

Full detail: **[docs/plan.md](docs/plan.md)**.

---

## 4. Repo layout

```
cp-deep-dive/
├── README.md                    ← you are here
├── CLAUDE.md                    ← how Claude collaborates in this repo
├── AGENTS.md
├── docs/
│   ├── overview.md              ← the field in one page
│   ├── plan.md                  ← 18-chapter living roadmap
│   ├── chapters/                ← per-chapter teaching notes (filled as you go)
│   ├── knowledge/               ← reference encyclopedia (indexed by QMD)
│   │   ├── cp-theory/
│   │   ├── cp-sat/
│   │   ├── cpsat-kt/            ← design doc for our Kotlin DSL
│   │   ├── minizinc/
│   │   ├── nurse-scheduling/
│   │   └── ecosystem/
│   └── adr/                     ← architecture decision records
├── libs/
│   └── cpsat-kt/                ← our idiomatic Kotlin DSL over OR-Tools CP-SAT
│                                  (first-class artifact, eventually publishable to Maven Central)
├── specs/
│   └── nsp-app/                 ← locked markdown spec for the end-to-end app
├── apps/
│   ├── py-cp-sat/               ← Python chapter code
│   ├── kt-cp-sat/               ← Kotlin chapter code (uses cpsat-kt)
│   ├── mzn/                     ← MiniZinc models
│   ├── py-api/                  ← FastAPI backend (Phase 7)
│   ├── kt-api/                  ← Ktor 3 backend (Phase 7)
│   ├── web/                     ← Vite + React 19 + React Router v7 frontend (Phase 7)
│   ├── shared/                  ← OpenAPI + JSON schemas
│   └── alt-solver/              ← Timefold + Choco ports (Phase 8)
├── data/nsp/                    ← NSP instances (toy + NSPLib + INRC-I/II)
├── benchmarks/
├── tools/
│   ├── setup-memory-link.sh     ← one-time: symlink Claude memory into repo
│   └── setup-qmd-hook.sh        ← one-time: install git post-commit QMD reindex hook
└── .github/workflows/           ← CI (lint + test Python + Kotlin)
```

Anything marked "first-class artifact" has its own build, README, and versioning — not coupled to the learning chapters.

---

## 5. Tech stack (locked in plan v0.2)

| Layer | Choice |
|---|---|
| Python | **3.12+** via **uv** |
| JVM | **JDK 25 (LTS)** |
| Kotlin | **2.1+**, Gradle 9 Kotlin DSL + version catalog |
| Node | **22 LTS** |
| Primary solver | **OR-Tools CP-SAT 9.15+** |
| Kotlin solver binding | **`cpsat-kt`** (this project's own DSL — built in Chapter 3) |
| Modeling | **MiniZinc 2.8+** (teaching tool, not in production app) |
| Python backend | **FastAPI + Pydantic v2 + uvicorn** |
| Kotlin backend | **Ktor 3.x** |
| Frontend | **Vite 6 + React 19 + React Router v7 (framework mode) + TypeScript 5 + Tailwind 4 + shadcn/ui + TanStack Query 5** |
| Testing | pytest + hypothesis (Py) / Kotest 5 (Kt) |
| Lint | ruff + mypy (Py) / ktlint + detekt (Kt) |
| Containers | Docker Compose |
| Deploy | Fly.io (default, decided in Ch. 17) |
| CI | GitHub Actions |

---

## 6. First-time setup (per machine, after `git clone`)

```bash
# 1. Symlink Claude Code project memory into this repo (so memory persists via git)
./tools/setup-memory-link.sh

# 2. Install git post-commit hook for QMD auto-reindex
./tools/setup-qmd-hook.sh

# 3. Register the docs folder with QMD (once per machine)
qmd collection add docs --name cp-deep-dive-docs
qmd update && qmd embed
```

**Prerequisites:**

- [QMD](https://github.com/tobi/qmd) v2+ with `io.qmd.daemon` launchd agent running (local HTTP MCP on port 8181).
- **For Python chapters** (Phase 2+): [uv](https://github.com/astral-sh/uv) installed. Each chapter has its own `pyproject.toml`.
- **For Kotlin chapters** (Phase 2+): JDK 25 LTS. Recommend [SDKMAN!](https://sdkman.io/): `sdk install java 25-tem`. Gradle comes from the wrapper.
- **For MiniZinc chapters** (Phase 3): MiniZinc 2.8+ ([download](https://www.minizinc.org/software.html)).
- **For the app (Phase 7)**: Node 22 LTS, Docker.

No need to install anything until you hit the chapter that needs it — each chapter's README lists its prerequisites.

---

## 7. How you and Claude work together

- **Vanja drives, Claude teaches.** You type; Claude explains, suggests, reviews, unblocks.
- **README → plan → chapter.** Always enter through this README, navigate to the current chapter, work it, commit, move on.
- **Dual-language parity is mandatory.** Every chapter has both `apps/py-cp-sat/chNN-*/` and `apps/kt-cp-sat/chNN-*/`. Kotlin code uses `cpsat-kt`, never raw `com.google.ortools.sat.*` (one exception: Ch. 2 shows raw-Java pain to motivate the wrapper).
- **Spec before app code.** The Phase 7 app is built strictly from `specs/nsp-app/`, locked in Phase 6.
- **Commit after each chapter or meaningful change.** Messages follow `<area>: <action>` (see [CLAUDE.md](CLAUDE.md) for examples).
- **Claude updates docs as we go.** Plan marks chapters done, knowledge docs absorb new learnings, ADRs capture decisions.

Full working agreement: [CLAUDE.md](CLAUDE.md).

---

## 8. Status

**Phase 0 — Setup & Research: ✅ Complete**

- Repo skeleton, CLAUDE.md, plan v0.2, 7 knowledge docs written and QMD-indexed.
- GitHub repo private under `vpetreski/cp-deep-dive`.
- Memory system primed with user/feedback/project context.

**Phase 0.5 — Full scaffold: ✅ Complete**

- `libs/cpsat-kt/` v0.1.0 library with 41/41 tests passing (`./gradlew test` green).
- All 18 chapter MDs drafted in `docs/chapters/` with intuition → formal → Python → Kotlin → MiniZinc → exercises structure.
- Python uv workspace at `apps/py-cp-sat/` (ch02 fully working, ch04–ch13 stubs) + FastAPI skeleton at `apps/py-api/`.
- Kotlin composite build at `apps/kt-cp-sat/` (ch02 raw-Java demo runs) + Ktor 3 skeleton at `apps/kt-api/`.
- Web frontend at `apps/web/` (Vite 8 + React 19 + RR7 framework mode + Tailwind 4 + shadcn/ui) — build/test/lint green.
- `apps/shared/openapi.yaml` (OpenAPI 3.1) + 4 JSON schemas validated against 2020-12.
- `specs/nsp-app/` 10-file skeleton (unlocked, filled in Chapter 14).
- 4 ADRs (`docs/adr/`), GitHub Actions CI (Python + Kotlin + Web parallel jobs), `benchmarks/` + `data/nsp/` (toy-01, toy-02 instances + schema).
- `alt-solver/` Timefold + Choco stubs for Phase 8.

**Phase 1 — Chapter 1 ready to open:** [`docs/chapters/01-what-is-cp.md`](docs/chapters/01-what-is-cp.md)

---

## 9. Quick links

- 📖 [Field overview](docs/overview.md) — read this first
- 🗺️ [The 18-chapter plan](docs/plan.md) — the roadmap
- 🤖 [How Claude works here](CLAUDE.md) — collaboration rules
- 🧠 [Knowledge base](docs/knowledge/) — reference docs
- 📦 [`cpsat-kt` design](docs/knowledge/cpsat-kt/overview.md) — our Kotlin DSL library

---

## 10. Privacy & licensing

- Private repo on GitHub (`vpetreski/cp-deep-dive`).
- Research notes paraphrase papers/articles — cite sources; don't paste copyrighted works.
- Code is Vanja's; MIT-license it (or whatever) before any public release.
- `libs/cpsat-kt/` specifically may go Apache-2.0 on Maven Central to match OR-Tools.
