# 00 — Overview

> **Status:** LOCKED v1.0
> **Last updated:** 2026-04-19

## What this app is

The Nurse Scheduling App ("the app") is an open, self-contained web application that accepts a Nurse Scheduling Problem (NSP) instance as JSON, solves it using Google OR-Tools CP-SAT, and returns a roster that respects a fixed set of hard and soft constraints. It ships with two backends — a Python backend (FastAPI) and a Kotlin backend (Ktor 3) — that implement an identical HTTP contract, plus a single React 19 + React Router v7 frontend that can switch between them. The app exists primarily as a teaching artefact for the `cp-deep-dive` learning project: its public surface is small, its internals are deliberately legible, and every feature traces back to a constraint-programming concept the project teaches.

The running example through the learning project is a realistic ward-scheduling problem: a handful of nurses, a short horizon of days, two or three shift types, coverage requirements, forbidden shift transitions, contract hours, and soft preferences. The app is designed to scale cleanly from that toy scale up through standard benchmark sizes (NSPLib small, INRC-II `n030w4`) while staying easy to read and extend.

The NSP is one of the most thoroughly studied problems in Operations Research. See [`docs/knowledge/nurse-scheduling/overview.md`](../../docs/knowledge/nurse-scheduling/overview.md) for background. The Wikipedia entry (<https://en.wikipedia.org/wiki/Nurse_scheduling_problem>) is a good starting point for readers new to the domain.

## At a glance

| Aspect | Summary |
|---|---|
| Problem class | Scheduling / rostering, NP-hard combinatorial optimisation |
| Solver | Google OR-Tools CP-SAT — <https://developers.google.com/optimization/> |
| Input format | JSON conforming to `nsp-instance.schema.json` |
| Output format | JSON `Schedule` + on-demand CSV export |
| Concurrency | Asynchronous solves, Server-Sent Events streaming |
| Deployment | Single-node, Docker Compose, three services (py-api, kt-api, web) |
| Licence | Apache 2.0 |

## In-scope

1. Upload, validate, list, fetch, and delete NSP instances in a fixed JSON format.
2. Submit instances for solving with configurable time limits, worker count, and objective weights.
3. Stream incremental solutions as the solver improves the objective.
4. Render a schedule as a roster table and as a Gantt timeline.
5. Export a schedule as CSV.
6. Report hard-constraint infeasibility with a structured explanation of which requirements could not be met.
7. Support all eight hard constraints HC-1..HC-8 and all five soft constraints SC-1..SC-5 defined in [04-functional-requirements.md](04-functional-requirements.md).
8. Offer a runtime toggle between the Python backend and the Kotlin backend; results must be equivalent-quality on the same instance.
9. Expose structured health and version endpoints for operational visibility.
10. Persist instances and solve jobs to local SQLite storage on either backend.

## Out-of-scope

1. Multi-tenancy, user accounts, or any authentication beyond an optional ingress-level API key.
2. Authorization models (roles, permissions, per-row access control).
3. Billing, subscriptions, or any monetization feature.
4. Audit trails or custody-chain guarantees on schedule edits.
5. Regulatory certification (HIPAA, GDPR processor status, SOC 2, etc.).
6. Real-time multi-user collaboration or optimistic-concurrency editing.
7. Native mobile applications or desktop packaging.
8. Internationalisation beyond English for v1.0; localisation is planned for a future version.
9. Integration with hospital information systems, HR software, or payroll.
10. Machine-learning-based demand forecasting or preference inference.
11. Re-rostering workflows (mid-horizon disruption handling); v1.0 solves a complete horizon from scratch.
12. Schedule comparison or diffing between solver runs.
13. Manual edit-and-revalidate flows for a produced schedule.
14. Exports beyond CSV (PDF, ICS, XLSX are deferred).
15. Solver-backend choices beyond CP-SAT.

## Glossary

| Term | Definition |
|---|---|
| **nurse** | An individual who can be scheduled. Carries an id, a display name, a set of skill tags, and a weekly contract in hours. |
| **shift** | A named time-of-day block within a single day (e.g. "Morning 07:00-15:00"). Instances define a fixed set of shift types. |
| **horizon** | The total number of calendar days that a single instance covers, 0-indexed from day 0 to day `horizonDays - 1`. |
| **coverage requirement** | A rule of the form "on day `d`, shift `s` must have between `min` and `max` nurses working". Defined once per (day, shift) cell. |
| **hard constraint** | A rule that must hold in any schedule the app returns; violating it means the instance is infeasible. Numbered HC-1..HC-8. |
| **soft constraint** | A rule the solver tries to satisfy but can violate at a penalty. Penalties are summed into the objective. Numbered SC-1..SC-5. |
| **instance** | The full problem input: nurses, shifts, horizon, coverage requirements, preferences, forbidden transitions, contract parameters. Validated against [`nsp-instance.schema.json`](../../apps/shared/schemas/nsp-instance.schema.json). |
| **schedule** | The output of a solve: a list of `(nurseId, day, shiftId)` assignments. `shiftId = null` indicates a day off. |
| **assignment** | A single cell in a schedule — one nurse, one day, one shift or off. |
| **objective** | The scalar value the solver minimises: the weighted sum of soft-constraint penalties. Lower is better. |

## Success criteria

The app is successful when all of the following hold on reference hardware (modern laptop, 8 cores, 16 GB RAM):

| Criterion | Target |
|---|---|
| Solve `toy-01.json` (3 nurses, 7 days, 2 shifts) to optimality | < 1 second |
| Solve `toy-02.json` (5 nurses, 14 days, 3 shifts) to optimality | < 2 seconds |
| Solve a medium instance (30 nurses, 28 days, 3 shifts) to first feasible | < 10 seconds |
| Solve a large instance (100 nurses, 28 days, 3 shifts) to a solution within 1 % optimality gap | < 60 seconds (with `numSearchWorkers = 8`) |
| API p95 latency on non-solve endpoints | < 200 ms |
| Backend parity: same instance on Python and Kotlin backends | Objective within ±5 % or identical coverage |
| Accessibility | Passes axe-core automated scan with zero critical issues on all pages |
| Dual-backend integration tests | Both backends pass the same contract test suite |

Success criteria that cannot be measured by an automated test are operationalised in [09-acceptance-criteria.md](09-acceptance-criteria.md) as manual walk-throughs with scripted steps.

### What success does not mean

The success criteria above deliberately do not include: integration with a hospital information system, support for arbitrary custom constraints, optimality proofs within any fixed budget for all benchmark sizes, or bit-for-bit schedule identity between backends. These are interesting research questions but fall outside the v1.0 teaching scope.

## Stack summary

| Layer | Choice | Version pin |
|---|---|---|
| Solver | Google OR-Tools CP-SAT | 9.x (latest stable at lock time) |
| Python backend | FastAPI + Uvicorn + Pydantic v2 | Python 3.12+ |
| Kotlin backend | Ktor 3 + Kotlinx Serialization + `cpsat-kt` DSL | JDK 25 LTS, Kotlin 2.1+ |
| Frontend | Vite + React 19 + React Router v7 (framework mode) + TanStack Query 5 + Tailwind 4 + shadcn/ui | Node 22+ |
| Persistence | SQLite via SQLModel (Python) / Exposed (Kotlin) | SQLite 3.45+ |
| Schema format | JSON Schema 2020-12 | — |
| API description | OpenAPI 3.1 | [`/apps/shared/openapi.yaml`](../../apps/shared/openapi.yaml) |
| Containerisation | Docker Compose for local development | Docker 24+ |
| CI | GitHub Actions; per-backend test matrix | — |

The rationale for these choices is recorded in [ADR 0002](../../docs/adr/0002-app-stack.md) (to be authored alongside `apps/` scaffolding; see `docs/plan.md` Chapter 15). Key trade-offs in the choice:

- **Why two backends.** The project's pedagogical goal is to show CP-SAT idiomatically in both Python and Kotlin. The `cpsat-kt` DSL (under [`libs/cpsat-kt/`](../../libs/cpsat-kt/)) wraps OR-Tools' Java bindings so the Kotlin backend reads naturally without dropping into `com.google.ortools.sat.*` directly.
- **Why SQLite, not Postgres.** For teaching-scale data, SQLite is zero-install, transactional, and fits the single-node deployment model. The schema is documented such that a Postgres migration is mechanical.
- **Why SSE, not WebSockets.** Partial solutions are strictly one-directional (server to client); SSE is simpler to reason about, works through more HTTP intermediaries, and has first-class browser support without a library.

## Relationship to the JSON schemas

The canonical data shapes live in two places:

- [`/data/nsp/schema.json`](../../data/nsp/schema.json) — the toy-style instance format used by chapter code in `apps/py-cp-sat/` and `apps/kt-cp-sat/`. This is the "teaching" schema.
- [`/apps/shared/schemas/`](../../apps/shared/schemas/) — the app's wire schemas (`nsp-instance`, `schedule`, `solve-request`, `solve-response`). These are the "production" schemas.

The app accepts either schema on upload (with a small adapter for the toy form), but stores and returns the wire schema canonically. See [08-data-model.md](08-data-model.md) for the adapter contract.

## Relationship to the learning project

This app is the capstone of the `cp-deep-dive` curriculum. Chapters 1–13 teach constraint programming using CP-SAT in Python and Kotlin (via the `cpsat-kt` DSL); Chapter 14 locks this spec; Chapters 15–18 build the app incrementally against this spec without modifying it unless an amendment is issued. See [`/docs/plan.md`](../../docs/plan.md) for the full roadmap and [`/docs/knowledge/nurse-scheduling/overview.md`](../../docs/knowledge/nurse-scheduling/overview.md) for domain background.

## How to read the rest of this spec

A reader new to the spec should read the files in order: `00` through `09`. The ordering is intentional:

1. [`00-overview.md`](00-overview.md) — this file. Orient yourself.
2. [`01-vision-and-goals.md`](01-vision-and-goals.md) — why the app exists.
3. [`02-user-stories.md`](02-user-stories.md) — what users will do.
4. [`03-domain-model.md`](03-domain-model.md) — the vocabulary.
5. [`04-functional-requirements.md`](04-functional-requirements.md) — what must be built (and the constraint definitions HC-1..HC-8, SC-1..SC-5).
6. [`05-non-functional-requirements.md`](05-non-functional-requirements.md) — how well it must be built.
7. [`06-api-contract.md`](06-api-contract.md) — the HTTP surface.
8. [`07-ui-ux.md`](07-ui-ux.md) — the frontend design.
9. [`08-data-model.md`](08-data-model.md) — persistence.
10. [`09-acceptance-criteria.md`](09-acceptance-criteria.md) — how we'll know it's done.

Readers looking for a specific answer should use the reading table in [README.md](README.md) — each file's scope is disjoint and cross-referenced consistently.

## Amendments and contributions

This spec lives in a public-ish learning repository. Issues and pull requests against the spec are welcome. Amendments to the locked spec follow the process in [README.md](README.md): bump the version, add an entry to the amendments log, and (for breaking changes) add an ADR under [`/docs/adr/`](../../docs/adr/).
