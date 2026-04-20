# 08 — Data Model

> **Status:** LOCKED v1.0
> **Last updated:** 2026-04-19

## Purpose

Persistence shapes. The domain entities in [03-domain-model.md](03-domain-model.md) are stored in SQLite tables on each backend. This section defines those tables, their indexes, the retention policy, and their relationship to the wire-format JSON schemas.

## Storage engine

v1.0 uses SQLite on local disk. Both backends open a single database file:

- Python backend: `apps/py-api/data/nsp.sqlite` (via SQLModel).
- Kotlin backend: `apps/kt-api/data/nsp.sqlite` (via Exposed).

The two backends do not share a database file in v1.0 — they maintain independent local stores. A future Postgres migration would switch both to a shared database; the ADR for that is deferred (out of scope for v1.0).

Why SQLite:
- Zero-install, file-based, sufficient for teaching-scale data volumes (< 10 GB).
- Fully transactional, with good JSON-column support (SQLite 3.38+).
- Easy to inspect (any SQLite browser) and to version-control snapshots.

## Schema overview

```
        +---------------+                 +----------------+
        |   instances   |<------1---------|   solve_jobs   |
        +---------------+                 +----------------+
        | id (PK)       |                 | id (PK)        |
        | name          |                 | instance_id (FK)|
        | source        |                 | status         |
        | horizon_days  |                 | params (JSON)  |
        | payload (JSON)|                 | created_at     |
        | created_at    |                 | started_at     |
        +---------------+                 | ended_at       |
              |                           | objective      |
              |                           | best_bound     |
              |                           | gap            |
              |                           | solve_time_sec |
              |                           | error          |
              |                           +----------------+
              |                                   |
              |                                   |
              |                                   +-------1-------+
              |                                                   |
              |                                                   v
              |                                          +------------------+
              +------1------->                           |    schedules     |
                                                         +------------------+
                                                         | id (PK)          |
                                                         | instance_id (FK) |
                                                         | job_id (FK)      |
                                                         | generated_at     |
                                                         | payload (JSON)   |
                                                         +------------------+
                                                                 |
                                                                 |
                                         +-----------------------+-----------------------+
                                         |                                               |
                                         v                                               v
                              +-------------------+                           +-------------------+
                              |   assignments     |                           |    violations     |
                              +-------------------+                           +-------------------+
                              | id (PK)           |                           | id (PK)           |
                              | schedule_id (FK)  |                           | schedule_id (FK)  |
                              | nurse_id          |                           | code              |
                              | day               |                           | message           |
                              | shift_id (null OK)|                           | severity          |
                              +-------------------+                           | nurse_id          |
                                                                              | day               |
                                                                              | penalty           |
                                                                              +-------------------+
```

Three of the tables (`instances`, `solve_jobs`, `schedules`) also carry a JSON column (`payload` or `params`) holding the full wire-format object. The flat columns exist to enable indexing and pagination; the JSON column is the authoritative form for downstream consumers.

## Table definitions

### `instances`

Stores uploaded and bundled instances.

```sql
CREATE TABLE instances (
  id             TEXT PRIMARY KEY,
  name           TEXT,
  source         TEXT NOT NULL CHECK (source IN ('toy','nsplib','inrc1','inrc2','custom')),
  horizon_days   INTEGER NOT NULL CHECK (horizon_days >= 1),
  nurse_count    INTEGER NOT NULL CHECK (nurse_count >= 1),
  shift_count    INTEGER NOT NULL CHECK (shift_count >= 1),
  payload        TEXT NOT NULL,  -- JSON, matches NspInstance
  created_at     TEXT NOT NULL   -- ISO-8601
);

CREATE INDEX idx_instances_created_at ON instances(created_at DESC);
CREATE INDEX idx_instances_source     ON instances(source);
```

`payload` is the full JSON per [`nsp-instance.schema.json`](../../apps/shared/schemas/nsp-instance.schema.json). `nurse_count` and `shift_count` are derived on insert and used by the list endpoint.

### `solve_jobs`

Stores the lifecycle of each solve.

