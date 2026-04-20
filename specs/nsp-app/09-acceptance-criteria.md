# 09 — Acceptance Criteria

> **Status:** LOCKED v1.0
> **Last updated:** 2026-04-19

## Purpose

The authoritative "is it done?" checklist for v1.0. Every functional requirement in [04-functional-requirements.md](04-functional-requirements.md) and every non-functional requirement in [05-non-functional-requirements.md](05-non-functional-requirements.md) maps to at least one acceptance criterion here. Each AC is expressed in Given-When-Then form so it is immediately testable.

## AC conventions

- **Given** — precondition that must hold before the scenario runs.
- **When** — the triggering action.
- **Then** — the observable result.
- Each AC lists the FR/NFR it verifies and the test category: `unit`, `integration`, `e2e`, or `manual`.

---

## AC-01: Reject schema-invalid instance upload

**Given** the backend is running and has no instances with id `bad-01`.
**When** the user submits `POST /instances` with a JSON body whose `nurses` field is an integer.
**Then** the backend returns HTTP 400 with an `Error` body where `code = "request.malformed"` and `details.pointer` points to `/nurses`; no instance is persisted.
**Verifies:** FR-01.
**Tested via:** integration.

## AC-02: Accept a valid toy instance upload

**Given** the backend is running.
**When** the user submits `POST /instances` with the contents of `data/nsp/toy-02.json`.
**Then** the backend returns HTTP 201 with a `Location` header `/instances/{id}` and a body matching the canonical `NspInstance` shape.
**Verifies:** FR-01, FR-02.
**Tested via:** integration.

## AC-03: Persisted instance is retrievable

**Given** an instance was created via AC-02 and has id `X`.
**When** the user requests `GET /instances/X`.
**Then** the backend returns HTTP 200 with the full instance body.
**Verifies:** FR-02, FR-04.
**Tested via:** integration.

## AC-04: Instances list paginates correctly

**Given** 25 instances exist in storage.
**When** the user requests `GET /instances?limit=10`.
**Then** the response contains 10 items sorted by `created_at` descending, plus a `nextCursor`; issuing a second request with that cursor returns the next 10 items; a third returns the remaining 5 with no `nextCursor`.
**Verifies:** FR-03.
**Tested via:** integration.

## AC-05: Unknown instance id returns 404

**Given** the backend is running.
**When** the user requests `GET /instances/does-not-exist`.
**Then** the backend returns HTTP 404 with `code = "instance.notFound"`.
**Verifies:** FR-04.
**Tested via:** integration.

## AC-06: Bundled examples are available at startup

**Given** a clean database and the backend has just started.
**When** the user requests `GET /instances`.
**Then** the response includes entries with ids `toy-01` and `toy-02` and `source = "toy"`.
**Verifies:** FR-05.
**Tested via:** integration.

## AC-07: Delete removes instance and cascades

**Given** instance `X` exists with solve job `J`.
**When** the user issues `DELETE /instances/X`.
**Then** the backend returns HTTP 204; subsequent `GET /instances/X` and `GET /solution/J` return 404.
**Verifies:** FR-06.
**Tested via:** integration.

## AC-08: Instance detail page shows summary

**Given** instance `toy-02` exists.
**When** the user navigates to `/instances/toy-02` in the frontend.
**Then** the page displays id, name, source, horizonDays (14), nurse count (5), shift count (3), and coverage-slot total.
**Verifies:** FR-07.
**Tested via:** e2e.

## AC-09: Solve request creates a job

**Given** instance `toy-01` exists and the solver pool is idle.
**When** the user submits `POST /solve` with `{"instance": {...toy-01...}, "params": {"maxTimeSeconds": 30}}`.
**Then** the backend returns HTTP 202 with a `jobId` and a `Location` header `/solution/{jobId}`; `GET /solution/{jobId}` returns `status in {pending, running}` immediately after.
**Verifies:** FR-08.
**Tested via:** integration.

