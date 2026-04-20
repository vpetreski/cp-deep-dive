# 06 — API Contract

> **Status:** LOCKED v1.0
> **Last updated:** 2026-04-19

## Purpose

The HTTP contract that both backends implement. The machine-readable source of truth is [`apps/shared/openapi.yaml`](../../apps/shared/openapi.yaml) (OpenAPI 3.1). This section summarises that contract in prose, defines the error envelope, pagination, idempotency, CORS, and versioning, and describes each endpoint's intent.

## Source of truth

**If this file and `apps/shared/openapi.yaml` disagree, the OpenAPI document wins** for shape (field names, types, enums, required flags, HTTP methods, response codes). This file wins for intent, rationale, and lifecycle descriptions not naturally expressed in OpenAPI.

Wire-format schemas:

- [`nsp-instance.schema.json`](../../apps/shared/schemas/nsp-instance.schema.json)
- [`solve-request.schema.json`](../../apps/shared/schemas/solve-request.schema.json)
- [`solve-response.schema.json`](../../apps/shared/schemas/solve-response.schema.json)
- [`schedule.schema.json`](../../apps/shared/schemas/schedule.schema.json)

All endpoints accept and return `application/json` unless otherwise noted.

## Versioning

v1.0 does not version the API path. All endpoints are served at the root. A v2 API would be served at `/v2/` while `/` continues to serve v1 for a deprecation window. This decision is recorded as ADR 0003 (to be authored alongside API scaffolding).

## Servers

| Environment | Python backend | Kotlin backend |
|---|---|---|
| Local development | `http://localhost:8000` | `http://localhost:8080` |
| Docker compose | `http://py-api:8000` (internal) | `http://kt-api:8080` (internal) |

Both backends listen on HTTP only in v1.0. TLS termination is deferred to ingress.

## Endpoint index

| Method | Path | operationId | Purpose |
|---|---|---|---|
| GET | `/health` | `getHealth` | Liveness probe. |
| GET | `/version` | `getVersion` | Runtime + solver version. |
| GET | `/openapi.yaml` | `getOpenApi` | Serve the OpenAPI document. |
| GET | `/metrics` | `getMetrics` | Prometheus metrics (text format). |
| POST | `/solve` | `postSolve` | Submit an instance for solving. |
| GET | `/solution/{jobId}` | `getSolution` | Fetch latest solution (polling). |
| GET | `/solutions/{jobId}/stream` | `streamSolutions` | SSE stream of incremental solutions. |
| POST | `/solve/{jobId}/cancel` | `cancelSolve` | Cancel a running solve. |
| GET | `/solve/{jobId}/log` | `getSolveLog` | Retrieve the CP-SAT search log. |
| POST | `/instances` | `postInstance` | Create (upload) an instance. |
| GET | `/instances` | `listInstances` | Paginated list of instances. |
| GET | `/instances/{id}` | `getInstance` | Fetch a single instance. |
| DELETE | `/instances/{id}` | `deleteInstance` | Delete an instance and its solve jobs. |

---

## Endpoint details

### GET /health

Liveness probe. Returns 200 when the process is up and the database is reachable; returns 200 with `status = "degraded"` when the database is reachable but a dependency is failing. Returns 503 when the process is unable to serve traffic.

| Response | Body shape | Description |
|---|---|---|
| 200 | `HealthResponse` | Up (optionally degraded). |
| 503 | `Error` | Dependency failure; process cannot serve traffic. |

Example `200` body:
```json
{"status": "ok", "service": "py-api"}
```

### GET /version

Returns the runtime, solver, and API version.

| Response | Body shape |
|---|---|
| 200 | `VersionResponse` |

Example `200` body:
```json
{
  "version": "1.0.0",
  "ortools": "9.11.4210",
  "runtime": "python 3.12.7",
  "service": "py-api"
}
```

### GET /openapi.yaml

Serves the OpenAPI 3.1 document as `application/yaml`. The served bytes must match the checked-in [`apps/shared/openapi.yaml`](../../apps/shared/openapi.yaml). CI enforces a byte-for-byte comparison.

### GET /metrics

Serves Prometheus metrics in the text exposition format. Content-Type: `text/plain; version=0.0.4`. See NFR-12 for the required metric set.

### POST /solve

Submits an instance for solving. Accepts a `SolveRequest` body (instance + optional params).

**Request body:** `SolveRequest`.

