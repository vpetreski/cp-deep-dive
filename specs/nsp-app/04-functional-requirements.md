# 04 — Functional Requirements

> **Status:** LOCKED v1.0
> **Last updated:** 2026-04-19

## Purpose

Enumerated functional requirements for v1.0. Each requirement has a stable identifier (`FR-NN`), a precise description, traceability to at least one user story, and traceability to at least one acceptance criterion. Non-functional targets (performance, accessibility, security) are in [05-non-functional-requirements.md](05-non-functional-requirements.md).

This section also defines the eight hard constraints (HC-1..HC-8) and five soft constraints (SC-1..SC-5) that the solver supports.

## Functional requirement index

| Area | IDs | Priority (MUST / SHOULD / COULD) |
|---|---|---|
| Instance management | FR-01..FR-07 | MUST (1-5), SHOULD (6-7) |
| Solve orchestration | FR-08..FR-14 | MUST (8-12), SHOULD (13-14) |
| Schedule viewing | FR-15..FR-19 | MUST (15, 17, 19), SHOULD (16, 18) |
| Constraint reporting | FR-20..FR-22 | MUST (20-21), SHOULD (22) |
| Backend switching | FR-23..FR-25 | MUST |
| Operational endpoints | FR-26..FR-28 | MUST |

---

## Instance management

### FR-01: Validate incoming instances against JSON schema

**Description:** The backend shall validate every instance payload (upload or inline in a `SolveRequest`) against [`nsp-instance.schema.json`](../../apps/shared/schemas/nsp-instance.schema.json) and reject malformed instances with an `Error` response identifying the offending field.

**Traces from:** US-01, US-12.
**Traces to:** AC-01, AC-02.
**Priority:** MUST.

### FR-02: Persist uploaded instances

**Description:** The backend shall persist a validated instance to storage with a generated `id` if one is not provided, returning the created resource via a `Location` header.

**Traces from:** US-01.
**Traces to:** AC-03.
**Priority:** MUST.

### FR-03: List instances with pagination

**Description:** The backend shall expose a paginated list of persisted instances, sorted by creation time descending, with cursor-based pagination.

**Traces from:** US-18.
**Traces to:** AC-04.
**Priority:** MUST.

### FR-04: Fetch a single instance by id

**Description:** The backend shall return a persisted instance by `id` with a 200 response; unknown ids return 404.

**Traces from:** US-03.
**Traces to:** AC-05.
**Priority:** MUST.

### FR-05: Support bundled example instances at startup

**Description:** The backend shall load `toy-01.json` and `toy-02.json` from [`data/nsp/`](../../data/nsp/) into storage on first startup, tagged with `source = "toy"`.

**Traces from:** US-02.
**Traces to:** AC-06.
**Priority:** MUST.

### FR-06: Delete instances

**Description:** The frontend shall offer a delete action on the instance detail page that calls `DELETE /instances/{id}`; the backend shall delete the instance and its associated solve jobs.

**Traces from:** (implied housekeeping; not directly a story).
**Traces to:** AC-07.
**Priority:** SHOULD.

### FR-07: Surface instance summary statistics

**Description:** The frontend shall display, for each instance: `id`, `name`, `source`, `horizonDays`, nurse count, shift count, and total coverage slots.

**Traces from:** US-03.
**Traces to:** AC-08.
**Priority:** SHOULD.

---

## Solve orchestration

### FR-08: Accept a solve request

**Description:** The backend shall accept `POST /solve` with a `SolveRequest` body, create a `SolveJob`, return `202 Accepted` with a `jobId`, and begin solving asynchronously.

**Traces from:** US-04, US-14.
**Traces to:** AC-09.
**Priority:** MUST.

### FR-09: Support configurable time limit

**Description:** The backend shall honour `params.maxTimeSeconds` (1..3600) as a soft wall-clock budget on the solve. When the budget expires, the solver shall return the best solution it has found with status `feasible` or `unknown`.

