# cp-deep-dive — Vanja Petreski's Constraint Programming / Optimization deep dive

This repo is a structured, long-form learning project where Vanja becomes a practitioner of Constraint Programming / Constraint Optimization. The running use case is the **Nurse Scheduling Problem (NSP)** solved with **Google OR-Tools CP-SAT** in both **Python** and **Kotlin**, with **MiniZinc** as a solver-agnostic modeling companion, and an eventual end-to-end app + visualization.

This file is always loaded. Read it first. Deep knowledge lives under `docs/knowledge/` and is searchable via QMD — don't read large files whole, query them.

## Structure

```
cp-deep-dive/
├── CLAUDE.md                  <- you are here — rules + routing + autonomous behaviors
├── README.md                  <- public-facing purpose + setup
├── AGENTS.md                  <- pointer to CLAUDE.md
├── .claude/
│   ├── commands/              <- project slash commands (empty for now)
│   ├── memory/                <- versioned project memory (symlinked via tools/setup-memory-link.sh)
│   └── settings.json          <- shared project settings
├── .mcp.json                  <- QMD HTTP MCP (local daemon on :8181)
├── .gitignore
├── docs/
│   ├── overview.md            <- always-loaded summary of the field, the tools, and the plan
│   ├── plan.md                <- living learning plan — source of truth for "what next"
│   └── knowledge/             <- QMD-indexed deep knowledge (grow over time)
│       ├── cp-theory/         <- Constraint Programming theory + concepts
│       ├── cp-sat/            <- Google OR-Tools CP-SAT deep dive
│       ├── minizinc/          <- MiniZinc modeling language + solver ecosystem
│       ├── nurse-scheduling/  <- NSP literature, formal definition, variants
│       ├── ecosystem/         <- Other tools (Choco, Timefold, Gecode, Z3, etc.)
│       └── decisions/         <- ADRs — why we chose X over Y
├── tools/
│   ├── setup-memory-link.sh   <- one-time per machine: symlink project memory into repo
│   └── setup-qmd-hook.sh      <- one-time per machine: install post-commit QMD reindex hook
└── (future) apps/ code/ experiments/ — added as we build, not now
```

## How we work together (teach-me mode)

Vanja is **new to Constraint Programming**. Explanations start ELI5, then scale up. Every concept follows the same ladder:

1. **Intuition first** — one plain-English paragraph, no jargon.
2. **Minimal formal definition** — one diagram or equation, labeled.
3. **Tiny worked example** — solvable by hand in 5 lines.
4. **Python implementation** — CP-SAT, runnable.
5. **Kotlin implementation** — CP-SAT, same problem, side-by-side contrast.
6. **MiniZinc model** (where it adds clarity) — the declarative spec version.
7. **What this unlocks** — one sentence linking to the next concept.

Rules:
- Never assume knowledge Vanja hasn't been taught yet in this repo.
- When introducing a new term, italicize it the first time and give a one-line gloss.
- Prefer "you" over "we" in explanations (active voice, direct).
- When two approaches compete, show both briefly and recommend one *with reasoning* — don't hide trade-offs.
- Code examples are runnable end-to-end. No pseudocode when real code fits.
- Every Python example has a Kotlin twin in the same chapter unless explicitly scoped out.

## Routing — where to go for what

| When Vanja says... | Work in... | Read first... |
|---|---|---|
| "What are we doing?" / "status" / "where are we" | `docs/overview.md` + `docs/plan.md` | `docs/overview.md` |
| "What is CP?" / "explain constraint programming" / general theory | `docs/knowledge/cp-theory/` | `docs/knowledge/cp-theory/overview.md` (when created) |
| Anything about CP-SAT internals, API, modeling | `docs/knowledge/cp-sat/` | `docs/knowledge/cp-sat/overview.md` |
| MiniZinc / FlatZinc / solver-agnostic modeling | `docs/knowledge/minizinc/` | `docs/knowledge/minizinc/overview.md` |
| Nurse Scheduling Problem / NSP / shift scheduling | `docs/knowledge/nurse-scheduling/` | `docs/knowledge/nurse-scheduling/overview.md` |
| "Why did we pick X?" / architectural decisions | `docs/knowledge/decisions/` | scan ADR filenames |
| "What's the plan?" / "next chapter" / "what's after this" | `docs/plan.md` | `docs/plan.md` |
| Write code for chapter N | `apps/` or `experiments/` (create if missing) | chapter's markdown in `docs/` |

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

**ADRs.** When we make a non-obvious decision (picking a solver, choosing a language feature, bounding scope), write a short ADR in `docs/knowledge/decisions/NNNN-slug.md`.

**Git.** Commit after every meaningful change. `<area>: <action>` format (`cp-sat: add chapter on IntVar and linear constraints`, `nurse-scheduling: document HC-1..HC-5`, `docs: update plan after chapter 2`). Push to `origin/main` immediately unless told to batch. One logical change = one commit. Never skip hooks, never force-push, never amend published commits.

**QMD.** The launchd daemon (`io.qmd.daemon`) runs automatically. The post-commit hook re-indexes. Prefer `mcp__qmd__query` over grep for content searches. Use grep for structural searches (imports, path patterns, filenames).

**Memory.** When Vanja corrects you, save a feedback memory with the *why*. When Vanja validates a non-obvious call, save that too. See the global memory guidance — project memory lives in `.claude/memory/`, symlinked into place.

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

- **Chapter = one concept, top-to-bottom.** Intuition → formal → tiny example → Python → Kotlin → MiniZinc (if it helps) → what's next.
- **Code lives next to the chapter.** Chapter 03 on `IntVar` gets `apps/python/ch03-intvars/` + `apps/kotlin/ch03-intvars/`. Same input/output contract.
- **Exercises at end of chapter.** 3-5 small ones with hidden solutions.
- **Complexity ramps.** Only add a new concept when the previous one is working end-to-end in both languages.

## Key principles

- **Mastery over coverage.** Better to deeply understand 5 concepts than skim 20.
- **Runnable everything.** If it's not runnable, it's incomplete. No pseudocode for real examples.
- **Two languages, same problem.** Python and Kotlin stay lockstep — that's the whole point of the comparison.
- **MiniZinc as spec.** Use MiniZinc when the declarative form clarifies the math; skip when it's just ceremony.
- **Public-ish repo, private by default.** Repo is private on GitHub. Don't leak personal details. Fine to share code snippets publicly later with permission.
- **No hallucinated APIs.** If uncertain about a CP-SAT or MiniZinc API, grep the OR-Tools / MiniZinc source or ask — never guess.

## Commit conventions

- `<area>: <action>`. Examples:
  - `docs: add plan.md`
  - `cp-theory: add chapter 1 — what is constraint programming`
  - `cp-sat: document NewIntVar and NewBoolVar with examples`
  - `minizinc: add alldifferent example`
  - `nurse-scheduling: formalize hard constraints HC-1..HC-8`
  - `setup: add QMD post-commit hook`
- Commit after every chapter/section. Push immediately.

## Don'ts

- Don't commit `.env` or credentials (there are none right now, but the rule stays)
- Don't create files outside the structure above without reason
- Don't write a chapter in only one language when both are in scope
- Don't add abstractions, frameworks, or configuration systems until there are 3+ concrete uses
- Don't invent CP-SAT or MiniZinc APIs — verify first