**Optional headers:**
- `Idempotency-Key: <client-generated UUID>` — if present, a second request with the same key and identical body within 24 hours returns the original `jobId` with the same HTTP status (202) rather than creating a new job. A second request with the same key and a different body returns `409 Conflict`.

**Responses:**

| Status | Body shape | Meaning |
|---|---|---|
| 202 | `SolveAccepted` | Job accepted; `jobId` returned. |
| 400 | `Error` | Malformed request body (invalid JSON). |
| 409 | `Error` | Idempotency-Key conflict (same key, different body). |
| 413 | `Error` | Payload larger than 1 MiB. |
| 422 | `Error` | Schema-valid but semantically invalid (e.g. 300 nurses). |
| 503 | `Error` | Solver pool full; retry later. |

The response includes a `Location` header pointing to `GET /solution/{jobId}` for subsequent polling.

### GET /solution/{jobId}

Fetches the latest known solution for a job. Returns the most recent partial solution while the solver is running; returns the final solution once the job reaches a terminal status.

**Responses:**

| Status | Body shape | Meaning |
|---|---|---|
| 200 | `SolveResponse` | Current or final state of the job. |
| 404 | `Error` | No such job. |

Polling the endpoint does not affect the solve. Clients should poll at most every 500 ms.

### GET /solutions/{jobId}/stream

Subscribes to a Server-Sent Events stream of partial solutions. The stream emits `solution` events with `SolveResponse` JSON bodies, each representing the current best schedule and metadata at the time of emission.

**Headers:**
- Content-Type: `text/event-stream`
- Cache-Control: `no-cache`
- X-Accel-Buffering: `no` (suppresses proxy buffering)

**Event format:**
```
event: solution
data: {"jobId":"...","status":"running","objective":142,"bestBound":138,"gap":0.028,"solveTimeSeconds":17.4,"schedule":{...}}

```

Event stream terminates when:
1. The job reaches a terminal status (`optimal`, `feasible`, `infeasible`, `unknown`, `cancelled`, `modelInvalid`, `error`). A final event is emitted with the terminal `SolveResponse`, then the stream closes with no further events.
2. The client disconnects.
3. The server's 10,000-event cap is reached, in which case a `limit_reached` event is emitted and the stream closes.

**Responses:**

| Status | Meaning |
|---|---|
| 200 | Stream opened; events follow. |
| 404 | No such job. |

### POST /solve/{jobId}/cancel

Requests cancellation of a running solve. Cancellation is cooperative; the backend sets a flag that the solver polls. The job transitions to `cancelled` within 5 seconds on reference hardware.

**Responses:**

| Status | Body shape | Meaning |
|---|---|---|
| 202 | `SolveResponse` | Cancellation accepted; status reflects current state. |
| 404 | `Error` | No such job. |
| 409 | `Error` | Job is already in a terminal state. |

### GET /solve/{jobId}/log

Returns the CP-SAT search log as `text/plain`. See NFR-13.

**Responses:**

| Status | Meaning |
|---|---|
| 200 | Log body (may be empty for jobs still in `pending`). |
| 404 | No such job. |

### POST /instances

Uploads an instance. Accepts an `NspInstance` object (the `instance` field of `SolveRequest`, standalone).

**Responses:**

| Status | Body shape | Meaning |
|---|---|---|
| 201 | `NspInstance` | Created; body is the canonical stored form including any server-assigned `id`. |
| 400 | `Error` | Malformed request body. |
| 413 | `Error` | Payload larger than 1 MiB. |
| 422 | `Error` | Schema-valid but semantically invalid. |

The response includes a `Location` header of the form `/instances/{id}`.

### GET /instances

Returns a paginated list of instances, sorted by creation time descending. Pagination is cursor-based.

**Query parameters:**
- `limit` — integer in `[1, 100]`, default 20.
- `cursor` — opaque cursor returned by a previous response; omit on the first call.

**Response body:**
```json
{
  "items": [ {"id": "...", "name": "...", "source": "toy", "horizonDays": 14, "nurseCount": 5}, ... ],
  "nextCursor": "eyJvZmZzZXQiOjIwfQ"  // omitted if no more pages
}
```

**Responses:**

| Status | Meaning |
|---|---|
| 200 | List returned. |
| 400 | Invalid `cursor` or `limit`. |

### GET /instances/{id}

Fetches a single instance by id.

**Responses:**

