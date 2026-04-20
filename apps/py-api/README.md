# py-api — FastAPI NSP backend

Python half of the NSP app's twin backends (see
[`docs/plan.md`](../../docs/plan.md) Phase 7 / Chapter 15). Kotlin twin lives at
`apps/kt-api/`. Both implement the same `apps/shared/openapi.yaml` v1.0
contract.

The CP-SAT solver lives in the `nsp-core` library at
`apps/py-cp-sat/nsp-core/`. This service routes all solve work through
`nsp_core.solver.solve()` — it never imports `ortools.sat.python` directly.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/health` | Liveness + DB ping |
| `GET` | `/version` | Service + runtime + OR-Tools version |
| `GET` | `/openapi.yaml` | Byte-serves `apps/shared/openapi.yaml` |
| `GET` | `/metrics` | Prometheus exposition |
| `POST` | `/instances` | Upsert instance (201 + `Location`) |
| `GET` | `/instances` | List with cursor pagination |
| `GET` | `/instances/{id}` | Read instance |
| `DELETE` | `/instances/{id}` | Delete + cascade jobs/events |
| `POST` | `/solve` | Start job, 202 + `Idempotency-Key` dedup (24h) |
| `GET` | `/solution/{jobId}` | Job status + result |
| `GET` | `/solutions/{jobId}/stream` | Server-Sent Events (incumbents + terminal) |
| `POST` | `/solve/{jobId}/cancel` | Request cancellation |
| `GET` | `/solve/{jobId}/log` | Plain-text solver log |

Errors follow the RFC 7807-style `Error` envelope from the OpenAPI
schema (`{"code","message","details?"}`) with canonical codes
(`request.malformed`, `instance.invalid`, `instance.notFound`,
`job.notFound`, `job.conflict`, `solver.poolFull`).

## Run

```bash
cd apps/py-api
uv sync
uv run uvicorn py_api.main:app --reload
```

Then:

```bash
curl localhost:8000/health
# {"status":"ok","service":"py-api","version":"1.0.0","checks":{"db":"ok"}}

curl localhost:8000/version
# {"service":"py-api","version":"1.0.0","runtime":"python 3.12...","ortools":"9.15.x"}

curl localhost:8000/instances | jq
# seeded with data/nsp/toy-01.json and toy-02.json on first start
```

### Configuration

Environment variables (all optional):

| Var | Default |
|---|---|
| `NSP_API_DB_URL` | `sqlite+aiosqlite:///./py_api.sqlite` |
| `NSP_API_CORS_ORIGINS` | `http://localhost:5173,http://localhost:4173` |
| `NSP_API_MAX_BODY_BYTES` | `1048576` (1 MiB) |
| `NSP_API_SOLVE_CONCURRENCY` | `min(4, cpu_count())` |
| `NSP_API_SSE_EVENT_CAP` | `10000` |
| `NSP_API_DEFAULT_OBJECTIVE` | `hard` |

## Test

```bash
uv run ruff check .
uv run mypy --strict src/py_api
uv run pytest
```

## Docker

```bash
docker build -t cp-deep-dive/py-api .
docker run --rm -p 8000:8000 cp-deep-dive/py-api
```
