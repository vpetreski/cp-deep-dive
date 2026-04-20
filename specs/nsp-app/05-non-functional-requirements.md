# 05 — Non-Functional Requirements

> **Status:** LOCKED v1.0
> **Last updated:** 2026-04-19

## Purpose

Quality attributes that the app must exhibit. Every NFR is measurable and maps to at least one acceptance criterion in [09-acceptance-criteria.md](09-acceptance-criteria.md). Reference hardware for all measurements is a modern laptop (Apple M-series or equivalent x86, 8 physical cores, 16 GB RAM) running the backend locally.

## NFR index

| ID | Area | Summary |
|---|---|---|
| NFR-01 | Performance | Solve-time targets by instance size |
| NFR-02 | Performance | API p95 latency on non-solve endpoints |
| NFR-03 | Performance | Frontend time-to-interactive |
| NFR-04 | Scalability | Largest supported instance shape |
| NFR-05 | Scalability | Concurrent solves per backend instance |
| NFR-06 | Availability | Single-instance deployment model |
| NFR-07 | Security | No authentication in v1.0 |
| NFR-08 | Security | Input validation and payload limits |
| NFR-09 | Security | No secrets in logs |
| NFR-10 | Privacy | PII-in-upload warning |
| NFR-11 | Observability | Structured JSON logs |
| NFR-12 | Observability | Prometheus metrics |
| NFR-13 | Observability | Solver search log capture |
| NFR-14 | Compatibility | Browser support matrix |
| NFR-15 | Accessibility | WCAG 2.1 AA |
| NFR-16 | Compatibility | Backend runtime matrix |
| NFR-17 | Reproducibility | `docker compose up` produces the full stack |

---

## Performance

### NFR-01: Solve-time targets

The solver shall meet the following targets on reference hardware with default parameters (`numSearchWorkers = 4`, `linearizationLevel = 1`):

| Instance size | Shape | Target |
|---|---|---|
| Toy | 3 nurses × 7 days × 2 shifts (`toy-01.json`) | Prove optimal in < 1 s |
| Small | 5 nurses × 14 days × 3 shifts (`toy-02.json`) | Prove optimal in < 2 s |
| Medium | 30 nurses × 28 days × 3 shifts | First feasible in < 10 s; good solution (gap ≤ 5 %) in < 60 s |
| Large | 100 nurses × 28 days × 3 shifts | Solution with gap ≤ 1 % in < 5 min with `numSearchWorkers = 8` |

Targets are wall-clock, per backend. The time limit configured on the request (`params.maxTimeSeconds`) overrides these targets; the targets above describe what the solver achieves when *allowed* to run up to that target.

### NFR-02: API p95 latency on non-solve endpoints

For every endpoint other than `POST /solve` and `GET /solutions/{jobId}/stream`, the 95th percentile response latency under nominal load (10 concurrent clients) shall be below 200 ms on reference hardware.

### NFR-03: Frontend time-to-interactive

The frontend's time-to-interactive on the main schedule view shall be below 2 seconds on a cold cache on reference hardware over a 100 Mbps connection.

---

## Scalability

### NFR-04: Largest supported instance shape

v1.0 officially supports instances up to 100 nurses × 28 days × 3 shifts. Instances larger than this may work but are not tested as part of the v1.0 gate. Validation shall reject instances with `nurses.length > 200` or `horizonDays > 90` with a `422 Unprocessable Entity` to prevent runaway solves.

### NFR-05: Concurrent solves per backend instance

Each backend shall support at least 4 concurrent solves using a bounded worker pool. Submissions beyond the pool capacity shall queue at `status = "pending"` rather than be rejected. Queue depth is capped at 32; further submissions return `503 Service Unavailable`.

---

## Availability

### NFR-06: Single-instance deployment model

v1.0 ships as a single-instance deployment. There is no SLA. The app is expected to run on the user's own hardware or a single container. Multi-instance deployment, horizontal scaling, or clustering is explicitly out of scope for v1.0.

---

## Security

### NFR-07: No authentication in v1.0

v1.0 exposes all endpoints without authentication. Deployments that need authentication shall provide it at the ingress layer (reverse proxy, API gateway, or basic auth wrapper). The spec does not define an authentication scheme and rate-limiting is noted as future work.

Rate-limiting is not a v1.0 requirement but is strongly recommended at the ingress layer for any deployment exposed to the public internet. Suggested default: 60 requests per minute per source IP.

### NFR-08: Input validation and payload limits

1. Every JSON payload shall be validated against its schema in [`apps/shared/schemas/`](../../apps/shared/schemas/) before processing.
2. Request bodies larger than 1 MiB shall be rejected with `413 Payload Too Large`.
3. Instances with `nurses.length > 200`, `horizonDays > 90`, or `shifts.length > 20` shall be rejected with `422 Unprocessable Entity`.
4. The SSE stream shall cap itself at 10,000 emitted events per job and close with a `limit_reached` terminal event if exceeded.

