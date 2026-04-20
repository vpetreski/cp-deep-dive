# nsp-core

Shared NSP (Nurse Scheduling Problem) primitives for the cp-deep-dive Python stack.
Used by chapter code (`ch11-nsp-v1`, `ch12-nsp-v2`, `ch13-nsp-v3`) and the FastAPI
backend (`apps/py-api`).

## What lives here

- `domain.py` — frozen dataclasses matching `apps/shared/schemas/nsp-instance.schema.json`
  (`Instance`, `Nurse`, `Shift`, `CoverageRequirement`, `Preference`, `Schedule`,
  `Assignment`, `Violation`).
- `loader.py` — JSON Schema validation + normalisation to the domain model.
- `model_v1.py` — CP-SAT model covering hard constraints HC-1..HC-8.
- `model_v2.py` — extends v1 with soft constraints SC-1..SC-5 and a weighted objective.
- `solver.py` — high-level `solve()` entrypoint that wraps the CP-SAT `CpSolver`,
  maps status codes to the wire `SolveResponse.status` enum, and extracts the
  `Schedule`.
- `validator.py` — standalone schedule validator: given an `Instance` + a `Schedule`,
  return the list of `Violation`s (both hard and soft). Used by tests for certification
  round-trips and by `docs/` tooling.

## Why it is its own package

- Keeps the three chapter packages small — each chapter adds one teaching idea, not
  a full re-implementation.
- Lets `apps/py-api` depend on exactly one solver binary without dragging in pytest,
  ruff, or any chapter-specific noise.
- Makes it easy to bump the domain model in lockstep with
  `apps/shared/schemas/nsp-instance.schema.json`.
