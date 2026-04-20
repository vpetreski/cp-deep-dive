# Your Learning Progress

This is a **personal progress tracker** for working through the `cp-deep-dive`
curriculum. Fork the repo, open this file in your own copy, and check items
off as you go. The file is small on purpose — it's meant to live alongside
your commits and grow with your notes.

> **How to use it.** Fork the repo, clone your fork, open `PROGRESS.md` in
> your editor or directly on GitHub, and tick boxes / fill in dates and notes
> as you go. Commit your progress on your own branch (e.g. `learn/<your-name>`)
> so you keep a history. If you are working with Claude Code (or any LLM
> agent) attached to the repo, ask it to "update PROGRESS.md with what I just
> finished" — the agent can edit this file directly.

## Learner profile

_Fill in once when you start._

- Name / handle:
- Started on (YYYY-MM-DD):
- Background (prior CP / OR / solver experience):
- Primary language you already know well (Python / Kotlin / both / neither):
- Goal for this curriculum (career / research / hobby / specific project):

## Time budget at a glance

Read `README.md` for the full breakdown. Rough targets below; actual time
varies by prior exposure.

| Phase | Chapters | Target hours |
|---|---|---|
| 0 — setup | 01 | 2 |
| 1 — intuition & vocabulary | 02, 03 | 7 |
| 2 — core modelling | 04, 05, 06 | 9 |
| 3 — bridges | 07, 08 | 4 |
| 4 — scheduling patterns | 09, 10 | 6 |
| 5 — NSP v1→v2→v3 | 11, 12, 13 | 14 |
| 6 — the `cpsat-kt` library | 14 | 4 |
| 7 — production app | 15, 16, 17 | 15 |
| 8 — ecosystem | 18 | 5 |
| — exercises (across phases) | | +20-30 |
| — reading (papers, docs) | | +5-10 |
| **Total** | | **~100-120 h** |

## Chapter tracker

For each chapter:
- tick **Read**  when you finished the chapter's `docs/chapters/NN-*.md`
- tick **Python** when you ran the chapter's `apps/py-cp-sat/chNN-*/` code
- tick **Kotlin** when you ran the chapter's `apps/kt-cp-sat/chNN-*/` code
- tick **Exercises** once you have a working answer for every exercise
- fill **Hours** with the number of hours you spent on this chapter
- use **Notes** for questions, "aha" moments, gotchas you want to remember

| # | Chapter | Read | Python | Kotlin | Exercises | Hours | Notes |
|---|---|---|---|---|---|---|---|
| 01 | Environment & tooling | [ ] | [ ] | [ ] | n/a |  |  |
| 02 | Hello CP-SAT | [ ] | [ ] | [ ] | [ ] |  |  |
| 03 | Variables, domains, constraints | [ ] | [ ] | [ ] | [ ] |  |  |
| 04 | Classic puzzles | [ ] | [ ] | [ ] | [ ] |  |  |
| 05 | Optimization (knapsack, bin-packing) | [ ] | [ ] | [ ] | [ ] |  |  |
| 06 | Global constraints | [ ] | [ ] | [ ] | [ ] |  |  |
| 07 | Introducing MiniZinc | [ ] | [ ] | [ ] | [ ] |  |  |
| 08 | MiniZinc → CP-SAT port | [ ] | [ ] | [ ] | [ ] |  |  |
| 09 | Job-shop scheduling | [ ] | [ ] | [ ] | [ ] |  |  |
| 10 | Shift scheduling primer | [ ] | [ ] | [ ] | [ ] |  |  |
| 11 | NSP v1 — hard constraints | [ ] | [ ] | [ ] | [ ] |  |  |
| 12 | NSP v2 — soft constraints | [ ] | [ ] | [ ] | [ ] |  |  |
| 13 | NSP v3 — scaling & benchmarks | [ ] | [ ] | [ ] | [ ] |  |  |
| 14 | `cpsat-kt` internals | [ ] | n/a | [ ] | [ ] |  |  |
| 15 | FastAPI + Ktor backends | [ ] | [ ] | [ ] | [ ] |  |  |
| 16 | Web UI (Vite + React) | [ ] | n/a | n/a | [ ] |  |  |
| 17 | Containerize & deploy | [ ] | [ ] | [ ] | [ ] |  |  |
| 18 | Ecosystem (Timefold, Choco) | [ ] | [ ] | [ ] | [ ] |  |  |

## Exercise log

Keep a short note for each exercise you finish. Example format:

```
2026-05-02  ch04 ex-4.1 (n-queens symmetry breaking)
  - Tried lex_leq on rows; cut symmetric solutions by 8×
  - Question: why does lex_leq propagate weaker than lex_less?
```

Your entries below:

```




```

## Reflections

At the end of each phase, write a short paragraph:
- What clicked?
- What is still fuzzy?
- What would you do differently on a re-read?

### After Phase 1 (intuition)


### After Phase 2 (core modelling)


### After Phase 3 (bridges)


### After Phase 4 (scheduling patterns)


### After Phase 5 (NSP)


### After Phase 6 (`cpsat-kt`)


### After Phase 7 (app)


### After Phase 8 (ecosystem)


## Finished

- Finished on (YYYY-MM-DD):
- Total hours (sum of the per-chapter column + exercises + reading):
- One-sentence summary of what you can now build that you couldn't before:
