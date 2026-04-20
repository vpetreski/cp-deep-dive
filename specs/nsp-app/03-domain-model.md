# 03 — Domain Model

> **Status:** LOCKED v1.0
> **Last updated:** 2026-04-19

## Purpose

The canonical vocabulary of the app. Every entity here has a corresponding schema in [`apps/shared/schemas/`](../../apps/shared/schemas/) (for wire format) or a table in [08-data-model.md](08-data-model.md) (for persistence). Field names in the domain model match the JSON schemas; code in either backend uses the same names (`horizonDays`, `shiftId`, `nurseId`, etc.) to keep wire-format and domain terminology aligned.

## Entity overview

```
                      +----------------+
                      |    Instance    |
                      +----------------+
                      | id             |
                      | name           |
                      | source         |
                      | horizonDays    |
                      +----------------+
                        |         |  |
         +--------------+         |  +---------------+
         | 1..*                1..*                1..*
         v                       v                   v
  +----------------+     +----------------+   +----------------------+
  |     Nurse      |     |     Shift      |   | CoverageRequirement  |
  +----------------+     +----------------+   +----------------------+
  | id             |     | id             |   | day                  |
  | name           |     | label          |   | shiftId (-> Shift)   |
  | skills[]       |     | start / end    |   | required / min / max |
  | contractHours  |     | startMinutes   |   | requiredSkills[]     |
  | preferences[]  |     | durationMins   |   +----------------------+
  | unavailable[]  |     | skill?         |
  +----------------+     +----------------+
         |
         | 0..*
         v
  +----------------+
  |   Preference   |
  +----------------+
  | day            |
  | shiftId? (null |
  |  = day-off)    |
  | weight (±)     |
  +----------------+

                      +----------------+            +------------------+
                      |   SolveJob     |<---+---1---|    Instance      |
                      +----------------+    |       +------------------+
                      | id             |    |
                      | instanceId     |----+
                      | status         |              +------------------+
                      | params         |     produces | Schedule         |
                      | createdAt      |   ---------->+------------------+
                      | startedAt      |   |          | instanceId       |
                      | endedAt        |---+          | jobId            |
                      | objective      |              | generatedAt      |
                      | bestBound      |              | assignments[]    |
                      | gap            |              | violations[]     |
                      | solveTimeSec   |              +------------------+
                      +----------------+                       |
                                                               | 1..*
                                                               v
                                                      +------------------+
                                                      |   Assignment     |
                                                      +------------------+
                                                      | nurseId          |
                                                      | day              |
                                                      | shiftId (or null |
                                                      |   = day off)     |
                                                      +------------------+

                                                      +------------------+
                                                      |    Violation     |
                                                      +------------------+
                                                      | code (HC-N/SC-N) |
                                                      | message          |
                                                      | severity         |
                                                      | nurseId?         |
                                                      | day?             |
                                                      | penalty?         |
                                                      +------------------+
```

## Entities

### Instance

A complete NSP problem statement submitted to the app. Persists until explicitly deleted.

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string | yes | Stable identifier (generated if not provided on creation). |
| `name` | string | no | Human-readable label shown in the UI. |
| `source` | enum `toy` / `nsplib` / `inrc1` / `inrc2` / `custom` | no | Origin tag; `custom` for user uploads. |
| `horizonDays` | integer, `>= 1` | yes | Length of the planning horizon. |
| `shifts` | `Shift[]`, `minItems: 1` | yes | Shift types available in this instance. |
| `nurses` | `Nurse[]`, `minItems: 1` | yes | Nurses to be scheduled. |
| `coverage` | `CoverageRequirement[]` | yes | Required coverage per `(day, shiftId)` cell. |
| `forbiddenTransitions` | `[shiftIdA, shiftIdB][]` | no | Ordered pairs forbidden on consecutive days. |
| `metadata` | object | no | Free-form per-instance metadata. |

Invariants:

1. `shifts[*].id` values are unique within the instance.
2. `nurses[*].id` values are unique within the instance.
3. Every `coverage[*].shiftId` and every `forbiddenTransitions[*][0..1]` references a defined `shifts[*].id`.
4. `coverage[*].day` values are in `[0, horizonDays - 1]`.
5. Every `preferences[*].day` and `unavailable[*]` value is in `[0, horizonDays - 1]`.
6. `coverage[*].requiredSkills` tags match tags that appear on at least one nurse's `skills`.

### Nurse

A schedulable individual within an instance.

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string | yes | Unique within the instance. |
| `name` | string | no | Display name. |
| `skills` | string[] | no | Skill tags held by this nurse. |
| `contractHoursPerWeek` | integer `0..80` | yes (toy) | Contract hours for HC-8. |
| `maxShiftsPerWeek` | integer `>= 0` | no | Maximum shifts per week. |
| `minShiftsPerWeek` | integer `>= 0` | no | Minimum shifts per week. |
| `maxConsecutiveWorkingDays` | integer `>= 1` | no | Per-nurse override for HC-4. |
| `preferences` | `Preference[]` | no | Soft preferences contributing to SC-1. |
| `unavailable` | integer[] | no | Days on which the nurse cannot be assigned any shift. |

