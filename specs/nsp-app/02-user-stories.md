# 02 — User Stories

> **Status:** LOCKED v1.0
> **Last updated:** 2026-04-19

## Purpose

This section enumerates the user stories that the app satisfies. Every functional requirement in [04-functional-requirements.md](04-functional-requirements.md) traces back to at least one story here; every story has acceptance criteria that are reflected in the Given-When-Then scenarios in [09-acceptance-criteria.md](09-acceptance-criteria.md).

## Personas

Three personas, reflecting the audiences described in [01-vision-and-goals.md](01-vision-and-goals.md):

- **Scheduler** — ward supervisor or operations manager; primary persona; thinks in rosters.
- **Analyst** — operations-research practitioner or CP researcher; uses the API directly; benchmarks behaviour.
- **Learner** — developer, data scientist, or student following the curriculum; reads code as much as UI.

## Prioritisation

Each story carries a MoSCoW label for v1.0:

- **MUST** — required for the v1.0 lock; absence blocks ship.
- **SHOULD** — strongly desirable for v1.0; may slip to 1.1 with an ADR.
- **COULD** — nice-to-have for v1.0; commonly moved to later versions.
- **WON'T** — explicitly not in v1.0; recorded here to prevent scope creep.

## Story index

| Story | Persona | Priority | Title |
|---|---|---|---|
| US-01 | Scheduler | MUST | Upload an NSP instance |
| US-02 | Scheduler | MUST | Pick a built-in example instance |
| US-03 | Scheduler | MUST | View an instance's summary statistics |
| US-04 | Scheduler | MUST | Submit an instance for solving |
| US-05 | Scheduler | MUST | Configure a solve time limit |
| US-06 | Scheduler | MUST | Watch a solve in progress with streaming updates |
| US-07 | Scheduler | MUST | View the final schedule as a roster table |
| US-08 | Scheduler | SHOULD | View the final schedule as a Gantt timeline |
| US-09 | Scheduler | MUST | Download the schedule as CSV |
| US-10 | Scheduler | MUST | See an infeasibility explanation when no schedule exists |
| US-11 | Scheduler | SHOULD | Cancel a running solve |
| US-12 | Scheduler | SHOULD | See a validation error before a bad upload is persisted |
| US-13 | Analyst | MUST | Switch between Python and Kotlin backends |
| US-14 | Analyst | MUST | Submit an instance via the HTTP API |
| US-15 | Analyst | MUST | Poll the solve status and partial solutions |
| US-16 | Analyst | SHOULD | Tune solver parameters (workers, seed, linearization level) |
| US-17 | Analyst | COULD | Retrieve solve metadata (time, bound, gap) |
| US-18 | Learner | MUST | Browse existing instances with pagination |
| US-19 | Learner | MUST | Read the OpenAPI contract |
| US-20 | Learner | SHOULD | See consistent behaviour documented against HC-1..HC-8 and SC-1..SC-5 |
| US-21 | Scheduler | WON'T | Manually edit cells in a produced schedule |
| US-22 | Scheduler | WON'T | Compare two schedules side-by-side |

---

## Scheduler stories

### US-01: Upload an NSP instance

**As a** scheduler, **I want to** upload a JSON file that describes my ward (nurses, shifts, horizon, coverage, preferences), **so that** I can solve it without installing anything locally.

**Priority:** MUST

**Acceptance criteria:**
- The home page exposes a drag-and-drop upload zone and a file-picker button.
- The uploaded file is validated against [`nsp-instance.schema.json`](../../apps/shared/schemas/nsp-instance.schema.json) server-side.
- A valid upload results in the instance being persisted with a generated id and the browser navigating to the instance detail page.
- An invalid upload surfaces a structured error indicating which field failed validation, with no partial persistence.

### US-02: Pick a built-in example instance

**As a** scheduler, **I want to** pick a bundled example (`toy-01`, `toy-02`) without having to prepare a JSON file, **so that** I can evaluate the app before preparing real data.

**Priority:** MUST

**Acceptance criteria:**
- The home page lists at least two built-in examples with short descriptions.
- Selecting an example populates the instance list as if it had been uploaded.
- The bundled examples remain available across restarts (loaded on startup from `data/nsp/`).

### US-03: View an instance's summary statistics

**As a** scheduler, **I want to** see an instance's basic shape (number of nurses, horizon, shift types, coverage totals) on a dedicated page, **so that** I can confirm the upload is what I expected before kicking off an expensive solve.

**Priority:** MUST

