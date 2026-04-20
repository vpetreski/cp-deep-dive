# ADR 0004 — Dual-language parity (Python + Kotlin in every chapter)

**Status:** accepted
**Date:** 2026-04-19
**Deciders:** maintainer, Claude

## Context

The project's stated goal is mastery of CP-SAT across two languages. The
*comparison* — same problem, same solver, two language-level expressions — is
a core learning mechanism. Asymmetric coverage (Python-first, Kotlin as
afterthought) would leak the abstraction: a learner would pick up Python
CP-SAT idioms fluently and Kotlin CP-SAT idioms shallowly.

## Decision

Every chapter, every example, and every doc ships in **both** Python and Kotlin
from day one. Kotlin code always goes through `cpsat-kt` (never raw
`com.google.ortools.sat.*`), with the single intentional exception of Chapter 2
where we show raw Java-in-Kotlin once to motivate building the wrapper.

## Alternatives considered
- **Python-first then port later:** faster per-chapter but loses comparative
  value; the port often happens weeks late and by then the Python version has
  drifted.
- **Kotlin-only using JVM tooling:** rejected — Python's CP-SAT bindings are
  first-party and many references/papers use Python; skipping it would leave
  the learner illiterate in the larger community.
- **Parity on a subset (e.g. chapters 1–6 only):** rejected — the app in
  Phase 7 *requires* both languages, so parity must hold through the whole
  ladder.

## Consequences
- **Positive:** deep comparative understanding; both backends in Phase 7 are
  "just another chapter"; `cpsat-kt` gets thoroughly exercised.
- **Negative:** ~30% extra time per chapter; coordinating the two
  implementations adds review overhead.
- **Neutral:** enforces a useful discipline — "if it's hard to express in one
  language, that's interesting; let's understand why."

## References
- `CLAUDE.md` "Dual-language parity is mandatory" rule
- ADR 0001 — `cpsat-kt` first-class artifact (enables idiomatic Kotlin parity)
- `docs/plan.md` §1 teaching principles