| Status | Body shape | Meaning |
|---|---|---|
| 200 | `NspInstance` | Instance found. |
| 404 | `Error` | No such instance. |

### DELETE /instances/{id}

Deletes an instance and all solve jobs that reference it.

**Responses:**

| Status | Meaning |
|---|---|
| 204 | Deleted (or already absent; idempotent). |
| 404 | No such instance. |

---

## Error envelope

Every 4xx and 5xx response uses the `Error` schema defined in [`apps/shared/openapi.yaml`](../../apps/shared/openapi.yaml):

```json
{
  "code": "instance.invalid",
  "message": "Field /nurses/2/skills is not an array.",
  "details": {
    "pointer": "/nurses/2/skills",
    "expected": "array",
    "actual": "string"
  }
}
```

### Canonical error codes

| Code | HTTP status | Meaning |
|---|---|---|
| `request.malformed` | 400 | JSON parse error or schema validation failure. |
| `instance.invalid` | 422 | Schema-valid but semantically invalid (e.g. duplicate `nurse.id`). |
| `instance.tooLarge` | 413 | Payload larger than the configured ceiling. |
| `instance.notFound` | 404 | Instance id unknown. |
| `job.notFound` | 404 | Job id unknown. |
| `job.conflict` | 409 | Cancel on a terminal job, idempotency-key mismatch, or state-conflict. |
| `solver.poolFull` | 503 | Solver worker pool saturated; retry. |
| `solver.modelInvalid` | 500 | The constructed CP-SAT model was rejected pre-solve. |
| `internal.unknown` | 500 | Unhandled server error; `details.traceId` included for correlation. |

Errors shall always include `code` and `message`. `details` is recommended where it helps the caller act.

---

## Pagination

List endpoints (`GET /instances`) use cursor-based pagination:

1. The initial request omits `cursor`.
2. The response includes `nextCursor` if more results exist; clients pass it unmodified on the next request.
3. Cursors are opaque; clients must not interpret or modify them.
4. A cursor remains valid for at least 24 hours; after that, the server may return `400 Bad Request` with code `request.malformed`.

## Idempotency

`POST /solve` accepts an optional `Idempotency-Key` header:

1. Key format: any UUID or unique string up to 128 characters.
2. A second `POST /solve` with the same key and identical body within 24 hours returns the original `jobId` with the same HTTP status (202), rather than creating a new job.
3. A second `POST /solve` with the same key but a different body returns `409 Conflict` with code `job.conflict`.
4. After 24 hours, the key is eligible for reuse.

Idempotency is optional; omitting the header is the recommended default for UI-driven submissions.

## CORS

Both backends configure CORS as follows:

| Directive | Value (default) |
|---|---|
| `Access-Control-Allow-Origin` | Configurable via `CORS_ORIGINS` env var, comma-separated list; defaults to `http://localhost:5173,http://localhost:3000`. |
| `Access-Control-Allow-Methods` | `GET, POST, DELETE, OPTIONS` |
| `Access-Control-Allow-Headers` | `Content-Type, Idempotency-Key` |
| `Access-Control-Max-Age` | `600` |

Preflight (`OPTIONS`) requests are handled by the HTTP framework; no custom logic is added.

## Content negotiation

All endpoints accept only `Content-Type: application/json` on request bodies; any other type returns `415 Unsupported Media Type`. All endpoints respond with `Content-Type: application/json` (except `GET /openapi.yaml` → `application/yaml`, `GET /metrics` → `text/plain`, `GET /solve/{jobId}/log` → `text/plain`, and the SSE stream → `text/event-stream`).

## Rate limiting (optional)

v1.0 does not implement rate limiting; NFR-07 notes this as future work. Production deployments are expected to rate-limit at the ingress layer. Headers `X-RateLimit-*` are reserved for future use and must not be relied upon by clients in v1.0.

---

## Reference links

- OpenAPI 3.1 specification — <https://spec.openapis.org/oas/v3.1.0>
- JSON Schema 2020-12 — <https://json-schema.org/draft/2020-12/release-notes.html>
- Server-Sent Events spec — <https://html.spec.whatwg.org/multipage/server-sent-events.html>
- Prometheus text exposition format — <https://prometheus.io/docs/instrumenting/exposition_formats/>
- HTTP status codes (IANA) — <https://www.iana.org/assignments/http-status-codes/http-status-codes.xhtml>
