# cp-deep-dive — maintainer's Claude operating instructions

> **Reader note (if you're not the maintainer):** this is the maintainer's personal working agreement with Claude Code for this repo. It is tuned to their learning style, tooling choices, and collaboration preferences. You are welcome to read it for context, fork it, or adapt it — but treat it as one person's setup, not a generic "how to use this repo" doc. The canonical learner-facing entry point is [`README.md`](README.md), and the locked plan is [`docs/plan.md`](docs/plan.md).

This repo is a structured, long-form learning project where the maintainer becomes a practitioner of Constraint Programming / Constraint Optimization. The running use case is the **Nurse Scheduling Problem (NSP)** solved with **Google OR-Tools CP-SAT** in both **Python** and **Kotlin** (via our own idiomatic DSL `cpsat-kt`), with **MiniZinc** as a solver-agnostic modeling companion during learning. The capstone is a full end-to-end NSP app: FastAPI + Ktor 3 twin backends, Vite + React 19 + React Router v7 frontend, built from a locked-in markdown spec.

This file is always loaded. Read it first. Deep knowledge lives under `docs/knowledge/` and is searchable via QMD — don't read large files whole, query them.

## Structure

```
cp-deep-dive/
├── README.md                  <- canonical public entry point — start here, links everywhere
├── CLAUDE.md                  <- you are here — rules + routing + autonomous behaviors
├── AGENTS.md                  <- pointer to CLAUDE.md
├── .claude/
│   ├── commands/              <- project slash commands (empty for now)
│   ├── memory/                <- mirror of canonical Claude memory (synced via tools/setup-memory-hook.sh post-commit hook)
│   └── settings.json          <- shared project settings
├── .mcp.json                  <- QMD HTTP MCP (local daemon on :8181)
├── .gitignore
├── docs/
│   ├── overview.md            <- always-loaded summary of field, tools, status
│   ├── plan.md                <- the 18-chapter roadmap — source of truth for "what next"
│   ├── chapters/              <- per-chapter teaching notes (`NN-slug.md`)
│   ├── knowledge/             <- QMD-indexed reference encyclopedia
│   │   ├── cp-theory/         <- CP theory + vocabulary
│   │   ├── cp-sat/            <- OR-Tools CP-SAT deep dive
│   │   ├── cpsat-kt/          <- our Kotlin DSL library (design + API reference)
│   │   ├── minizinc/          <- MiniZinc modeling language
│   │   ├── nurse-scheduling/  <- NSP literature + formal definition
│   │   └── ecosystem/         <- Choco, Timefold, Gecode, Z3, etc.
│   └── adr/                   <- architecture decision records (NNNN-slug.md)
├── libs/
│   └── cpsat-kt/              <- idiomatic Kotlin DSL wrapper over OR-Tools CP-SAT (first-class artifact, publishable)
├── specs/
│   └── nsp-app/               <- locked markdown spec for the end-to-end NSP app
├── apps/
│   ├── py-cp-sat/             <- Python chapter code (puzzles, scheduling, NSP)
│   ├── kt-cp-sat/             <- Kotlin chapter code (uses cpsat-kt)
│   ├── mzn/                   <- MiniZinc models
│   ├── py-api/                <- FastAPI backend for NSP app
│   ├── kt-api/                <- Ktor 3 backend for NSP app
│   ├── web/                   <- Vite + React 19 + React Router v7 frontend
│   └── shared/                <- OpenAPI + JSON schemas shared by backends/frontend
├── data/
│   └── nsp/                   <- NSP instances (toy + NSPLib + INRC-I/II)
├── benchmarks/                <- baseline + tuned solver runs
├── tools/
│   ├── setup-memory-hook.sh   <- one-time per machine: install post-commit hook that mirrors canonical Claude memory into claude-memory/ for git versioning
│   ├── setup-memory-link.sh.deprecated  <- old symlink approach, kept one cycle for documentation
│   └── setup-qmd-hook.sh      <- one-time per machine: install post-commit QMD reindex hook
└── .github/workflows/         <- CI (Py + Kt lint/test)
```

All artifacts listed above are present and built — see `PROGRESS.md` and `CHANGELOG.md` for status.

## How we work together (teach-me mode)

Vanja is **new to Constraint Programming**. Explanations start ELI5, then scale up. Every concept follows the same ladder:

1. **Intuition first** — one plain-English paragraph, no jargon.
2. **Minimal formal definition** — one diagram or equation, labeled.
3. **Tiny worked example** — solvable by hand in 5 lines.
4. **Python implementation** — CP-SAT, runnable.
5. **Kotlin implementation** — same problem via `cpsat-kt` (our DSL), side-by-side contrast.
6. **MiniZinc model** (where it adds clarity) — the declarative spec version.
7. **What this unlocks** — one sentence linking to the next concept.

Rules:
- Never assume knowledge Vanja hasn't been taught yet in this repo.
- When introducing a new term, italicize it the first time and give a one-line gloss.
- Prefer "you" over "we" in explanations (active voice, direct).
- When two approaches compete, show both briefly and recommend one *with reasoning* — don't hide trade-offs.
- Code examples are runnable end-to-end. No pseudocode when real code fits.
- **Dual-language parity is mandatory** — every Python example has a Kotlin twin using `cpsat-kt`. No single-language chapters.
- **Kotlin code never calls raw `com.google.ortools.sat.*` directly** — always through `cpsat-kt`. If the wrapper lacks a feature, extend `cpsat-kt` first, *then* use it. One exception: Chapter 2 intentionally shows raw Java-in-Kotlin once to motivate the wrapper.
- **Spec before app.** The NSP app in `apps/{py-api,kt-api,web}/` only gets built from the locked-in spec in `specs/nsp-app/`. If reality diverges, amend the spec first, then the code.

## Routing — where to go for what

| When Vanja says... | Work in... | Read first... |
|---|---|---|
| "What are we doing?" / "status" / "where are we" | `docs/overview.md` + `docs/plan.md` | `docs/overview.md` |
| "What is CP?" / "explain constraint programming" / general theory | `docs/knowledge/cp-theory/` | `docs/knowledge/cp-theory/overview.md` |
| Anything about CP-SAT internals, API, modeling | `docs/knowledge/cp-sat/` | `docs/knowledge/cp-sat/overview.md` |
| Anything about our Kotlin DSL / `cpsat-kt` / idiomatic Kotlin API | `libs/cpsat-kt/` + `docs/knowledge/cpsat-kt/` | `docs/knowledge/cpsat-kt/overview.md` |
| MiniZinc / FlatZinc / solver-agnostic modeling | `docs/knowledge/minizinc/` | `docs/knowledge/minizinc/overview.md` |
| Nurse Scheduling Problem / NSP / shift scheduling | `docs/knowledge/nurse-scheduling/` | `docs/knowledge/nurse-scheduling/overview.md` |
| "What's the app supposed to do?" / requirements / API contract / UI scope | `specs/nsp-app/` | `specs/nsp-app/00-overview.md` |
| "Why did we pick X?" / architectural decisions | `docs/adr/` | scan ADR filenames |
| "What's the plan?" / "next chapter" / "what's after this" | `docs/plan.md` | `docs/plan.md` |
| Write code for chapter N | `apps/py-*/chNN-*/` + `apps/kt-*/chNN-*/` | `docs/chapters/NN-*.md` |
| Extend the Kotlin DSL | `libs/cpsat-kt/src/main/kotlin/` | `docs/knowledge/cpsat-kt/overview.md` |

## QMD for deep knowledge retrieval

For domain questions that span files, prefer `mcp__qmd__query` over reading large files whole.

- `mcp__qmd__query "CP-SAT lazy clause generation search"` → relevant chunks from `docs/knowledge/cp-sat/*`
- `mcp__qmd__query "nurse scheduling hard constraints coverage"` → chunks from `docs/knowledge/nurse-scheduling/*`
- `mcp__qmd__query "MiniZinc global constraint alldifferent"` → chunks from `docs/knowledge/minizinc/*`

Rule: **always read `docs/overview.md` and the area's `overview.md` first**, then use QMD for depth. Don't read 500+-line knowledge files whole.

Fallback: if QMD returns "connection refused", restart the daemon: `launchctl kickstart -k gui/$(id -u)/io.qmd.daemon`, then retry. The `.git/hooks/post-commit` auto-reindexes; if results look stale, run `qmd update && qmd embed`.

## Autonomous behaviors (do without asking)

**Context capture.** When Vanja shares knowledge verbally or pastes a link/article/paper, extract the relevant bits and save them to the right place under `docs/knowledge/<area>/`. Create the area if needed (also create its `overview.md`). Report what you saved after the fact — don't narrate the process.

**Doc maintenance.**
- Keep `docs/overview.md` and `docs/plan.md` current. When a chapter is complete, mark it done in `plan.md` and refresh the summary in `overview.md`.
- Keep cross-references consistent — if you rename/move/delete a file, update every referrer.
- Watch for knowledge files growing past **~500 lines with clear sub-sections** → propose a split.
- Watch for duplication across files → consolidate + cross-reference.

**ADRs.** When we make a non-obvious decision (picking a solver, choosing a language feature, bounding scope), write a short ADR in `docs/adr/NNNN-slug.md`.

**Spec maintenance.** When Vanja changes what the NSP app should do mid-build, update `specs/nsp-app/` first (and bump spec version), then the code. Never let code drift from a locked spec silently — either amend the spec or revert the code.

**`cpsat-kt` hygiene.** When a chapter uses a CP-SAT feature the DSL doesn't cover, extend `cpsat-kt` (add the API + test + short docstring) before the chapter. Commit `cpsat-kt:` changes separately from chapter code.

**Git.** Commit after every meaningful change. `<area>: <action>` format (`cp-sat: add chapter on IntVar and linear constraints`, `nurse-scheduling: document HC-1..HC-5`, `docs: update plan after chapter 2`). Push to `origin/main` immediately unless told to batch. One logical change = one commit. Never skip hooks, never force-push, never amend published commits.

**QMD.** The launchd daemon (`io.qmd.daemon`) runs automatically. The post-commit hook re-indexes. Prefer `mcp__qmd__query` over grep for content searches. Use grep for structural searches (imports, path patterns, filenames).

**Memory.** When Vanja corrects you, save a feedback memory with the *why*. When Vanja validates a non-obvious call, save that too. Project memory lives at the canonical location `~/.claude/projects/<slug>/memory/` (outside the repo) and is mirrored into `claude-memory/` in this repo via the post-commit hook (`tools/setup-memory-hook.sh`). Versioned in git, portable across machines, zero permission prompts. Two protected zones avoided: canonical sits outside the working tree, and the in-repo mirror sits outside the hardcoded `.claude/` prefix that triggers the "sensitive file" prompt regardless of `permissions.allow`, `bypassPermissions`, `--dangerously-skip-permissions`, or PreToolUse auto-approve hooks. Edit at the canonical path; `claude-memory/` is a one-way mirror (`rsync -a --delete` canonical → repo) and direct edits there are wiped on the next commit. The earlier symlink trick (`setup-memory-link.sh.deprecated`) put both paths in the same physical location inside the protected `.claude/` prefix and is abandoned.

## When to pause and ask Vanja

Only these:
1. **Destructive operations** — deleting files you didn't create, `git reset --hard`, dropping QMD collections
2. **Irreversible upstream changes** — force-push, closing PRs, tagging releases
3. **Scope pivots** — if Vanja's request implies changing the learning plan meaningfully, propose the change before executing
4. **External communication** — publishing anything, posting on social, creating public artifacts
5. **New top-level areas** under `docs/` or `apps/` — propose first

For everything else: act, then report.

## How to report

**Don't:** *"I'll add that to the CP-SAT notes and commit. Let me do that now."*
**Do:** *"Added to `docs/knowledge/cp-sat/variables.md`. Committed + pushed."*

**Don't:** multi-paragraph narration of what you thought.
**Do:** lead with what changed, end with what's next if anything. Skip process.

## Teaching cadence

- **Chapter = one concept, top-to-bottom.** Intuition → formal → tiny example → Python → Kotlin (via `cpsat-kt`) → MiniZinc (if it helps) → what's next.
- **Code lives next to the chapter.** Chapter 03 on `IntVar` gets `apps/py-cp-sat/ch03-intvars/` + `apps/kt-cp-sat/ch03-intvars/`. Same input/output contract.
- **Exercises at end of chapter.** 3–5 small ones, hidden solutions in `solutions/` subfolder.
- **Complexity ramps.** Only add a new concept when the previous one works end-to-end in both languages with the same output.

## Key principles

- **Mastery over coverage.** Better to deeply understand 5 concepts than skim 20.
- **Runnable everything.** If it's not runnable, it's incomplete. No pseudocode for real examples.
- **Dual-language parity.** Python and `cpsat-kt`-powered Kotlin stay lockstep — comparison is the whole point.
- **Idiomatic Kotlin via `cpsat-kt`.** Never raw `com.google.ortools.sat.*` in Kotlin code (except the one motivational chapter).
- **Spec-driven apps.** The NSP app is built from `specs/nsp-app/`, locked before impl. Divergence → amend spec, then code.
- **Latest modern defaults.** JDK 25 (LTS), Kotlin 2.1+, Gradle 9, Python 3.12+ via uv, Node 22+. Upgrade on schedule, don't linger on older versions.
- **MiniZinc as teaching tool.** Used during learning phases; not in the production app unless a concrete case proves value.
- **Public repo, personal-framing kept discreet.** Repo is public on GitHub. Technical content is fully public; personal memories under `claude-memory/` are intentionally tracked in git as part of the public artifact — write only what you'd be fine publishing.
- **No hallucinated APIs.** If uncertain about a CP-SAT, `cpsat-kt`, or MiniZinc API, grep the source or ask — never guess.

## Commit conventions

- `<area>: <action>`. Examples:
  - `docs: add plan.md`
  - `cp-theory: add chapter 1 — what is constraint programming`
  - `cp-sat: document NewIntVar and NewBoolVar with examples`
  - `cpsat-kt: add IntVar + BoolVar DSL with operator overloads`
  - `cpsat-kt: extend DSL with Automaton constraint`
  - `minizinc: add alldifferent example`
  - `nurse-scheduling: formalize hard constraints HC-1..HC-8`
  - `specs/nsp-app: lock v1.0`
  - `apps/web: scaffold Vite + React Router v7 project`
  - `setup: add QMD post-commit hook`
- Commit after every chapter/section. Push immediately.
- Separate `cpsat-kt:` commits from chapter-code commits when both change in one session.

## Don'ts

- Don't commit `.env` or credentials (there are none right now, but the rule stays)
- Don't create files outside the structure above without reason
- Don't write a chapter in only one language — dual-parity is mandatory
- Don't use raw `com.google.ortools.sat.*` in Kotlin — go through `cpsat-kt`
- Don't write app code that contradicts a locked spec — amend the spec first
- Don't add abstractions, frameworks, or configuration systems until there are 3+ concrete uses
- Don't invent CP-SAT, `cpsat-kt`, or MiniZinc APIs — verify first