## AC-10: Solver respects time limit

**Given** a medium instance (30 nurses × 28 days) is available and the request body sets `maxTimeSeconds = 5`.
**When** the user issues `POST /solve`.
**Then** the final `SolveResponse` has `solveTimeSeconds <= 6.0` and `status in {feasible, unknown}`.
**Verifies:** FR-09.
**Tested via:** integration.

## AC-11: Polling returns monotonically improving objective

**Given** a medium instance is being solved (polling every 500 ms).
**When** the user polls `GET /solution/{jobId}` repeatedly over 20 seconds.
**Then** each response's `objective` (when present) is ≤ the previous response's `objective` until terminal status is reached.
**Verifies:** FR-10.
**Tested via:** integration.

## AC-12: SSE stream emits solution events

**Given** a medium instance is being solved.
**When** the user opens `GET /solutions/{jobId}/stream`.
**Then** the stream emits at least one `solution` event within 5 seconds; each event's `data` payload parses as `SolveResponse`; the stream closes after the job reaches a terminal status.
**Verifies:** FR-11.
**Tested via:** integration.

## AC-13: Terminal status includes metadata

**Given** `toy-02` is solved to optimality.
**When** the user issues `GET /solution/{jobId}` after completion.
**Then** the response contains `status = "optimal"`, and numeric `objective`, `bestBound`, `gap`, `solveTimeSeconds` all present.
**Verifies:** FR-12.
**Tested via:** integration.

## AC-14: Cancellation transitions status within 5 seconds

**Given** a medium instance is running with a 60-second budget.
**When** the user issues `POST /solve/{jobId}/cancel` five seconds into the solve.
**Then** within 5 more seconds, `GET /solution/{jobId}` returns `status = "cancelled"` and the last partial schedule (if any) is present.
**Verifies:** FR-13.
**Tested via:** integration.

## AC-15: Idempotency-Key dedupes submissions

**Given** the backend is idle.
**When** the user issues two identical `POST /solve` requests with the same `Idempotency-Key` header.
**Then** both requests return HTTP 202 with the same `jobId`, and the second request does not create an additional job row.
**Verifies:** FR-14.
**Tested via:** integration.

## AC-16: Roster table renders all cells

**Given** a completed schedule for `toy-02`.
**When** the user views `/solves/{jobId}/schedule`.
**Then** the roster table renders 5 rows (one per nurse) × 14 columns (one per day) = 70 cells; each cell shows a shift id or "off"; the keyboard Tab sequence traverses cells in reading order.
**Verifies:** FR-15.
**Tested via:** e2e.

## AC-17: Gantt view is reachable

**Given** a completed schedule is displayed as a roster.
**When** the user clicks the "Gantt" toggle.
**Then** the view switches to a Gantt timeline with per-nurse lanes showing shift durations.
**Verifies:** FR-16.
**Tested via:** e2e.

## AC-18: CSV export has correct shape

**Given** a completed schedule with 70 assignments.
**When** the user clicks "Download CSV".
**Then** the downloaded file has filename `schedule-toy-02-{jobId}.csv`, contains a header row `nurseId,nurseName,day,shiftId,shiftLabel`, and 70 data rows matching the assignments.
**Verifies:** FR-17.
**Tested via:** e2e.

## AC-19: Solve metadata visible on schedule page

**Given** a completed schedule with objective 138, gap 0.0 %.
**When** the user views `/solves/{jobId}/schedule`.
**Then** the page header shows `status=optimal`, `objective=138`, `gap=0.0%`, and `solveTimeSeconds` with one-decimal precision.
**Verifies:** FR-18.
**Tested via:** e2e.

## AC-20: Progress page updates live

**Given** a medium instance is being solved.
**When** the user opens `/solves/{jobId}` and watches for 10 seconds.
**Then** the displayed `objective` and `gap` values update at least once as SSE events arrive; the "Elapsed" counter advances.
**Verifies:** FR-19.
**Tested via:** e2e.