```sql
CREATE TABLE solve_jobs (
  id                   TEXT PRIMARY KEY,
  instance_id          TEXT NOT NULL REFERENCES instances(id) ON DELETE CASCADE,
  status               TEXT NOT NULL CHECK (status IN (
    'pending','running','optimal','feasible','infeasible',
    'unknown','cancelled','modelInvalid','error'
  )),
  params               TEXT,          -- JSON, SolverParams
  idempotency_key      TEXT,
  created_at           TEXT NOT NULL,
  started_at           TEXT,
  ended_at             TEXT,
  objective            REAL,
  best_bound           REAL,
  gap                  REAL,
  solve_time_seconds   REAL,
  error                TEXT,
  search_log           TEXT,          -- CP-SAT search log
  expires_at           TEXT NOT NULL  -- retention cutoff
);

CREATE INDEX idx_solve_jobs_instance_id ON solve_jobs(instance_id);
CREATE INDEX idx_solve_jobs_status      ON solve_jobs(status);
CREATE INDEX idx_solve_jobs_created_at  ON solve_jobs(created_at DESC);
CREATE INDEX idx_solve_jobs_expires_at  ON solve_jobs(expires_at);
CREATE UNIQUE INDEX idx_solve_jobs_idempotency
  ON solve_jobs(idempotency_key) WHERE idempotency_key IS NOT NULL;
```

`expires_at` is set to `created_at + 30 days` on insert. A periodic job (Python: APScheduler; Kotlin: coroutine timer) deletes rows with `expires_at < now()` — see retention below.

### `schedules`

Stores produced schedules. Each solve job has at most one row; rows are created when the solver produces its first partial solution and updated in place as the solver improves.

```sql
CREATE TABLE schedules (
  id            TEXT PRIMARY KEY,
  job_id        TEXT UNIQUE NOT NULL REFERENCES solve_jobs(id) ON DELETE CASCADE,
  instance_id   TEXT NOT NULL REFERENCES instances(id) ON DELETE CASCADE,
  generated_at  TEXT NOT NULL,
  payload       TEXT NOT NULL  -- JSON, matches Schedule
);

CREATE INDEX idx_schedules_instance_id ON schedules(instance_id);
CREATE INDEX idx_schedules_job_id      ON schedules(job_id);
```

`payload` is the full JSON per [`schedule.schema.json`](../../apps/shared/schemas/schedule.schema.json).

### `assignments`

A flat, indexable copy of the `Schedule.assignments` list. Used by reporting queries and by the CSV exporter.

```sql
CREATE TABLE assignments (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  schedule_id  TEXT NOT NULL REFERENCES schedules(id) ON DELETE CASCADE,
  nurse_id     TEXT NOT NULL,
  day          INTEGER NOT NULL CHECK (day >= 0),
  shift_id     TEXT  -- null means day off
);

CREATE INDEX idx_assignments_schedule_id    ON assignments(schedule_id);
CREATE INDEX idx_assignments_schedule_day   ON assignments(schedule_id, day);
CREATE UNIQUE INDEX idx_assignments_unique
  ON assignments(schedule_id, nurse_id, day);
```

### `violations`

A flat copy of the `Schedule.violations` list.

```sql
CREATE TABLE violations (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  schedule_id  TEXT NOT NULL REFERENCES schedules(id) ON DELETE CASCADE,
  code         TEXT NOT NULL,        -- HC-N or SC-N
  severity     TEXT NOT NULL CHECK (severity IN ('hard','soft')),
  message      TEXT NOT NULL,
  nurse_id     TEXT,                 -- optional
  day          INTEGER,              -- optional
  penalty      REAL                  -- optional, present for soft
);

CREATE INDEX idx_violations_schedule_id ON violations(schedule_id);
CREATE INDEX idx_violations_code        ON violations(code);
```

---

## Migration approach

Each backend uses a migration toolchain native to its stack:

- **Python backend** — Alembic. Migration scripts live in `apps/py-api/alembic/versions/`. The initial migration (v1.0) creates all five tables above. Subsequent migrations are additive or data-migrating; destructive migrations require an ADR.
- **Kotlin backend** — Exposed's built-in DDL + Flyway for explicit migrations. Scripts live in `apps/kt-api/src/main/resources/db/migration/`. Naming: `V0001__initial_schema.sql`.

CI runs both migration toolchains against a fresh SQLite file and verifies that the resulting schema is identical (up to SQLite-vendor trivia). A drift test compares the two backends' schemas after applying all migrations.

### Seed data

