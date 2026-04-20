# 08 — Data Model

> **Status:** placeholder — fill in Chapter 14
> **Last updated:** 2026-04-19

## Purpose of this section

How domain entities are persisted and exchanged. Tables (or collections), JSON
schemas for wire/file formats, indexes, and the migration approach. This
supplements `03-domain-model.md` (conceptual) with concrete storage shapes.

## Outline of content to fill

- [ ] Persistence strategy
  - [ ] v1.0: SQLite on disk via SQLModel (Python) and Exposed (Kotlin)
  - [ ] v2+: Postgres swap plan (connection string, migration tooling)
- [ ] Tables / collections
  - [ ] `instances` (id, name, horizon_days, payload JSONB, created_at)
  - [ ] `schedules` (id, instance_id, assignments JSONB, objective, status,
        solver_meta JSONB, created_at)
  - [ ] `solve_jobs` (id, instance_id, status, started_at, ended_at,
        progress JSONB)
  - [ ] `api_keys` (id, key_hash, label, revoked_at)
- [ ] Indexes (what to index + why)
- [ ] Migration approach (Alembic / Exposed migrations / plain SQL files)
- [ ] Instance file format
  - [ ] JSON schema path: `apps/shared/nsp-instance.schema.json`
  - [ ] INRC-II XML → our JSON converter in `tools/nsp-loader/`
- [ ] Schedule file format (export shape for PDF/CSV/ICS downstream)
- [ ] Backup / seed data

## Relevant prior art

- `docs/plan.md` §2 Persistence: SQLite → Postgres decided; SQLModel / Exposed
  for access.
- `docs/knowledge/nurse-scheduling/overview.md` for the INRC-I/II instance
  formats we must ingest.
- `apps/shared/nsp-instance.schema.json` (created in Chapter 11) is the
  authoritative schema — this file references it, doesn't duplicate it.

<!--
TO FILL IN CHAPTER 14:
- Prefer JSONB columns for the solver-facing payloads (instance + assignments)
  — they're naturally document-shaped and we'd otherwise be translating
  constantly.
- Relational columns for: IDs, foreign keys, timestamps, status enums,
  objective value. These are the things we filter/sort by.
- Index the hot paths: `solve_jobs.status`, `schedules.instance_id`.
- Migration story: even for SQLite, use a real tool — Alembic on the Py side,
  Exposed migrations on the Kt side. Don't hand-edit schemas.
- Seed data: ship 2–3 toy instances in `data/nsp/` and load them on first
  startup.
-->