Invariants:

1. If `minShiftsPerWeek` and `maxShiftsPerWeek` are both set, `min <= max`.
2. `unavailable` values are within `[0, horizonDays - 1]` and unique.

### Shift

A named time-of-day block.

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string | yes | Stable short identifier (e.g. `M`, `D`, `N`). |
| `label` | string | yes | Human-readable name. |
| `start` | HH:MM string | yes (toy) | Local-time start, 24-hour format. |
| `end` | HH:MM string | yes (toy) | Local-time end, 24-hour format. |
| `startMinutes` | integer `0..1439` | no (wire) | Wire-format alternative to `start`. |
| `durationMinutes` | integer `>= 1` | no (wire) | Wire-format duration. |
| `skill` | string | no | Skill required by anyone working this shift. |

Invariants:

1. `id` is unique within the instance.
2. Either `start`+`end` or `startMinutes`+`durationMinutes` is present.
3. A shift may cross midnight (`end < start`); in that case it spans two calendar days for Gantt display purposes but is still indexed by its start day.

### CoverageRequirement

The demand for a specific `(day, shift)` cell.

| Field | Type | Required | Description |
|---|---|---|---|
| `day` | integer `>= 0` | yes | Day index in `[0, horizonDays - 1]`. |
| `shiftId` | string | yes | References a defined `Shift.id`. |
| `min` | integer `>= 0` | yes (toy) | Minimum nurses required. |
| `max` | integer `>= 0` | yes (toy) | Maximum nurses assignable. |
| `required` | integer `>= 0` | yes (wire) | Exact or minimum coverage (wire format; maps to `min`). |
| `requiredSkills` | string[] | no | Skill tags that at least some nurses on this cell must hold. |

Invariants:

1. `max >= min`.
2. No duplicate `(day, shiftId)` entries for a single instance.

### Preference

A soft preference held by a nurse about a `(day, shiftId)` cell.

| Field | Type | Required | Description |
|---|---|---|---|
| `nurseId` | string | yes (toy) | References a defined `Nurse.id`. |
| `day` | integer `>= 0` | yes | Day index within the horizon. |
| `shiftId` | string or null | yes | Null = preference about a day off; string = preference about a specific shift. |
| `weight` | integer | yes | Positive = nurse wants this cell; negative = nurse avoids it. |

Invariants:

1. `weight != 0` (a zero-weight preference is redundant and rejected at validation time).

### Assignment

A single scheduled cell; the atomic unit of a `Schedule`.

| Field | Type | Required | Description |
|---|---|---|---|
| `nurseId` | string | yes | References a defined `Nurse.id`. |
| `day` | integer `>= 0` | yes | Day index within the horizon. |
| `shiftId` | string or null | no | Null or missing = the nurse has the day off. |

Invariants:

1. At most one `Assignment` per `(nurseId, day)` in a single `Schedule`.
2. If `shiftId` is non-null, it references a defined `Shift.id` of the source instance.

### Schedule

The output of a solve.

| Field | Type | Required | Description |
|---|---|---|---|
| `instanceId` | string | yes | Instance the schedule is for. |
| `jobId` | string | no | The producing `SolveJob.id`. |
| `generatedAt` | ISO-8601 datetime | yes | Generation timestamp. |
| `assignments` | `Assignment[]` | yes | Full roster. |
| `violations` | `Violation[]` | no | Soft-constraint violations detected by the validator. |

Invariants:

1. `assignments` is non-empty for any schedule with status `feasible` or `optimal`.
2. The set of `(nurseId, day)` pairs covered by `assignments` exactly equals the Cartesian product of the instance's `Nurse.id` values and day indices `[0, horizonDays - 1]`.

### SolveJob

A transient entity tracking the lifecycle of a solve.

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string | yes | Job identifier (returned to the client). |
| `instanceId` | string | yes | Instance being solved. |
| `status` | enum (see state diagram below) | yes | Current status. |
| `params` | `SolverParams` object | no | Parameters passed in the request. |
| `createdAt` | ISO-8601 datetime | yes | Submission time. |
| `startedAt` | ISO-8601 datetime | no | When the worker began executing. |
| `endedAt` | ISO-8601 datetime | no | Terminal-status time. |
| `objective` | number | no | Current/final objective value. |
| `bestBound` | number | no | Current/final best bound. |
| `gap` | number | no | Relative optimality gap. |
| `solveTimeSeconds` | number `>= 0` | no | Wall-clock time consumed so far. |
| `error` | string | no | Present for `error` / `modelInvalid` status. |