On first startup:

1. Each backend checks whether `instances` contains any rows with `source = 'toy'`.
2. If not, it loads every `toy-*.json` from `data/nsp/` into `instances` with derived `id = {filename-without-extension}`, `name = {filename-without-extension}`, `source = 'toy'`.

Seed loading is idempotent and non-destructive.

---

## Data retention

| Entity | Retention | Deleted when |
|---|---|---|
| `instances` | Indefinite | User explicitly deletes (DELETE /instances/{id}). |
| `solve_jobs` | 30 days from `created_at` | Background sweeper finds `expires_at < now()`. Deletion cascades to `schedules`, `assignments`, `violations`. |
| `schedules` | Tied to `solve_jobs` | Cascade from `solve_jobs` deletion. |
| `assignments`, `violations` | Tied to `schedules` | Cascade from `schedules` deletion. |

The 30-day cap on `solve_jobs` is intentional and conservative: for a teaching app, a month of search history is plenty and keeps the SQLite file small. Deployments that need longer retention can override `JOB_RETENTION_DAYS` via environment variable.

---

## JSON schemas

Wire-format is authoritative. Storage keeps a JSON copy in the `payload` column for round-tripping.

| Entity | Wire schema |
|---|---|
| `Instance` | [`apps/shared/schemas/nsp-instance.schema.json`](../../apps/shared/schemas/nsp-instance.schema.json) |
| `SolveRequest` | [`apps/shared/schemas/solve-request.schema.json`](../../apps/shared/schemas/solve-request.schema.json) |
| `SolveResponse` | [`apps/shared/schemas/solve-response.schema.json`](../../apps/shared/schemas/solve-response.schema.json) |
| `Schedule` | [`apps/shared/schemas/schedule.schema.json`](../../apps/shared/schemas/schedule.schema.json) |

The toy-format schema at [`data/nsp/schema.json`](../../data/nsp/schema.json) is a slightly-older variant used by puzzle-book chapter code; the wire-format schemas are the v1.0 app-facing shape. The app accepts both on upload: if the incoming JSON matches the wire schema it is stored as-is; if it matches the toy schema, a small adapter converts it to the wire shape (primarily: `start`/`end` → `startMinutes`/`durationMinutes`, `demand[*].min`/`max` → `coverage[*].required`, `contractHoursPerWeek` preserved under `nurses[*].contractHoursPerWeek`).

---

## Indexes and performance notes

All indexes listed above are intentional:

- `idx_instances_created_at` — supports `GET /instances` sorted descending.
- `idx_solve_jobs_instance_id` — supports the cascade when an instance is deleted.
- `idx_solve_jobs_status` — supports the background sweeper's query `WHERE status IN (pending, running) AND created_at < threshold`.
- `idx_solve_jobs_expires_at` — supports the retention sweeper.
- `idx_solve_jobs_idempotency` (unique, partial) — supports `Idempotency-Key` lookups.
- `idx_assignments_unique` (unique) — enforces one assignment per `(schedule_id, nurse_id, day)`.
- `idx_assignments_schedule_day` — supports the CSV exporter's ordered read.

No composite indexes beyond those listed — adding more risks write amplification for what is a small working set.

---

## Backup and export

v1.0 does not ship automated backups. Operators relying on persistence should:

1. Stop the backend cleanly (SQLite will flush its WAL).
2. Copy the `nsp.sqlite` file to a backup location.
3. Restart.

Alternative: `sqlite3 nsp.sqlite .dump > nsp.sql` produces a text snapshot that can be replayed with `sqlite3 newdb.sqlite < nsp.sql`.

Full export of a single instance's history is available via:
- `GET /instances/{id}` — the instance itself.
- `GET /solve/{jobId}` for each job, filtered by `instance_id`.
- `GET /solve/{jobId}/log` for per-job search logs.

There is no bulk-export endpoint in v1.0.

---

## Reference links

- SQLite JSON1 extension — <https://www.sqlite.org/json1.html>
- SQLite WAL journal mode — <https://www.sqlite.org/wal.html>
- SQLModel — <https://sqlmodel.tiangolo.com/>
- Kotlin Exposed — <https://github.com/JetBrains/Exposed>
- Alembic — <https://alembic.sqlalchemy.org/>
- Flyway — <https://flywaydb.org/>