**Traces from:** US-05, US-16.
**Traces to:** AC-10.
**Priority:** MUST.

### FR-10: Expose solve status and partial solution via polling

**Description:** The backend shall expose `GET /solution/{jobId}` returning a `SolveResponse` with the current status, objective, best bound, gap, solve time, and the best schedule found so far (if any).

**Traces from:** US-15.
**Traces to:** AC-11.
**Priority:** MUST.

### FR-11: Stream partial solutions via Server-Sent Events

**Description:** The backend shall expose `GET /solutions/{jobId}/stream` as a `text/event-stream` endpoint emitting `solution` events containing `SolveResponse` JSON payloads, terminating when the job reaches a terminal status.

**Traces from:** US-06.
**Traces to:** AC-12.
**Priority:** MUST.

### FR-12: Report terminal status and final metadata

**Description:** On reaching a terminal status, the backend shall populate `objective`, `bestBound`, `gap`, and `solveTimeSeconds` in the final `SolveResponse` and the final SSE event.

**Traces from:** US-17.
**Traces to:** AC-13.
**Priority:** MUST.

### FR-13: Cancel a running solve

**Description:** The backend shall accept `POST /solve/{jobId}/cancel` while status is `pending` or `running`, causing the job to transition to `cancelled` within 5 seconds and preserving any last partial solution.

**Traces from:** US-11.
**Traces to:** AC-14.
**Priority:** SHOULD.

### FR-14: Support optional idempotency key

**Description:** The backend shall accept an `Idempotency-Key` header on `POST /solve`. Repeated submissions with the same key and same payload within a 24-hour window shall return the original `jobId` rather than creating a new job.

**Traces from:** (API robustness; analysts scripting solves).
**Traces to:** AC-15.
**Priority:** SHOULD.

---

## Schedule viewing

### FR-15: Render a roster table

**Description:** The frontend shall render the final (or latest partial) schedule as a two-dimensional roster table with nurses on rows and days on columns; off-days are visually distinct from working days; shifts are colour-coded by `shiftId`.

**Traces from:** US-07.
**Traces to:** AC-16.
**Priority:** MUST.

### FR-16: Render a Gantt timeline

**Description:** The frontend shall offer a toggle from the roster view to a Gantt timeline view, where each assignment is a horizontal bar in a per-nurse lane positioned by its shift's start time.

**Traces from:** US-08.
**Traces to:** AC-17.
**Priority:** SHOULD.

### FR-17: Export schedule to CSV

**Description:** The frontend shall provide a "Download CSV" action that produces a comma-separated file with header `nurseId,nurseName,day,shiftId,shiftLabel`, one row per assignment, and filename `schedule-{instanceId}-{jobId}.csv`.

**Traces from:** US-09.
**Traces to:** AC-18.
**Priority:** MUST.

### FR-18: Display solve metadata alongside the schedule

**Description:** The frontend shall display `status`, `objective`, `bestBound`, `gap`, and `solveTimeSeconds` adjacent to the rendered schedule.

**Traces from:** US-17.
**Traces to:** AC-19.
**Priority:** SHOULD.

### FR-19: Render a live progress indicator during solves

**Description:** The frontend shall display the solver's current best objective and gap on the solve progress page, updating in real time as SSE events arrive.

**Traces from:** US-06.
**Traces to:** AC-20.
**Priority:** MUST.

---

## Constraint reporting

### FR-20: Attach soft-constraint violations to schedules

**Description:** The backend shall include a `violations` list on every `Schedule` with one entry per soft-constraint violation, using the stable codes `SC-1`..`SC-5`, a plain-English `message`, and the numeric `penalty` contribution.

**Traces from:** US-20.
**Traces to:** AC-21.
**Priority:** MUST.

### FR-21: Render an infeasibility report

**Description:** When solve status is `infeasible`, the frontend shall render a dedicated infeasibility view identifying at least one failing hard-constraint family by code (`HC-1`..`HC-8`) with a plain-English explanation and links back to the offending instance.

