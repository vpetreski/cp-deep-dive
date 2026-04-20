# 03 — Domain Model

> **Status:** placeholder — fill in Chapter 14
> **Last updated:** 2026-04-19

## Purpose of this section

The core entities of the domain, their attributes, and their relationships.
This is the shared vocabulary between backend, frontend, and the solver — if
it's not here, it doesn't exist in the app.

## Outline of content to fill

- [ ] Entities
  - [ ] **Nurse** — id, name, skills, contract (hours/week), availability
  - [ ] **Shift** — id, code (M/A/N), start time, end time, day offset
  - [ ] **Instance** — id, horizon (days), nurses, shifts, coverage
        requirements, metadata (seed, source)
  - [ ] **Schedule** — id, instance ref, assignments (nurse × day × shift),
        objective value, solver metadata
  - [ ] **Constraint** — id, kind (hard/soft), definition, weight
  - [ ] **Preference** — id, nurse ref, day(s), shift(s), direction
        (prefer/avoid), weight
- [ ] Relationships (mermaid ERD)
- [ ] Invariants (always-true properties across the model)

## Relevant prior art

- `docs/knowledge/nurse-scheduling/overview.md` — formal NSP definition +
  constraint taxonomy (HC-1..HC-N, SC-1..SC-N).
- INRC-II instance schema — reference for what real-world instances carry.
- `apps/shared/nsp-instance.schema.json` (created in Chapter 11) — keep this
  aligned with the eventual authoritative schema.

<!--
TO FILL IN CHAPTER 14:
- Start with an ASCII sketch, then upgrade to mermaid. A mermaid ERD block:
    ```mermaid
    erDiagram
      NURSE ||--o{ ASSIGNMENT : receives
      INSTANCE ||--o{ SCHEDULE : "is solved by"
      ...
    ```
- List invariants explicitly — "A nurse never works two shifts on the same day"
  is a model invariant, not a constraint. Invariants live in the types;
  constraints live in the solver.
- If an entity exists only transiently (e.g. "SolveJob" for the background
  queue), mark it as such and cross-reference 06-api-contract.md.
- Keep names consistent with what the solver code uses (`Nurse`, `Shift`,
  `Schedule`) — code-spec drift starts here.
-->
