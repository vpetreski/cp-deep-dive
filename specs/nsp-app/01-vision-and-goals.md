# 01 — Vision and Goals

> **Status:** LOCKED v1.0
> **Last updated:** 2026-04-19

## Vision

To provide a small, trustworthy, transparent reference application that demonstrates end-to-end how a real-world combinatorial problem — nurse scheduling — is modelled, solved, and operationalised with modern constraint-programming tooling. The app should read as a worked example, not a product: clear enough that a constraint-programming newcomer can trace every line from the instance file to the rendered roster, rigorous enough that an operations-research practitioner can reproduce industry-scale solves against standard benchmarks.

## Who this is for

The app targets three reader-practitioners, ordered by expected frequency of use:

### Primary: the scheduler (practitioner persona)

A ward supervisor, charge nurse, or small-hospital operations manager who wants to try an NSP solver on their own data without installing anything. They think in rosters and nurses, not in boolean variables. They will upload a JSON file describing their ward, click "Solve", and judge the result by whether it would be acceptable on Monday morning.

### Secondary: the learner (student persona)

A developer, data scientist, or university student following the `cp-deep-dive` project or reading the source to understand CP-SAT in context. They will look at the code as much as the UI. They care about API contracts, schema validation, observability, and whether the Python and Kotlin backends really produce equivalent output.

### Tertiary: the researcher (CP persona)

An operations-research practitioner or CP researcher who wants a reference UI to visualise outputs from a solver they are prototyping, or who wants to benchmark CP-SAT on standard NSP instances with minimal overhead. They will mostly use the API directly and will treat the frontend as a sanity-check viewer.

## Primary use cases

1. **Solve a ward instance end-to-end.** The scheduler uploads a JSON instance, requests a solve with a 60-second budget, watches partial solutions stream in, inspects the final roster, and downloads it as CSV.
2. **Compare backends on a standard instance.** The learner loads a built-in toy instance, solves it on the Python backend, switches the backend toggle to Kotlin, re-solves, and confirms that both backends produce equivalent-quality output.
3. **Diagnose an infeasible instance.** The scheduler uploads an instance with impossible coverage (e.g. more night shifts required than the pool can legally staff), receives an infeasibility report, and sees which hard constraints could not be met.
4. **Stream partial solutions.** The researcher submits a medium instance with a 5-minute time limit and observes the objective curve improve in real time via the server-sent events stream.
5. **Validate an instance before submitting.** The learner uploads a malformed JSON file, receives a structured validation error pointing to the offending field, fixes it, and re-uploads.
6. **Benchmark CP-SAT on a standard NSP instance.** The researcher scripts `curl` against `POST /solve` for an NSPLib-sized instance, records the final `objective`, `gap`, and `solveTimeSeconds`, and compares against published benchmark results.
7. **Teach CP-SAT from real code.** The learner reads the Python backend's solver module alongside the Kotlin backend's `cpsat-kt` DSL usage to see the same CP-SAT constraints expressed in two idioms.

## Design principles

### Deterministic by default

For a given instance, solver parameters, and random seed, the Python and Kotlin backends must produce equivalent-quality schedules. Non-determinism from the solver's parallel search is acceptable only when it is explicitly opted into via a higher `numSearchWorkers` value. When `numSearchWorkers = 1` and `randomSeed` is fixed, each backend's output is reproducible across runs; cross-backend results may differ in assignment shape but not in objective value (HC and SC totals agree, within the parity tolerance defined in FR-24).

### Explainable

Every schedule is accompanied by enough structured metadata — objective value, best bound, solve time, status, violations list — that a reader can understand *why* the output is what it is. Infeasibility reports identify the failing hard constraint family (HC-1..HC-8) rather than returning an opaque "UNSAT". Soft-constraint violations carry human-readable messages and the numeric penalty contribution, so the UI can show "Alice preferred Sunday off, was scheduled for M on day 6 (penalty 5)" instead of an anonymous "objective 138".

### Dual-language parity