**Traces from:** US-10.
**Traces to:** AC-22.
**Priority:** MUST.

### FR-22: Explain infeasibility at the backend

**Description:** When the solver proves a model infeasible, the backend shall compute at least one failing hard-constraint family and attach it to the `SolveResponse` via the `violations` list with `severity = "hard"`.

**Traces from:** US-10, US-20.
**Traces to:** AC-23.
**Priority:** SHOULD.

---

## Backend switching

### FR-23: Identical HTTP contract across backends

**Description:** Both the Python backend (`apps/py-api/`) and the Kotlin backend (`apps/kt-api/`) shall implement the OpenAPI contract in [`apps/shared/openapi.yaml`](../../apps/shared/openapi.yaml) identically: same paths, methods, request shapes, response shapes, and status codes.

**Traces from:** US-13, US-14.
**Traces to:** AC-24.
**Priority:** MUST.

### FR-24: Equivalent-quality output across backends

**Description:** For any given instance, `SolverParams`, and random seed, the two backends shall produce equivalent-quality schedules: the objective values shall agree within ±5 % or the coverage shall be identical.

**Traces from:** US-13.
**Traces to:** AC-25.
**Priority:** MUST.

### FR-25: Frontend backend selector

**Description:** The frontend shall expose a backend selector in the application header; the selection shall persist in `localStorage` and be applied to subsequent requests.

**Traces from:** US-13.
**Traces to:** AC-26.
**Priority:** MUST.

---

## Operational endpoints

### FR-26: Health endpoint

**Description:** Each backend shall expose `GET /health` returning `{"status": "ok", "service": "py-api"|"kt-api"}` with HTTP 200 when the process is up and the database is reachable, or `{"status": "degraded", ...}` with HTTP 200 otherwise.

**Traces from:** (operational).
**Traces to:** AC-27.
**Priority:** MUST.

### FR-27: Version endpoint

**Description:** Each backend shall expose `GET /version` returning `{"version": ..., "ortools": ..., "runtime": ...}` per the `VersionResponse` schema.

**Traces from:** (operational).
**Traces to:** AC-28.
**Priority:** MUST.

### FR-28: Serve OpenAPI document

**Description:** Each backend shall serve the OpenAPI 3.1 document at `GET /openapi.yaml` byte-identical to the checked-in [`apps/shared/openapi.yaml`](../../apps/shared/openapi.yaml).

**Traces from:** US-19.
**Traces to:** AC-29.
**Priority:** MUST.

---

## Hard constraints

All hard constraints are enforced in every solve. An instance that cannot satisfy all of HC-1..HC-8 is `infeasible`. Numbering is stable; `violations[*].code` uses the `HC-N` form.

### HC-1: Coverage

**Plain English:** Every `(day, shiftId)` cell must have at least `min` nurses and at most `max` nurses assigned.

**Mathematical sketch:** For each `CoverageRequirement` entry `c` with day `d` and shift `s`:
```
c.min  <=  sum over n in Nurses of x[n, d, s]  <=  c.max
```
where `x[n, d, s]` is the binary decision variable indicating whether nurse `n` works shift `s` on day `d`.

### HC-2: One shift per day per nurse

**Plain English:** A nurse works at most one shift on any given day.

**Mathematical sketch:** For each nurse `n` and each day `d`:
```
sum over s in Shifts of x[n, d, s]  <=  1
```

### HC-3: Forbidden shift transitions

**Plain English:** For every ordered pair `(s1, s2)` in the instance's `forbiddenTransitions`, a nurse working `s1` on day `d` must not work `s2` on day `d + 1`. This encodes rest-after-night and other transition bans.

**Mathematical sketch:** For each nurse `n`, each day `d` in `[0, horizonDays - 2]`, and each forbidden pair `(s1, s2)`:
```
x[n, d, s1] + x[n, d+1, s2]  <=  1
```

### HC-4: Max consecutive working days