### NFR-09: No secrets in logs

Structured logs shall never contain: request bodies that might contain PII (see NFR-10), internal file paths, environment variable values, or stack frames from third-party libraries beyond the first. When PII is suspected in an instance payload, logs shall include only the instance `id` and shape metadata.

---

## Privacy

### NFR-10: PII-in-upload warning

The frontend upload view shall display a prominent warning:

> The app stores uploaded instances locally. Do not upload real nurse names or identifying information; use pseudonyms for public deployments.

Instances are not de-identified by the app; the warning is an explicit user responsibility.

---

## Observability

### NFR-11: Structured JSON logs

Both backends shall emit structured JSON logs with at least the fields: `timestamp`, `level`, `message`, `request_id`, `trace_id` (if available), `service`, `event`. Each solve job shall emit `solve.submitted`, `solve.started`, `solve.improved` (per SSE emission), and one terminal `solve.completed` / `solve.failed` event.

Log level defaults to `INFO` in production and `DEBUG` in local development (governed by the `LOG_LEVEL` environment variable).

### NFR-12: Prometheus metrics

Each backend shall expose `GET /metrics` (Prometheus text format) with at least:

| Metric | Type | Labels |
|---|---|---|
| `http_requests_total` | counter | `method`, `path`, `status` |
| `http_request_duration_seconds` | histogram | `method`, `path` |
| `nsp_solve_requests_total` | counter | `status` |
| `nsp_solve_duration_seconds` | histogram | `status` |
| `nsp_solve_objective` | histogram | (none) |
| `nsp_solve_infeasibility_rate` | gauge | (none) |

### NFR-13: Solver search log capture

For every solve, the backend shall retain the CP-SAT search log (`solver.parameters.log_search_progress = True`) and make it available via `GET /solve/{jobId}/log` as `text/plain`. The log is retained with the `SolveJob` record and expires with it.

---

## Compatibility

### NFR-14: Browser support

The frontend shall function on the latest 2 stable releases of:

- Google Chrome
- Mozilla Firefox
- Apple Safari
- Microsoft Edge

Older browsers are not supported. Internet Explorer is explicitly not supported.

### NFR-16: Backend runtime matrix

| Backend | Language / runtime | Minimum version |
|---|---|---|
| `apps/py-api/` | CPython | 3.12 |
| `apps/kt-api/` | JDK | 25 LTS |
| `apps/kt-api/` | Kotlin | 2.1 |
| Both | Google OR-Tools | 9.x (latest stable at lock time) |

CI shall test each backend against both its minimum and latest supported runtime versions.

---

## Accessibility

### NFR-15: WCAG 2.1 AA

The frontend shall meet WCAG 2.1 AA standards. In particular:

1. **Colour contrast.** Text and UI elements meet a 4.5:1 contrast ratio against background in both light and dark modes.
2. **Keyboard navigation.** All interactive elements are reachable and operable via keyboard alone; tab order follows logical reading order; visible focus indicators are present.
3. **Screen-reader labelling.** Roster-table cells carry `aria-label` attributes describing the nurse, day, and shift; the Gantt view uses `role="img"` with a descriptive `aria-label` per bar.
4. **Reduced motion.** Animations respect `prefers-reduced-motion`; SSE-driven updates fade gently without moving layout.
5. **Colour-blind-safe encoding.** Shifts are distinguished by both colour and a short textual label (shift id); infeasibility markers use icons in addition to colour.
6. **Automated gate.** `@axe-core/playwright` passes with zero critical issues as part of CI.

---

## Reproducibility

### NFR-17: `docker compose up` produces the full stack

From a clean repository checkout:

```
docker compose up
```

shall produce a running stack with:

- Python backend at `http://localhost:8000`
- Kotlin backend at `http://localhost:8080`
- Frontend at `http://localhost:5173` (dev) or `http://localhost:3000` (prod build)

All three services shall be reachable via `GET /health` (or the frontend's root URL) within 60 seconds of the `up` command returning.

---

## NFR traceability

Every NFR has at least one automated or scripted manual check in [09-acceptance-criteria.md](09-acceptance-criteria.md):

| NFR | AC family |
|---|---|
| NFR-01 | AC-30 (performance) |
| NFR-02 | AC-31 (latency) |
| NFR-03 | AC-32 (TTI) |
| NFR-04, NFR-05 | AC-33 (scalability) |
| NFR-06 | AC-34 (deployment) |
| NFR-07, NFR-08, NFR-09 | AC-35 (security) |
| NFR-10 | AC-36 (privacy warning visible) |
| NFR-11, NFR-12, NFR-13 | AC-37 (observability) |
| NFR-14, NFR-16 | AC-38 (compatibility) |
| NFR-15 | AC-39 (accessibility) |
| NFR-17 | AC-40 (compose up) |
