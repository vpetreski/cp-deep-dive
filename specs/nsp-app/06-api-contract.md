# 06 — API Contract

> **Status:** placeholder — fill in Chapter 14
> **Last updated:** 2026-04-19

## Purpose of this section

The HTTP(S) API contract that both backends (FastAPI `apps/py-api/` and Ktor 3
`apps/kt-api/`) implement identically. The prose in this file is the human
summary; the authoritative machine-readable contract is
`apps/shared/openapi.yaml` (OpenAPI 3.1).

## Outline of content to fill

- [ ] Endpoint list (table: method, path, purpose, auth, reference to OpenAPI
      operationId)
  - [ ] `POST /instances` — upload/create an instance
  - [ ] `GET /instances` — list instances
  - [ ] `GET /instances/{id}` — fetch an instance
  - [ ] `POST /instances/{id}/solve` — start a solve, returns job ID
  - [ ] `GET /solves/{jobId}` — poll solve status + partial solution
  - [ ] `GET /solves/{jobId}/events` — SSE/WebSocket stream of progress
  - [ ] `POST /solves/{jobId}/cancel` — cancel a running solve
  - [ ] `GET /schedules/{id}` — fetch a completed schedule
  - [ ] `POST /schedules/{id}/validate` — validate an edited schedule
- [ ] Request/response shapes (summary; full detail in OpenAPI)
- [ ] Error codes (4xx / 5xx inventory + meaning)
- [ ] Auth scheme (API key header: `X-API-Key`)
- [ ] Versioning strategy (URL prefix `/v1/`? Header?)
- [ ] Pointer: **authoritative spec is `apps/shared/openapi.yaml`**

## Relevant prior art

- `docs/plan.md` Chapter 15 for the backend architectural constraints already
  agreed (Pydantic v2 ↔ Kotlin `@Serializable` parity, thread-pool vs
  coroutine dispatch for CP-SAT).
- OpenAPI 3.1 spec
  (<https://spec.openapis.org/oas/v3.1.0>) for correct YAML shape.

<!--
TO FILL IN CHAPTER 14:
- Write prose here describing *intent* and the *shape* of each endpoint;
  leave exact JSON schemas to OpenAPI.
- Any endpoint not in OpenAPI doesn't exist. If they disagree, OpenAPI wins and
  this file is stale.
- Define error codes early — 400 (bad request), 401 (no key), 403 (wrong key),
  404 (not found), 409 (state conflict: cancelling an already-done solve), 422
  (schema-valid but semantically invalid), 429 (rate limit), 500, 503 (solver
  unavailable).
- SSE vs WebSocket: pick ONE for streaming progress. SSE is simpler; WS allows
  two-way cancel. Chapter 15 decides.
- Auth: API key in header is enough for v1.0. No OAuth.
-->