**Acceptance criteria:**
- The instance detail page shows: id, name, source, horizonDays, nurse count, shift count, total coverage slots.
- The raw instance JSON is available via a "Show JSON" expandable section.
- A prominent "Solve" button is present and visible without scrolling on a desktop viewport.

### US-04: Submit an instance for solving

**As a** scheduler, **I want to** click "Solve" on an instance and receive a job identifier, **so that** the solve runs asynchronously and I can check back on its progress.

**Priority:** MUST

**Acceptance criteria:**
- "Solve" issues `POST /solve` with the instance embedded in the request body (per the OpenAPI contract).
- A successful submission returns `202 Accepted` with a `jobId` and the browser navigates to the solve progress page.
- The solve runs in the background; no HTTP request is held open for the duration of the solve.

### US-05: Configure a solve time limit

**As a** scheduler, **I want to** set a wall-clock budget on the solve (e.g. 60 seconds), **so that** I get the best answer the solver can find in a predictable time.

**Priority:** MUST

**Acceptance criteria:**
- The solve-configuration dialog exposes a "time limit" field accepting values from 1 second to 3600 seconds.
- Default is 60 seconds.
- The value is forwarded to the backend as `params.maxTimeSeconds` (per `SolverParams` in the OpenAPI contract).
- When the budget expires, the solver returns the best solution found (status `feasible`) or `unknown` if no feasible solution was found.

### US-06: Watch a solve in progress with streaming updates

**As a** scheduler, **I want to** see intermediate solutions as the solver improves the objective, **so that** I can decide to stop early if the current solution is already acceptable.

**Priority:** MUST

**Acceptance criteria:**
- The solve progress page opens a Server-Sent Events subscription to `GET /solutions/{jobId}/stream`.
- Each `solution` event updates the displayed objective value, best bound, gap, and solve time.
- The stream closes cleanly when the solve reaches a terminal status (`optimal`, `feasible`, `infeasible`, `unknown`, `modelInvalid`, `error`).

### US-07: View the final schedule as a roster table

**As a** scheduler, **I want to** see the final schedule as a table with nurses on rows and days on columns, **so that** I can eyeball coverage, rest days, and patterns.

**Priority:** MUST

**Acceptance criteria:**
- The roster table renders a cell for each (nurse, day) pair.
- Cells show the shift id or "off" (for `shiftId = null`).
- Shifts are colour-coded; off-days are visually distinct from working days.
- Row and column headers stay visible as the user scrolls (sticky headers).

### US-08: View the final schedule as a Gantt timeline

**As a** scheduler, **I want to** toggle the schedule view to a Gantt-style timeline, **so that** I can see shift durations and cross-day transitions (night shifts spanning midnight) more naturally.

**Priority:** SHOULD

**Acceptance criteria:**
- A toggle switches between "Roster" and "Gantt" views on the schedule page.
- The Gantt view renders each assignment as a horizontal bar in a per-nurse lane, positioned by the shift's start time.
- The current time-of-day axis is labelled in local time based on the shifts' `start`/`end` (or `startMinutes`/`durationMinutes`) fields.

### US-09: Download the schedule as CSV

**As a** scheduler, **I want to** download the schedule as a CSV file, **so that** I can paste it into a spreadsheet or hand it to HR.

**Priority:** MUST

**Acceptance criteria:**
- A "Download CSV" button triggers a browser download.
- The CSV has a header row `nurseId,nurseName,day,shiftId,shiftLabel` and one row per assignment.
- Off-days appear as rows with an empty `shiftId` and `shiftLabel`.
- The filename is `schedule-{instanceId}-{jobId}.csv`.

### US-10: See an infeasibility explanation when no schedule exists

**As a** scheduler, **I want to** see which hard constraint was impossible when the solver reports infeasibility, **so that** I can fix the instance rather than guessing what went wrong.

**Priority:** MUST

**Acceptance criteria:**
- When the solve status is `infeasible`, the schedule view is replaced by an infeasibility report.
- The report identifies at least one failing hard-constraint family by code (HC-1..HC-8) with a plain-English explanation.
- The report links to the instance detail page so the scheduler can correct and re-submit.

### US-11: Cancel a running solve

**As a** scheduler, **I want to** cancel a solve that is taking too long, **so that** I can iterate on the instance without waiting for the time limit to expire.

**Priority:** SHOULD

**Acceptance criteria:**
- A "Cancel" button is visible on the solve progress page while status is `pending` or `running`.
- Clicking it transitions the job to a `cancelled` terminal status within 5 seconds on reference hardware.
- The last partial solution (if any) remains accessible via `GET /solution/{jobId}`.

### US-12: See a validation error before a bad upload is persisted

