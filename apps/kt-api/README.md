# kt-api — Ktor 3 backend for the NSP explorer

Kotlin twin of `apps/py-api`. Serves the Nurse Scheduling Problem REST API on
top of `libs/cpsat-kt` (Kotlin DSL over OR-Tools CP-SAT) and
`apps/kt-cp-sat/nsp-core` (instance model + CP-SAT model builder).

The endpoints, schemas, and status semantics are defined by the locked OpenAPI
contract at `apps/shared/openapi.yaml`. Both the Python and Kotlin backends
serve that same document verbatim at `/openapi.yaml`.

## Endpoints

| Method | Path                                   | Notes |
|--------|----------------------------------------|-------|
| GET    | `/health`                              | liveness / DB + solver checks |
| GET    | `/version`                             | `{service, version, runtime, ortools}` |
| GET    | `/openapi.yaml`                        | exact bytes of `apps/shared/openapi.yaml` |
| GET    | `/metrics`                             | Prometheus text exposition (Micrometer) |
| POST   | `/instances`                           | upload an NSP instance → 201 + Location |
| GET    | `/instances`                           | cursor-paged list (`limit`, `cursor`) |
| GET    | `/instances/{id}`                      | raw stored wire JSON |
| DELETE | `/instances/{id}`                      | 204 |
| POST   | `/solve`                               | accepts inline `instance` or `instanceId` → 202 + Location |
| GET    | `/solution/{jobId}`                    | poll for terminal state |
| POST   | `/solve/{jobId}/cancel`                | 202 while running; 409 when already terminal |
| GET    | `/solve/{jobId}/log`                   | `text/plain` CP-SAT search log |
| GET    | `/solutions/{jobId}/stream`            | Server-Sent Events — `event: solution`, data = `SolveResponse` |

### Headers

- `X-Request-Id` — propagated in and out via the `CallId` plugin.
- `Idempotency-Key` (POST /solve) — 24h dedup. Same key + same body returns the
  original jobId; same key + different body → **409 Conflict**.

### Errors

RFC 7807-ish envelope: `{"code", "message", "details"?}` with these codes:
`bad_request` (400), `not_found` (404), `validation_failed` (422),
`conflict` (409), `solver_pool_full` (503), `terminal_state` (409),
`internal_error` (500).

## Running locally

```bash
./gradlew run
# Server listens on :8080 by default
curl http://localhost:8080/health
curl http://localhost:8080/version
```

Two toy instances (`toy-01`, `toy-02`) are pre-loaded on first start from
`data/nsp/*.json`, so `GET /instances` returns something useful out of the
box.

### Environment variables

| Var                        | Default                           | Effect |
|----------------------------|-----------------------------------|--------|
| `PORT`                     | `8080`                            | bind port |
| `NSP_API_DB_URL`           | `jdbc:sqlite:./nsp-api.sqlite`    | instance + idempotency store |
| `NSP_API_SOLVER_CAPACITY`  | `min(4, CPUs)`                    | concurrent solves; full → 503 |

### Solver parameters on POST /solve

```json
{
  "instance": { ... full instance JSON ... },
  "params": {
    "maxTimeSeconds": 30,
    "numSearchWorkers": 4,
    "randomSeed": 1,
    "logSearchProgress": true,
    "objectiveWeights": { "SC1": 10, "SC2": 3, "SC3": 3, "SC4": 5, "SC5": 2 }
  }
}
```

The `objectiveWeights` object accepts either the SCn names from the OpenAPI
(`SC1..SC5`) or the friendly aliases `preference / fairness / workloadBalance /
weekendDistribution / consecutiveDaysOff` — whichever is more convenient for
your client.

## Tests

```bash
./gradlew test
```

Nine Kotest specs cover meta endpoints, instance CRUD, errors, idempotency, the
full solve lifecycle (both happy path and cancel-mid-solve), and the SSE
stream. Solver specs load OR-Tools natives in a `beforeSpec` warmup so the
first `/solve` test doesn't pay the JNI-load penalty.

## Building a Docker image

Build from the **repo root** so the Dockerfile can copy the composite-build
siblings (`apps/kt-cp-sat/nsp-core`, `libs/cpsat-kt`, `apps/shared/openapi.yaml`,
`data/nsp`):

```bash
docker build -f apps/kt-api/Dockerfile -t nsp-kt-api .
docker run --rm -p 8080:8080 nsp-kt-api
```

The image is a two-stage `eclipse-temurin:25-*` build; runtime is the slim
`-jre` variant and drops to a non-root user.

## Configuration

Ktor reads `src/main/resources/application.conf` (HOCON). Environment
variables override individual fields — e.g. `PORT=9090 ./gradlew run`.

## Requirements

- JDK 25+ (Temurin recommended — the same we use in the Dockerfile)
- Gradle 9.4+ (use the bundled wrapper)
- Composite-build siblings:
  - `libs/cpsat-kt` — Kotlin DSL over CP-SAT
  - `apps/kt-cp-sat/nsp-core` — domain model + ModelBuilder

## How it works (one-page tour)

1. `Application.module()` connects SQLite via Exposed, boots a
   `PrometheusMeterRegistry` with JVM + process binders, creates a
   `SolveJobRegistry` sized by `NSP_API_SOLVER_CAPACITY`, preloads toy
   instances, then calls `configureModule()` to install plugins and mount
   routes.
2. `SolveJobRegistry` owns concurrency. `submit()` does `semaphore.tryAcquire()`
   and throws `SolverPoolFullException` → 503 if full. Otherwise it launches
   the solver on `Dispatchers.Default` and exposes per-job state + a
   `MutableSharedFlow<SolveResponse>` (replay=1, DROP_OLDEST) for SSE.
3. `SolverAdapter` wraps `nsp-core`'s `ModelBuilder.buildHardModel` +
   `addSoftObjective`, adds an objective (`minimize weightedSum`), and drives
   `CpSolver.solve` with a `CpSolverSolutionCallback` that snapshots
   incumbents back into the job state.
4. Cancellation uses `CpSolverSolutionCallback.stopSearch()` via a
   thread-safe `AtomicReference<CancelHandle?>`. The coroutine is also
   cancelled, and `submit()`'s launch catches `CancellationException`
   separately from other throwables so cancel is never misreported as `error`.
5. OpenAPI is served by reading `apps/shared/openapi.yaml` — copied into the
   classpath by a `copyOpenApi` Gradle task at build time. A CWD fallback
   exists for `./gradlew run` from non-standard directories.
