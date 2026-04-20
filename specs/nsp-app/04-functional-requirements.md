# 04 — Functional Requirements

> **Status:** placeholder — fill in Chapter 14
> **Last updated:** 2026-04-19

## Purpose of this section

The numbered list of functional requirements the app must satisfy. Each FR is
traceable to one or more user stories in `02-user-stories.md` and to one or
more acceptance criteria in `09-acceptance-criteria.md`.

## Outline of content to fill

- [ ] FR-1..FR-N as a numbered list
- [ ] Each FR has:
  - **Description** — what the system shall do
  - **Rationale** — why this is needed (story link)
  - **Acceptance link** — pointer to matching AC in `09-acceptance-criteria.md`
  - **Priority** — MUST / SHOULD / COULD
- [ ] Coverage families (suggested starting list):
  - [ ] Instance import (JSON upload, built-in samples, INRC-II parse)
  - [ ] Constraint configuration (toggle hard, weight soft)
  - [ ] Solve lifecycle (start, observe progress, cancel, restart, hint)
  - [ ] Schedule inspection (grid view, coverage KPI, fairness KPI,
        preference satisfaction)
  - [ ] Manual edit → re-validate → re-solve flow
  - [ ] Export (PDF, CSV, ICS)
  - [ ] Backend toggle (Python vs Kotlin)
  - [ ] Basic auth (API key)

## Relevant prior art

- `docs/knowledge/nurse-scheduling/overview.md` for the NSP constraint families
  (HC + SC) that inform the solver-facing FRs.
- `docs/plan.md` Chapter 15–17 for the implementation-side scope already
  discussed.

<!--
TO FILL IN CHAPTER 14:
- Format for each entry:
    ### FR-NN: <Short title>
    **Description:** The system shall <verb> <object> <conditions>.
    **Rationale:** Supports US-XX.
    **Acceptance:** AC-YY.
    **Priority:** MUST
- Be precise. "Fast" is not a functional requirement (it's an NFR). "Solve
  completes and returns a schedule within 30s for toy instances" is a mixed FR
  + NFR — split it: FR = "returns a schedule", NFR = "within 30s".
- Keep it flat. Nested FRs (FR-4.1.2) lead to tangled ACs. Prefer more
  numbered entries.
- v1.0 probably has 20–30 FRs. If you're hitting 50, scope is too big.
-->