## AC-21: Soft-constraint violations are listed

**Given** `toy-02` solves with a preference violation.
**When** the user views the schedule.
**Then** the violations panel lists at least one entry whose `code` matches `SC-N` for some N in 1..5, with a readable message and a penalty value.
**Verifies:** FR-20.
**Tested via:** e2e.

## AC-22: Infeasibility page names a hard constraint

**Given** an instance is constructed with impossible coverage (e.g. `required = 5` for a night shift with only 3 nurses available).
**When** the user solves it.
**Then** `status = "infeasible"`; the frontend renders the infeasibility view naming at least one `HC-N` code with an explanation.
**Verifies:** FR-21.
**Tested via:** e2e.

## AC-23: Backend attaches hard-violation explanation

**Given** the same impossible instance as AC-22.
**When** `GET /solution/{jobId}` is called.
**Then** `schedule.violations` contains at least one entry with `severity = "hard"` and a `code` of the form `HC-N`.
**Verifies:** FR-22.
**Tested via:** integration.

## AC-24: Both backends serve the same OpenAPI

**Given** both backends are running.
**When** the test fetches `/openapi.yaml` from each.
**Then** the response bytes are byte-for-byte identical to the checked-in `apps/shared/openapi.yaml`.
**Verifies:** FR-23, FR-28.
**Tested via:** integration.

## AC-25: Backend parity on toy-02

**Given** `toy-02` is loaded into both backends.
**When** the test solves it on both with identical `params` (including `randomSeed = 42`).
**Then** both backends return `status = "optimal"`; their `objective` values agree within ±5 % **or** the coverage (by `(day, shiftId)` count) is identical.
**Verifies:** FR-24.
**Tested via:** integration.

## AC-26: Frontend backend selector persists

**Given** the user is on the Python backend.
**When** the user selects "Kotlin" and reloads the page.
**Then** the header still shows "Kotlin"; subsequent requests go to `http://localhost:8080`.
**Verifies:** FR-25.
**Tested via:** e2e.

## AC-27: Health endpoint is live

**Given** the backend process is running.
**When** the test requests `GET /health`.
**Then** the response is HTTP 200 with body `{"status": "ok", "service": "py-api"|"kt-api"}` within 200 ms.
**Verifies:** FR-26.
**Tested via:** integration.

## AC-28: Version endpoint exposes OR-Tools version

**Given** the backend process is running.
**When** the test requests `GET /version`.
**Then** the response includes non-empty `version`, `ortools`, and `runtime` strings.
**Verifies:** FR-27.
**Tested via:** integration.

## AC-29: Served OpenAPI matches checked-in file

**Given** both backends are running.
**When** the test fetches `/openapi.yaml`.
**Then** the bytes match `apps/shared/openapi.yaml`.
**Verifies:** FR-28.
**Tested via:** integration.

## AC-30: Performance targets on reference hardware

**Given** reference hardware (8 cores, 16 GB RAM).
**When** each size is solved with defaults (`numSearchWorkers = 4`).
**Then** the targets in NFR-01 are met: toy-01 < 1 s, toy-02 < 2 s, medium < 10 s first feasible, large < 5 min within 1 % gap.
**Verifies:** NFR-01.
**Tested via:** manual (benchmark script recorded in `benchmarks/`).

## AC-31: API p95 latency below 200 ms

**Given** the backend has 10 concurrent clients hitting `GET /instances`, `GET /instances/{id}`, `GET /health`, `GET /version`.
**When** the test measures latency over 60 seconds.
**Then** p95 is below 200 ms for each endpoint.
**Verifies:** NFR-02.
**Tested via:** integration (load test).

## AC-32: Frontend time-to-interactive below 2 s

**Given** the schedule page is cold-loaded over a 100 Mbps connection.
**When** the Lighthouse measurement runs.
**Then** time-to-interactive is below 2 seconds.
**Verifies:** NFR-03.
**Tested via:** manual (Lighthouse run).

