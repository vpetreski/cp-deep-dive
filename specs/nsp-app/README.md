# specs/nsp-app — Nurse Scheduling App Specification

**Version:** 1.0 (LOCKED)
**Status:** LOCKED v1.0
**Locked by:** Vanja Petreski
**Locked on:** 2026-04-19
**Supersedes:** 0.1 (initial skeletons)

This is the canonical specification for the end-to-end nurse-scheduling web app produced by the `cp-deep-dive` learning project. It is the authoritative contract for `apps/py-api/` (FastAPI), `apps/kt-api/` (Ktor 3), and `apps/web/` (Vite + React 19 + React Router v7). No app code is written that contradicts this spec; divergence requires an amendment (see below).

## Reading order

1. [00-overview.md](00-overview.md) — what the app is, in one page
2. [01-vision-and-goals.md](01-vision-and-goals.md) — north star, design principles, non-goals
3. [02-user-stories.md](02-user-stories.md) — personas + user stories with acceptance criteria
4. [03-domain-model.md](03-domain-model.md) — entities, relationships, invariants, state diagrams
5. [04-functional-requirements.md](04-functional-requirements.md) — FR-1..FR-N, plus HC-1..HC-8 and SC-1..SC-5
6. [05-non-functional-requirements.md](05-non-functional-requirements.md) — performance, security, accessibility, observability
7. [06-api-contract.md](06-api-contract.md) — HTTP contract (summarises `apps/shared/openapi.yaml`)
8. [07-ui-ux.md](07-ui-ux.md) — wireframes, flows, design tokens
9. [08-data-model.md](08-data-model.md) — persistence, tables, JSON schemas, retention
10. [09-acceptance-criteria.md](09-acceptance-criteria.md) — Given-When-Then scenarios and sign-off checklist

## Source-of-truth files

- **Instance schema:** [`/data/nsp/schema.json`](../../data/nsp/schema.json) (toy-format reference) and [`/apps/shared/schemas/nsp-instance.schema.json`](../../apps/shared/schemas/nsp-instance.schema.json) (wire-format reference).
- **HTTP API:** [`/apps/shared/openapi.yaml`](../../apps/shared/openapi.yaml).
- **Wire schemas:** [`/apps/shared/schemas/*.schema.json`](../../apps/shared/schemas/).

The spec files in this directory describe **intent, rationale, and acceptance**. The JSON/YAML files listed above describe **shape**. If these two disagree, the machine-readable files are authoritative for shape and this spec is authoritative for intent — reconcile via amendment.

## How the spec is locked

1. Written end-to-end by the learning-project owner and reviewed as a single artefact.
2. Every user story maps to at least one functional requirement; every functional requirement maps to at least one acceptance criterion; every non-functional requirement has a measurable target.
3. The owner declares "locked v1.0" and the repo is tagged `spec-nsp-app-v1.0`.
4. Any change thereafter requires an explicit amendment: bump the version number, add an entry to the amendments log below, and update the `Locked on` header.

## Amendment policy

Amendments must be additive or clarifying. If a change is breaking (removes a requirement, renames an entity, changes a status-code contract), bump the major version and update `docs/adr/` with an ADR explaining the decision. Minor versions (1.1, 1.2, ...) cover additive or wording-only changes.

## Amendments log

| Version | Date | Summary |
|---|---|---|
| 0.1 | 2026-04-19 | Initial structure (empty placeholders) |
| 1.0 | 2026-04-19 | Initial lock, 2026-04-19 — full v1.0 content for all 10 sections. Ratified hard constraints HC-1..HC-8 and soft constraints SC-1..SC-5. Adopted `apps/shared/openapi.yaml` and `apps/shared/schemas/*.schema.json` as wire-format source of truth. |