**Plain English:** A nurse works at most `maxConsecutiveWorkingDays` calendar days in a row (global instance value, with per-nurse override via `Nurse.maxConsecutiveWorkingDays`).

**Mathematical sketch:** With `K = maxConsecutiveWorkingDays`, for each nurse `n` and each sliding window of `K + 1` consecutive days starting at `d` in `[0, horizonDays - K - 1]`:
```
sum over d' in [d, d + K] of  sum over s in Shifts of x[n, d', s]  <=  K
```

### HC-5: Max consecutive nights

**Plain English:** A nurse works at most 3 consecutive night shifts. ("Night" = any shift whose `id` matches the instance's designated night shift, detected by label or explicit metadata.)

**Mathematical sketch:** Let `N_SHIFTS` be the subset of shift ids classified as night shifts. For each nurse `n` and each sliding window of 4 consecutive days starting at `d` in `[0, horizonDays - 4]`:
```
sum over d' in [d, d + 3] of  sum over s in N_SHIFTS of x[n, d', s]  <=  3
```

### HC-6: Minimum rest between shifts

**Plain English:** Between the end of one shift and the start of the next, a nurse must have at least `minRestHours` hours of rest.

**Mathematical sketch:** This is encoded exclusively via `forbiddenTransitions` (HC-3). When an instance is loaded, pairs `(s1, s2)` whose implied rest gap (computed from shift `end` of day `d` to shift `start` of day `d+1`) is less than `minRestHours` are added to the forbidden-transition set before solving. No additional variables are introduced.

### HC-7: Skill-match on coverage

**Plain English:** When a `CoverageRequirement` lists `requiredSkills`, the nurses assigned to that cell must collectively hold each listed skill; and a shift tagged with a `skill` may only be assigned to nurses holding that skill.

**Mathematical sketch:** For each required skill `k` on coverage cell `(d, s)`:
```
sum over n in Nurses with k in n.skills of x[n, d, s]  >=  1
```
For each shift with an explicit `skill = k`:
```
x[n, d, s] = 0  for every nurse n not holding skill k
```

### HC-8: Contract hours

**Plain English:** Over each rolling 7-day window, a nurse's scheduled hours must lie within their `contractHoursPerWeek` ± tolerance. Tolerance defaults to 4 hours to accommodate shift granularity; configurable per instance via `metadata.contractTolerance`.

**Mathematical sketch:** Let `hours(s)` be the duration of shift `s` in hours. For each nurse `n` and each sliding 7-day window starting at `d` in `[0, horizonDays - 7]`:
```
contractHoursPerWeek[n] - tol  <=  sum over d' in [d, d + 6] of sum over s of x[n, d', s] * hours(s)  <=  contractHoursPerWeek[n] + tol
```

If `horizonDays < 7`, the constraint applies to the entire horizon scaled proportionally.

---

## Soft constraints

All soft constraints contribute a weighted penalty to the objective. The solver minimises the sum of penalties. Default weights are fixed at lock-in but can be overridden per-request via `params.objectiveWeights` (defined below).

Objective:
```
minimise  w_SC1 * violations_SC1
        + w_SC2 * violations_SC2
        + w_SC3 * violations_SC3
        + w_SC4 * violations_SC4
        + w_SC5 * violations_SC5
```

Default weights: `w_SC1 = 10`, `w_SC2 = 5`, `w_SC3 = 2`, `w_SC4 = 3`, `w_SC5 = 1`.

### SC-1: Preference honoring

**Plain English:** Each `Preference` entry contributes a penalty when the schedule does not honour it. Positive-weight preferences are violated when the nurse does not receive the requested cell; negative-weight preferences are violated when the nurse does receive the avoided cell.

**Mathematical sketch:** For each preference with `nurseId = n`, `day = d`, `shiftId = s`, `weight = w`:
```
if w > 0:    penalty_term += w * (1 - x[n, d, s])
if w < 0:    penalty_term += |w| * x[n, d, s]
if s is null (day-off preference): replace x[n, d, s] with sum over s' of x[n, d, s']
```

### SC-2: Fairness across nurses

**Plain English:** The total number of shifts each nurse works should be as equal as possible across the pool.

**Mathematical sketch:** Let `total[n] = sum over d, s of x[n, d, s]`. Let `maxTotal = max over n of total[n]` and `minTotal = min over n of total[n]`. The penalty is:
```
(maxTotal - minTotal)
```
encoded via `AddMaxEquality` / `AddMinEquality` auxiliaries in CP-SAT.

### SC-3: Workload balance

**Plain English:** Total scheduled hours (not just shift count) should be balanced across nurses relative to their contract.

**Mathematical sketch:** Let `hoursFraction[n] = (hours_scheduled[n]) / contractHoursPerWeek[n]`. Penalise `max over n of hoursFraction[n] - min over n of hoursFraction[n]` on an integer scale (hours scaled ×10 to preserve precision in CP-SAT's integer domain).

### SC-4: Weekend distribution

**Plain English:** Weekend assignments (Saturday and Sunday shifts) should be distributed evenly across nurses over the horizon.

**Mathematical sketch:** Let `weekendCount[n] = sum over d in WEEKENDS, s of x[n, d, s]` where `WEEKENDS = {d | d mod 7 in {5, 6}}`. Penalise `max over n of weekendCount[n] - min over n of weekendCount[n]`.

### SC-5: Consecutive days-off

**Plain English:** When a nurse has days off, those days should cluster together rather than be isolated.

**Mathematical sketch:** For each nurse `n` and each day `d` in `[1, horizonDays - 2]`, detect isolated days off via an indicator variable `isolated[n, d]`:
```
isolated[n, d] = 1  iff  the nurse works day d-1,
                         is off on day d,
                         and works day d+1
```
Penalty: `sum over n, d of isolated[n, d]`.

---

## Objective weights override

The `SolverParams` object accepts an optional extension at the spec level (`objectiveWeights`). Implementations shall accept this extension via the top-level `params` object on `POST /solve`:

```json
{
  "params": {
    "maxTimeSeconds": 60,
    "objectiveWeights": {
      "SC1": 10,
      "SC2": 5,
      "SC3": 2,
      "SC4": 3,
      "SC5": 1
    }
  }
}
```

If omitted, defaults apply. Each weight must be a non-negative integer in `[0, 1000]`.

---

## Traceability summary

Every FR maps to at least one user story and at least one acceptance criterion:

| FR | User stories | ACs |
|---|---|---|
| FR-01 | US-01, US-12 | AC-01, AC-02 |
| FR-02 | US-01 | AC-03 |
| FR-03 | US-18 | AC-04 |
| FR-04 | US-03 | AC-05 |
| FR-05 | US-02 | AC-06 |
| FR-06 | (housekeeping) | AC-07 |
| FR-07 | US-03 | AC-08 |
| FR-08 | US-04, US-14 | AC-09 |
| FR-09 | US-05, US-16 | AC-10 |
| FR-10 | US-15 | AC-11 |
| FR-11 | US-06 | AC-12 |
| FR-12 | US-17 | AC-13 |
| FR-13 | US-11 | AC-14 |
| FR-14 | (analyst scripting) | AC-15 |
| FR-15 | US-07 | AC-16 |
| FR-16 | US-08 | AC-17 |
| FR-17 | US-09 | AC-18 |
| FR-18 | US-17 | AC-19 |
| FR-19 | US-06 | AC-20 |
| FR-20 | US-20 | AC-21 |
| FR-21 | US-10 | AC-22 |
| FR-22 | US-10, US-20 | AC-23 |
| FR-23 | US-13, US-14 | AC-24 |
| FR-24 | US-13 | AC-25 |
| FR-25 | US-13 | AC-26 |
| FR-26 | (operational) | AC-27 |
| FR-27 | (operational) | AC-28 |
| FR-28 | US-19 | AC-29 |
