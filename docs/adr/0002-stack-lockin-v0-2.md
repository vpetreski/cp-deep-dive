# ADR 0002 — Tool-stack lock-in for plan v0.2

**Status:** accepted
**Date:** 2026-04-19
**Deciders:** maintainer, Claude

## Context

Plan v0.1 left multiple stack decisions open (JVM version, frontend framework,
Kotlin backend framework, Python package manager). Maintainer feedback during
the v0.1 → v0.2 iteration provided concrete locks for each. This ADR captures
the decision set so future contributors see *why* — not just *what*.

## Decision

Locked for plan v0.2; change only via a new ADR.

- **Languages/runtimes:** JDK 25 LTS, Kotlin 2.1+, Gradle 9.x (Kotlin DSL +
  version catalog), Python 3.12+ via `uv`, Node 22 LTS.
- **Kotlin backend:** Ktor 3.x (rejecting Spring Boot).
- **Frontend:** Vite 6 + React 19 + React Router v7 framework mode +
  TypeScript 5 + Tailwind 4 + shadcn/ui + TanStack Query 5 (rejecting
  Next.js 15).
- **Python backend:** FastAPI + Pydantic v2 + uvicorn.

## Alternatives considered
- **Spring Boot 3:** heavier, less Kotlin-idiomatic, more ceremony for a
  learning-project backend.
- **Next.js 15:** heavier, Vercel-leaning, SSR ceremony we don't need — a
  SPA/SSR Vite+RR7 stack is lighter and vendor-neutral.
- **JDK 21:** previous LTS but no longer current; we want the latest LTS to
  avoid churn when we inevitably hit JDK 25 features via Kotlin 2.1.
- **poetry / pipenv:** `uv` is faster, reproducible, and replaces virtualenv
  ceremony.

## Consequences
- **Positive:** modern defaults; lightweight footprint; consistent idiomatic
  choices; upgradable cleanly.
- **Negative:** bleeding-edge JDK 25 means some tooling (e.g. CI actions) may
  need version nudges; RR7 framework mode is newer and less documented than
  plain React + Vite.
- **Neutral:** adds ~6 lock decisions to the documented record, reducing future
  debate.

## References
- `docs/plan.md` §2 (stack table) and §3 (answered-questions table)
- ADR 0001 — `cpsat-kt` artifact decision
