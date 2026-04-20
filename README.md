# cp-deep-dive

A structured, long-form deep dive into **Constraint Programming / Constraint Optimization**, using the **Nurse Scheduling Problem (NSP)** as the running use case.

Tools studied:
- **Google OR-Tools CP-SAT** (Python + Kotlin) — the primary solver
- **MiniZinc** — solver-agnostic declarative modeling
- Supporting ecosystem (Choco, Timefold, Gecode, Z3) for context

Deliverables:
- Theory notes (`docs/knowledge/`) — searchable via QMD
- Runnable examples in Python *and* Kotlin for every concept
- Progressive chapters, ELI5 → advanced
- An end-to-end nurse scheduling application with visualization

This is a learning project. The goal is mastery, not coverage.

## Structure

See [CLAUDE.md](CLAUDE.md) for the full routing map, autonomous-behavior rules, and teaching cadence.

Entry points:
- `docs/overview.md` — the field, the tools, where we are
- `docs/plan.md` — the living learning plan (what we're doing, what's next)
- `docs/knowledge/` — deep notes (indexed by QMD)

## Setup per machine (one-time, after `git clone`)

```bash
# 1. Symlink Claude Code project memory into this repo (so memory persists via git)
./tools/setup-memory-link.sh

# 2. Install git post-commit hook for QMD auto-reindex
./tools/setup-qmd-hook.sh

# 3. Register the docs folder with QMD (if not already)
qmd collection add docs --name cp-deep-dive-docs
qmd update && qmd embed
```

Prerequisites: [QMD](https://github.com/tobi/qmd) v2+ installed and the `io.qmd.daemon` launchd agent running (it's a local HTTP MCP server on port 8181).

## Privacy

Private repo on GitHub. Research notes may include paraphrased content from papers/articles — cite sources, don't paste entire copyrighted works. Code is fine to extract and share later.
