# specs/nsp-app — Nurse Scheduling App Specification

**Version:** 0.1 (draft, unlocked)
**Status:** not yet locked
**Locked by:** —
**Locked on:** —
**Supersedes:** —

This is the canonical specification for the end-to-end nurse-scheduling web app we build in Phase 7. It gets written and locked in Chapter 14 before any app code is written.

## Reading order

1. [00-overview.md](00-overview.md) — what we're building, in one page
2. [01-vision-and-goals.md](01-vision-and-goals.md) — north star + non-goals
3. [02-user-stories.md](02-user-stories.md) — primary flows (Given/When/Then)
4. [03-domain-model.md](03-domain-model.md) — entities + relationships
5. [04-functional-requirements.md](04-functional-requirements.md) — FR-1..FR-N
6. [05-non-functional-requirements.md](05-non-functional-requirements.md) — perf/a11y/security
7. [06-api-contract.md](06-api-contract.md) — endpoint spec + OpenAPI
8. [07-ui-ux.md](07-ui-ux.md) — wireframes, flows, design
9. [08-data-model.md](08-data-model.md) — JSON schemas, ERD
10. [09-acceptance-criteria.md](09-acceptance-criteria.md) — how we know we're done

## How the spec gets locked

1. Written during Chapter 14 (teacher + student together).
2. Reviewed end-to-end: every user story maps to APIs; every FR has an AC; every NFR is testable.
3. Vanja says "locked v1.0" and we tag the repo `spec-nsp-app-v1.0`.
4. Any change thereafter requires an explicit spec amendment (bump version, update CHANGELOG in this README).

## Amendments log

| Version | Date | Summary |
|---|---|---|
| 0.1 | 2026-04-19 | Initial structure (empty placeholders) |
