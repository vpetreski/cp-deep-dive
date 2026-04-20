# ADR 0001 — `cpsat-kt` as a first-class artifact

**Status:** accepted
**Date:** 2026-04-19
**Deciders:** maintainer, Claude

## Context

OR-Tools ships first-party Python bindings but no Kotlin bindings — the JVM
surface is raw Java. Calling that Java directly from Kotlin is ergonomically
poor: no operator overloading, verbose constraint construction, nullable
platform types everywhere, exception-based result handling, and no sealed
result types. The repo's stated goal is dual-language parity with **idiomatic**
Kotlin. The wrapper is also meant to be publishable independently so the
work has value beyond the learning project.

## Decision

Build `libs/cpsat-kt/` as a first-class Kotlin DSL library under
`io.vanja:cpsat-kt`, used by **every** Kotlin example, chapter, and app in the
repo. Treat it as a real artifact: its own Gradle build, version, tests,
README, changelog, Dokka docs, publishable to Maven Central.

## Alternatives considered
- **Use the raw Java API from Kotlin:** rejected — the ergonomics are bad,
  and the repo's idiomatic-Kotlin goal was established during plan iteration.
- **Adopt a third-party Kotlin wrapper:** none maintained or feature-complete
  as of 2026-04.
- **Use Timefold or Choco instead of CP-SAT:** rejected — the learning goal is
  CP-SAT specifically; switching solver to dodge the bindings problem fails the
  goal.

## Consequences
- **Positive:** idiomatic Kotlin in every example; one canonical place to add
  helpers; publishable artifact the community can use; forces rigorous API
  design discipline.
- **Negative:** maintenance burden — must evolve with OR-Tools releases; risk
  of lagging behind upstream changes; extra build complexity via composite
  Gradle build.
- **Neutral:** Chapter 2 intentionally still shows raw Java-in-Kotlin once to
  motivate the wrapper; Chapter 3 then builds v0.1.

## References
- `docs/knowledge/cpsat-kt/overview.md` — design doc / API surface / package layout
- `docs/knowledge/cp-sat/python-vs-kotlin.md` — the ergonomic-gap argument
- `docs/plan.md` Chapter 3 — the build-it moment