**As a** scheduler, **I want to** be told which field of my JSON file is malformed, **so that** I can fix it instead of guessing.

**Priority:** SHOULD

**Acceptance criteria:**
- A JSON file failing schema validation returns a `400 Bad Request` with an `Error` body identifying `code`, `message`, and a `details` object containing the JSON Pointer to the offending field.
- No partial instance is persisted.
- The UI surfaces the error prominently and does not navigate away from the upload page.

---

## Analyst stories

### US-13: Switch between Python and Kotlin backends

**As an** analyst, **I want to** switch the app's active backend between the Python and Kotlin implementations from the UI, **so that** I can compare parity without redeploying.

**Priority:** MUST

**Acceptance criteria:**
- A backend selector is visible in the application header.
- Selecting a backend changes the base URL used by the frontend for subsequent requests.
- The selected backend persists in browser local storage across reloads.
- The selector displays the current backend's `GET /version` response on hover.

### US-14: Submit an instance via the HTTP API

**As an** analyst, **I want to** hit `POST /solve` with `curl` or a scripted client, **so that** I can benchmark solver behaviour without touching the UI.

**Priority:** MUST

**Acceptance criteria:**
- The API is described by [`apps/shared/openapi.yaml`](../../apps/shared/openapi.yaml) (OpenAPI 3.1).
- Both backends accept and validate `SolveRequest` bodies identically.
- Responses conform to `SolveAccepted` on success and `Error` on failure.

### US-15: Poll the solve status and partial solutions

**As an** analyst, **I want to** poll `GET /solution/{jobId}` and receive the current best schedule, **so that** I can integrate the API into scripts that don't handle SSE.

**Priority:** MUST

**Acceptance criteria:**
- `GET /solution/{jobId}` returns a `SolveResponse` with the current best schedule (if any) and status.
- Polling does not affect the solve; it is a read-only operation.
- Unknown `jobId` returns `404 Not Found` with an `Error` body.

### US-16: Tune solver parameters

**As an** analyst, **I want to** pass `params.numSearchWorkers`, `params.randomSeed`, and `params.linearizationLevel` through the API, **so that** I can tune CP-SAT for benchmarking.

**Priority:** SHOULD

**Acceptance criteria:**
- The `SolverParams` object in the OpenAPI contract is fully supported by both backends.
- Invalid parameter combinations are rejected with `422 Unprocessable Entity`.
- Documented defaults are identical between backends.

### US-17: Retrieve solve metadata

**As an** analyst, **I want to** see the final objective, best bound, optimality gap, and solve time, **so that** I can record benchmarking results.

**Priority:** COULD

**Acceptance criteria:**
- `SolveResponse.objective`, `bestBound`, `gap`, and `solveTimeSeconds` are populated on terminal status.
- Values are present even when status is `feasible` (not yet proven optimal).

---

## Learner stories

### US-18: Browse existing instances with pagination

**As a** learner, **I want to** see a list of previously uploaded instances, **so that** I can re-run solves on them without re-uploading.

**Priority:** MUST

**Acceptance criteria:**
- The instances list page shows a paginated table of instances with id, name, source, horizonDays, and nurseCount columns.
- Default page size is 20; subsequent pages are loaded via a cursor-based mechanism.
- The list is sorted by creation time descending.

### US-19: Read the OpenAPI contract

**As a** learner, **I want to** read a machine-readable description of the HTTP API, **so that** I can understand what each backend implements without reading two codebases.

**Priority:** MUST

**Acceptance criteria:**
- Each backend serves the OpenAPI document at `GET /openapi.yaml` (or equivalent path advertised in the README).
- The served document matches `apps/shared/openapi.yaml` byte-for-byte in CI.
- The UI links to a rendered Swagger/Redoc view for human inspection.

### US-20: See consistent constraint documentation

**As a** learner, **I want to** see the exact set of hard and soft constraints the app enforces, with stable codes HC-1..HC-8 and SC-1..SC-5, **so that** I can map constraint violations from the UI back to the theory.

**Priority:** SHOULD

**Acceptance criteria:**
- [04-functional-requirements.md](04-functional-requirements.md) lists HC-1..HC-8 and SC-1..SC-5 with definitions.
- The UI's infeasibility report uses the same stable codes.
- Violations in `Schedule.violations` use the `code` field to reference these identifiers.

---

## Explicit non-stories (WON'T)

### US-21: Manually edit cells in a produced schedule

The app does not support manual modifications to a produced schedule. A future version may add this as a constrained repair flow with re-validation.

### US-22: Compare two schedules side-by-side

The app does not render a diff view between two schedules. A future version may add this as a benchmarking aid.