The two backends implement the same HTTP contract and produce equivalent output on the same instance. The app is valuable as a teaching artefact precisely because it shows that a non-trivial CP-SAT application can be expressed idiomatically in both Python and Kotlin. Divergence between backends is a bug, not a feature. Idiomatic Kotlin is achieved via the `cpsat-kt` DSL under [`libs/cpsat-kt/`](../../libs/cpsat-kt/); Kotlin code never touches `com.google.ortools.sat.*` directly.

### Spec-driven

The locked specification in this directory is the contract. Implementation details can change; spec shape does not, except through an explicit amendment. When implementation reality and spec intent disagree, the spec is updated first and the code follows — not the other way around. The amendment process is documented in [README.md](README.md); it requires an ADR for breaking changes.

### Small surface area

Every feature must pay rent. The app exposes a handful of endpoints, one schema, one set of constraints, and two pages of UI. Features are added when a concrete teaching need or user story demands them, never because they would be "nice to have". The out-of-scope list in [00-overview.md](00-overview.md) is a forcing function against scope creep.

### Modern defaults

The app uses current-generation tooling: JDK 25 LTS, Kotlin 2.1+, Python 3.12+ via `uv`, React 19, React Router v7 framework mode, Vite 6, Tailwind 4, OR-Tools 9.x. Upgrades happen on schedule; the stack does not linger on older versions to preserve compatibility with abandoned environments. Node 22+ is required for the frontend build.

### Observability first

Even a teaching app benefits from structured observability. Every backend emits JSON logs, Prometheus metrics, and retains the CP-SAT search log per job. See NFR-11 through NFR-13 for specifics.

### Open by default

The repo is currently private but the code, schemas, and spec are all written for public release. No identifying data ships in the repo. Toy instances use pseudonyms (`Alice`, `Bob`, `Carmen`). Deployments that expose the app publicly are expected to add authentication and rate limiting at the ingress layer.

## Non-goals (explicit)

The following are consciously excluded from v1.0 and would require a significant re-scoping effort to add. Each non-goal is listed both to set expectations with users and to prevent scope creep during implementation.

1. **Multi-tenancy.** The app runs as a single logical tenant. There is no concept of organisations, users who own subsets of instances, or data isolation. Everything uploaded is visible to everyone.
2. **Billing and licensing.** The app is Apache 2.0 licensed and has no billing logic, usage metering, or license enforcement.
3. **Custody chain of schedules.** There is no audit log of who produced, edited, or downloaded a schedule. The app tracks solve jobs for operational purposes only.
4. **Regulatory certification.** The app makes no HIPAA, GDPR processor, or SOC 2 claims. Users are responsible for de-identifying any data they upload.
5. **Schedule editing.** The app does not support manual modification of a produced schedule. It computes, it does not amend.
6. **Schedule comparison.** Diffing two schedules or comparing solver runs is deferred to a future version.
7. **Schedule export formats beyond CSV.** PDF and ICS exports are a common follow-up request but are out of scope for v1.0.
8. **Alternative solvers.** Only CP-SAT is supported. A MiniZinc or Choco backend, even if architecturally feasible, is out of scope.
9. **Streaming updates to the client during editing.** There is no live-collaboration model; once a schedule is rendered, it is a static artefact.
10. **Mid-horizon re-rostering.** Given a partial schedule and a disruption, the app does not compute a minimum-change repair. Full-horizon solves only.
11. **Custom constraint authoring.** v1.0 supports exactly the constraint families HC-1..HC-8 and SC-1..SC-5. Users cannot define additional custom constraints without modifying the source.
12. **INRC-I and INRC-II XML ingestion.** v1.0 only accepts the JSON format defined by the wire schema. Academic benchmarks must be converted offline.

## North-star scenario