### Violation

A soft-constraint violation (or hard-constraint explanation in infeasibility reports).

| Field | Type | Required | Description |
|---|---|---|---|
| `code` | string | yes | Stable identifier (`HC-1`..`HC-8`, `SC-1`..`SC-5`). |
| `message` | string | yes | Plain-English description. |
| `severity` | enum `hard` / `soft` | no | Matches the `HC-`/`SC-` prefix. |
| `nurseId` | string | no | Nurse the violation attaches to (if localisable). |
| `day` | integer `>= 0` | no | Day the violation attaches to (if localisable). |
| `penalty` | number | no | Objective contribution for soft violations; omitted for hard. |

## Relationships

| Relationship | Cardinality | Notes |
|---|---|---|
| `Instance` ↔ `Nurse` | 1..* | Nurses belong to a single instance. |
| `Instance` ↔ `Shift` | 1..* | Shift sets are per-instance. |
| `Instance` ↔ `CoverageRequirement` | 1..* | Each instance has coverage entries for every relevant cell. |
| `Nurse` ↔ `Preference` | 0..* | A nurse may have zero or more preferences. |
| `Instance` ↔ `SolveJob` | 1..* | An instance can be solved many times. |
| `SolveJob` → `Schedule` | 0..1 | A completed feasible/optimal job has exactly one produced schedule; other statuses have none. |
| `Schedule` ↔ `Assignment` | 1..* | A schedule is a bag of assignments. |
| `Schedule` ↔ `Violation` | 0..* | A schedule may carry zero or more soft-constraint violations. |

## SolveJob state diagram

```
                  +---------+
          submit  |         |                POST /solve succeeds
          ------->| pending |----+
                  |         |    |
                  +---------+    |  worker picks up the job
                                 v
                             +---------+
                  +----------| running |-------------------+
                  |          |         |                   |
                  |          +---------+                   |
                  |            |   |   |                   |
                  |   solver   |   |   | solver improves   | cancel requested
                  |   proves   |   |   | objective         |
                  |   optimal  |   |   |                   |
                  |            v   |   v                   v
                  |        +---------+ +---------+   +-----------+
                  |        | optimal | |feasible |   | cancelled |
                  |        +---------+ +---------+   +-----------+
                  |
                  | solver proves infeasible
                  v
             +------------+
             | infeasible |
             +------------+
                  ^
                  |  wall-clock expires before feasible OR
                  |  solver returns UNKNOWN
                  |
             +-----------+          +------------+          +--------------+
             |  unknown  |<---------|  running   |--------->| modelInvalid |
             +-----------+          +------------+          +--------------+
                                          |
                                          | backend-internal error
                                          v
                                     +---------+
                                     |  error  |
                                     +---------+
```

### Status definitions

| Status | Terminal? | Meaning |
|---|---|---|
| `pending` | No | Accepted, not yet started. |
| `running` | No | Worker is executing the solve. |
| `optimal` | Yes | Objective is proven minimum; the schedule is optimal under the given weights. |
| `feasible` | Yes | A feasible schedule was found, but optimality is not proven (e.g. time limit hit). |
| `infeasible` | Yes | No schedule can satisfy all hard constraints. |
| `unknown` | Yes | Time limit expired before any feasible schedule was found; not proven infeasible. |
| `cancelled` | Yes | User requested cancellation (US-11). A partial schedule may be attached. |
| `modelInvalid` | Yes | The constructed CP-SAT model was rejected before solving (internal bug or bad input that slipped past validation). |
| `error` | Yes | An internal error occurred; the job is dead. |

Transition rules:

1. A job enters exactly one terminal status; terminal statuses do not transition further.
2. `running` may emit many intermediate partial solutions via SSE; each emission updates `objective`, `bestBound`, `gap`, and `solveTimeSeconds` but does not change `status` until a terminal condition is reached.
3. `cancelled` is only reachable by an explicit cancel request; a time limit alone produces `feasible` (if a solution was found) or `unknown` (if not).

## Invariants across entities

1. Every `Assignment.nurseId` in a `Schedule` references a `Nurse.id` in the source `Instance`.
2. Every `Assignment.shiftId` in a `Schedule` (when non-null) references a `Shift.id` in the source `Instance`.
3. For any `Assignment` where `shiftId` is non-null, the corresponding `Nurse.skills` satisfies any `CoverageRequirement.requiredSkills` for the `(day, shiftId)` cell it contributes to (HC-7).
4. A `Schedule` is self-consistent: for every cell `(nurseId, day)` in the horizon, there is exactly one `Assignment` record (possibly with `shiftId = null`).
5. `CoverageRequirement.requiredSkills` never references an empty string; missing skill lists are encoded as an empty array, not null.