## AC-33: Scalability limits rejected

**Given** the backend is running.
**When** the user submits an instance with 300 nurses.
**Then** the backend returns HTTP 422 with `code = "instance.invalid"` and a message identifying the limit.
**Verifies:** NFR-04, NFR-05.
**Tested via:** integration.

## AC-34: `docker compose up` produces a running stack

**Given** a clean repository checkout.
**When** the test runs `docker compose up -d` and polls each service's `/health`.
**Then** all three services (py-api, kt-api, web) respond with 200 within 60 seconds.
**Verifies:** NFR-06, NFR-17.
**Tested via:** e2e.

## AC-35: Security baseline

**Given** a fresh instance is submitted containing a nurse with `name = "Test Nurse <script>alert(1)</script>"`.
**When** the app renders the instance detail page.
**Then** the name is rendered as text (not executed); the backend log does not contain the request body; payload > 1 MiB returns HTTP 413.
**Verifies:** NFR-07, NFR-08, NFR-09.
**Tested via:** manual (security walk-through) + integration.

## AC-36: PII warning is visible on upload

**Given** the user navigates to the home page.
**When** the page renders.
**Then** the PII warning copy is visible without scrolling on a 1024×768 viewport.
**Verifies:** NFR-10.
**Tested via:** e2e.

## AC-37: Observability endpoints live

**Given** the backend is running and has served at least one solve.
**When** the test requests `/metrics`.
**Then** the response is Prometheus text format containing `http_requests_total` with a count ≥ 1, `nsp_solve_requests_total` with a count ≥ 1, and `nsp_solve_duration_seconds` with bucketed samples.
**Verifies:** NFR-11, NFR-12, NFR-13.
**Tested via:** integration.

## AC-38: Browser compatibility

**Given** the frontend deployment.
**When** the e2e test suite runs against Chrome, Firefox, and Safari (latest 2 versions each via Playwright).
**Then** the core flows (upload, solve, view schedule) pass on each browser.
**Verifies:** NFR-14, NFR-16.
**Tested via:** e2e (multi-browser).

## AC-39: Accessibility scan passes

**Given** each page of the frontend.
**When** `@axe-core/playwright` scans it.
**Then** zero critical or serious violations are reported.
**Verifies:** NFR-15.
**Tested via:** e2e.

## AC-40: Compose stack round-trip

**Given** the stack is up via `docker compose up`.
**When** the e2e test runs: upload `toy-02`, solve, download CSV, verify CSV has 70 rows.
**Then** all steps succeed end-to-end.
**Verifies:** NFR-17.
**Tested via:** e2e.

---

## Sign-off checklist for v1.0 lock

All of the following must be true before the `spec-nsp-app-v1.0` tag is pushed and the app is considered shipped:

- [ ] Every MUST-priority functional requirement (FR-01..FR-28) has a passing automated test or a completed manual walk-through.
- [ ] Every non-functional requirement (NFR-01..NFR-17) has a passing automated or scripted manual check.
- [ ] Both backends pass the same contract-test suite (AC-24, AC-25, AC-29).
- [ ] `docker compose up` reproduces the stack from a clean checkout (AC-34, AC-40).
- [ ] The schedule CSV exports correctly (AC-18).
- [ ] Accessibility scans are clean (AC-39).
- [ ] Performance benchmarks on reference hardware are recorded in `benchmarks/` (AC-30).
- [ ] README documents run procedure, supported instance format, and links to this spec.
- [ ] `docs/adr/` contains ADRs for every non-obvious architectural decision (stack choice, SQLite choice, SSE vs WebSocket).
- [ ] The spec amendment log in `specs/nsp-app/README.md` correctly reflects the 1.0 lock entry.

Once all items are checked, the v1.0 tag is pushed and subsequent changes follow the amendment process defined in [README.md](README.md).