A ward supervisor opens the app at `https://nsp.example.com`. The home page offers either "Upload an instance" or "Pick a built-in example". They drag their ward's JSON file onto the drop zone; the app validates it, saves it, and navigates to the instance detail page, which shows 12 nurses, a 28-day horizon, three shift types (Morning, Day, Night), and a coverage grid. They click "Solve", configure a 60-second time limit and accept the default objective weights, and choose "Python backend" from the dropdown. The solve progress page opens and begins streaming partial solutions: first a feasible schedule with an objective of 342, then 198, then 156, then 142, finally settling on 138 with a reported 0.4 % optimality gap. They click through to the schedule view, toggle between the roster-table and the Gantt timeline, confirm that no nurse is working seven days in a row and that the weekends are distributed evenly, and click "Download CSV". Five minutes in total, no installation, the output is one commit away from being imported into next month's posted schedule.

If something had gone wrong — a malformed upload, a constraint that made the instance infeasible, a solver that hit its time limit without finding a feasible solution — the path would still have terminated in a clear, actionable screen: a validation error pointing to the offending field; an infeasibility report naming the failing HC code; a status `unknown` with the search-log available for inspection. The app does not silently fail.

## Success metrics

The app is "successful enough to ship" when all of the following are simultaneously true:

1. All MUST-priority user stories in [02-user-stories.md](02-user-stories.md) have passing automated acceptance tests.
2. All hard constraints HC-1..HC-8 and soft constraints SC-1..SC-5 defined in [04-functional-requirements.md](04-functional-requirements.md) are implemented and tested.
3. Performance targets in [05-non-functional-requirements.md](05-non-functional-requirements.md) are met on reference hardware.
4. Both backends pass the same contract-test suite described in [09-acceptance-criteria.md](09-acceptance-criteria.md).
5. The frontend passes automated accessibility scans (axe-core, zero critical issues) on all pages.
6. `docker compose up` reproduces the full stack (Python backend, Kotlin backend, frontend) end-to-end from a clean checkout.
7. README documents the run procedure and lists the supported instance format.

Post-v1.0 success will be measured by: number of public forks that adapt the app to a concrete ward, number of issues opened that identify real teaching gaps in the code, and inclusion of the repo as reference material in at least one external CP tutorial.

## Constraints on the design

The v1.0 design has several self-imposed constraints that shape how features are implemented:

1. **No ambient state.** Requests carry everything they need. No server-side session state, no per-user preferences stored on the backend.
2. **JSON everywhere on the wire.** The API accepts and returns JSON (except for the CSV export, Prometheus metrics, search logs, and the OpenAPI document). No XML, no protobuf, no custom binary formats.
3. **Single-file SQLite per backend.** Persistence is intentionally boring. No clustering, no replication, no distributed state.
4. **No background job queues beyond the in-process worker pool.** Celery, RQ, Kafka, etc. are excluded. The bounded worker pool defined in NFR-05 is sufficient for v1.0.
5. **No frontend framework beyond React + React Router v7.** No Redux, no MobX, no custom state management library. TanStack Query handles server state; React local state handles everything else.
6. **Shadcn/ui primitives, not wholesale component libraries.** Each component installed is a conscious decision. No Material UI, no Chakra, no Ant Design.

## Future directions (not committed, not in v1.0)

The following ideas are candidates for future versions but carry no commitment. They appear here to contextualise the v1.0 non-goals list:

- **Manual schedule editing** with live re-validation against HC-1..HC-8.
- **Schedule comparison** between two solver runs, with diff visualisation.
- **Additional export formats** (PDF, ICS calendar feed, XLSX).
- **INRC-I and INRC-II XML parsers** so that academic benchmark instances can be loaded directly.
- **Postgres backend** for multi-tenant deployments with a shared store across backends.
- **Re-rostering workflows** for mid-horizon disruptions (sick calls, last-minute changes).
- **ML-augmented warm starts** using prior schedules as CP-SAT hints.
- **MiniZinc backend** as a teaching comparison (declarative CP vs CP-SAT).
- **Internationalisation** with translations for at least one additional language.
- **Hospital information-system integration** with a thin adapter that reads from FHIR or similar.

Each of these would require a scoping document and an amendment to this spec if adopted.
