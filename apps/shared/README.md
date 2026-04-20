# apps/shared — NSP API contract

Contract-first artifacts shared by the two NSP backends
(`apps/py-api/`, `apps/kt-api/`) and the web UI (`apps/web/`). Both backends
implement this contract identically so the UI can switch between them with a
toggle.

> This package is a **stub** at v0.1.0. The complete, versioned contract is
> produced in Chapter 14 (`specs/nsp-app/`) and locked before Phase 7
> implementation begins. Expect breaking changes here until that lock happens.

## Contents

```
apps/shared/
├── openapi.yaml                      # OpenAPI 3.1 specification
└── schemas/
    ├── nsp-instance.schema.json      # Problem instance
    ├── schedule.schema.json          # Candidate / final roster
    ├── solve-request.schema.json     # POST /solve payload
    └── solve-response.schema.json    # GET  /solution/{jobId} payload
```

## Endpoints (stubs)

| Method | Path                              | Description                                   |
| ------ | --------------------------------- | --------------------------------------------- |
| GET    | `/health`                         | Liveness probe                                |
| GET    | `/version`                        | API + OR-Tools runtime version                |
| POST   | `/solve`                          | Submit an `NspInstance`; returns a `jobId`    |
| GET    | `/solution/{jobId}`               | Latest `SolveResponse` (may be partial)       |
| GET    | `/solutions/{jobId}/stream`       | SSE stream of `SolveResponse` events          |

## Validation

```bash
npm install
npm run validate          # openapi + schemas
npm run validate:openapi  # swagger-cli validate openapi.yaml
npm run validate:schemas  # ajv-compile every schemas/*.schema.json
```

`validate:schemas` runs against **JSON Schema 2020-12** with strict mode on —
this catches malformed refs, unknown keywords, and type mismatches early.

## Design notes

- The `NspInstance` shape here is the canonical one. When `data/nsp/` lands
  with toy instances + loaders, its `data/nsp/schema.json` is this file (copy
  or symlink), so that `apps/py-api/` and `apps/kt-api/` parse the same
  payload and `tools/validate-schedule.py` remains useful end-to-end.
- `shiftId` is a free-form string so instances from `toy`, `nsplib`, and
  `inrc2` can coexist without a global shift enumeration.
- `Assignment.shiftId = null` (or the row being absent) = day off. This is
  cheaper to represent than a dedicated "OFF" shift and keeps coverage
  calculations clean.
- Cross-file `$ref` uses **filenames** (`"schedule.schema.json"`) rather than
  absolute `$id`s so the same schemas work locally (both for Ajv compile and
  code-gen tools like `openapi-typescript`).

## Future (Chapter 14)

- Full contract lock with acceptance criteria per endpoint.
- Generate TS client (for `apps/web/`), Pydantic models (for `py-api`), and
  `kotlinx.serialization` data classes (for `kt-api`) from this file.
- Add auth, rate-limit, and idempotency headers per
  [`docs/plan.md`](../../docs/plan.md) Phase 7.
