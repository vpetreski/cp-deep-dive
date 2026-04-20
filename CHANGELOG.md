# Changelog

All notable changes to this project will be documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
for its published artifacts (currently only `cpsat-kt`).

## [Unreleased]

Changes on `main` that have not yet been tagged.

## [0.1.0] — 2026-04-20

First public release. The repository is a teaching deep-dive on Constraint
Programming with OR-Tools CP-SAT, with the Nurse Scheduling Problem (NSP) as
the running example, in both Python and Kotlin, plus a full end-to-end NSP
app (FastAPI + Ktor + Vite/React).

### Added

- `docs/plan.md` — 18-chapter learning roadmap (v0.2)
- `docs/overview.md` — always-loaded project summary
- `docs/knowledge/` — reference encyclopedia indexed by QMD:
  - `cp-theory/`, `cp-sat/`, `cpsat-kt/`, `minizinc/`, `nurse-scheduling/`, `ecosystem/`
- `docs/chapters/01-..-18-*.md` — per-chapter teaching notes
- `docs/adr/0001..0004-*.md` — architectural decision records
- `libs/cpsat-kt/` — v0.1.0 Kotlin DSL over OR-Tools CP-SAT, first-class
  publishable artifact; idiomatic API, coroutines-aware, Kotest-covered
- `specs/nsp-app/` — locked v1.0 spec for the NSP app (11 markdown files,
  domain model, FRs/NFRs, API contract, UI/UX, data model, acceptance
  criteria)
- `apps/py-cp-sat/` — chapter code for Python (ch02–ch13), runnable under
  `uv`, with ch-by-ch exercises and solutions
- `apps/kt-cp-sat/` — chapter code for Kotlin (ch02–ch13) using `cpsat-kt`,
  parity with Python
- `apps/py-cp-sat/nsp-core/` and `apps/kt-cp-sat/nsp-core/` — shared NSP
  solver libraries in each language (domain, loader, validator, model_v1,
  model_v2, solver)
- `apps/py-api/` — FastAPI backend for the NSP app (13 endpoints including
  SSE streaming of incumbents, Prometheus metrics, idempotency, cursor
  pagination, RFC 7807 errors). 30 tests.
- `apps/kt-api/` — Ktor 3 backend with the same contract, 17 tests, byte-for
  -byte identical `/openapi.yaml` served
- `apps/web/` — Vite + React 19 + React Router v7 frontend; instance upload,
  solve view with SSE, schedule / coverage / Gantt views, infeasibility
  explorer, dark mode, backend switch
- `apps/shared/` — shared OpenAPI 3.1 contract + JSON Schemas
- `apps/mzn/` — MiniZinc teaching models (n-queens, knapsack, send-more-money,
  toy-NSP)
- `apps/alt-solver/timefold/` — Chapter 18 Timefold port of the NSP
- `apps/alt-solver/choco/` — Chapter 18 Choco port of the NSP
- `benchmarks/runner/` — cross-solver benchmark harness with baseline results
  (`benchmarks/results/2026-04-baseline/`) for CP-SAT, Choco, Timefold on
  toy-01 and toy-02
- `.github/workflows/ci.yml` — Python, Kotlin, and Web CI jobs on push & PR
- Top-level project files: `README.md`, `CLAUDE.md`, `AGENTS.md`,
  `LICENSE` (Apache-2.0), `NOTICE`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`,
  `SECURITY.md`, `CITATION.cff`, `PROGRESS.md`

### Notes

- `nsp-core` (Python) currently emits a single terminal SSE event per solve
  because an OR-Tools 9.15 change removed `has_objective()` from the solver
  callback API; a small patch is planned to restore intermediate incumbents.
- Timefold baseline numbers on the toy instances use a 15 s budget and report
  `feasible` rather than `optimal` because the default phase config favours
  exploration over proof; tuning for parity is tracked as future work.

[Unreleased]: https://github.com/vpetreski/cp-deep-dive/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/vpetreski/cp-deep-dive/releases/tag/v0.1.0
