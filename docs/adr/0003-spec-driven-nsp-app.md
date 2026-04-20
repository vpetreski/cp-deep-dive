# ADR 0003 — Spec-driven development for the NSP app

**Status:** accepted
**Date:** 2026-04-19
**Deciders:** maintainer, Claude

## Context

The Phase 7 NSP app is the most complex artefact in the project — two backends,
a web UI, a shared OpenAPI contract, persistence, observability, deployment.
Building it ad-hoc would almost certainly produce something misaligned with the
learning goals (the app should *showcase* the solver cleanly, not wrestle with
accidental complexity). This project treats spec-driven development as a core
discipline and requires a documented source of truth before any code.

## Decision

Chapter 14 (Phase 6) writes and locks `specs/nsp-app/` in full **before** any
code is written in Phase 7. The spec covers overview, vision/goals, user
stories, domain model, functional + non-functional requirements, API contract
(with OpenAPI 3.1 YAML), UI/UX wireframes, data model, and acceptance criteria.
Lock is marked by the maintainer explicitly saying "locked v1.0" and tagging
the repo `spec-nsp-app-v1.0`. Subsequent changes require an explicit spec amendment
(version bump + CHANGELOG update in `specs/nsp-app/README.md`).

## Alternatives considered
- **Spec as we go (write sections in parallel with code):** rejected — defeats
  the purpose; divergence happens silently and nobody notices.
- **Spec after MVP (write a spec retroactively from the built app):** rejected
  — a post-hoc spec reflects what was built, not what should have been built.
- **No spec, just a clean README:** rejected — insufficient for a three-chapter
  implementation arc with two backends that must match contractually.

## Consequences
- **Positive:** shared source of truth for implementation; acceptance criteria
  drive tests directly; fewer rewrites; backend parity is verifiable.
- **Negative:** ~4h upfront time investment in Chapter 14 before any app code
  ships.
- **Neutral:** imposes discipline — if reality diverges from the spec, the
  process is "amend spec, then code," not "just change the code."

## References
- `specs/nsp-app/` — the spec folder skeleton
- `docs/plan.md` Chapter 14 — the chapter that produces the locked spec
- `CLAUDE.md` "Spec maintenance" autonomous-behaviour rule
