# py-api — FastAPI NSP backend

Skeleton for the Python half of the NSP app's twin backends (see
[`docs/plan.md`](../../docs/plan.md) Phase 7 / Chapter 15). Kotlin twin lives at
`apps/kt-api/`.

## Endpoints

| Method | Path | Status |
|---|---|---|
| `GET` | `/health` | implemented |
| `GET` | `/version` | implemented (reports OR-Tools version) |
| `POST` | `/solve` | **501** — returns `{"todo": "Phase 7 Chapter 15"}` |
| `GET` | `/solution/{id}` | **501** — returns `{"todo": "Phase 7 Chapter 15"}` |

## Run

```bash
uv sync
uv run uvicorn py_api.main:app --reload
```

Then:

```bash
curl localhost:8000/health
# {"status":"ok","service":"py-api"}

curl localhost:8000/version
# {"version":"0.1.0","ortools":"9.15.x"}
```

## Test

```bash
uv run pytest
```

## Docker

```bash
docker build -t cp-deep-dive/py-api .
docker run --rm -p 8000:8000 cp-deep-dive/py-api
```
