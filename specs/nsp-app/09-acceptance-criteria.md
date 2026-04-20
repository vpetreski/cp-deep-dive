# 09 — Acceptance Criteria

> **Status:** placeholder — fill in Chapter 14
> **Last updated:** 2026-04-19

## Purpose of this section

The definitive "is it done?" checklist. Every FR from
`04-functional-requirements.md` and every NFR from
`05-non-functional-requirements.md` has at least one testable acceptance
criterion here. This is the artefact Vanja signs off against to cut v1.0.

## Outline of content to fill

- [ ] AC matrix (table: AC-NN | FR/NFR ref | criterion | how tested)
- [ ] Criteria grouped by family:
  - [ ] Instance import ACs
  - [ ] Solve lifecycle ACs
  - [ ] Schedule inspection ACs
  - [ ] Edit + re-validate ACs
  - [ ] Export ACs
  - [ ] Backend parity ACs (same instance → equivalent-quality schedules)
  - [ ] Performance ACs (map to NFR numbers)
  - [ ] Accessibility ACs (WCAG 2.1 AA checklist)
  - [ ] Security ACs (auth, input validation, logging)
- [ ] Final sign-off checklist
  - [ ] All MUST FRs pass
  - [ ] All performance NFRs pass on reference hardware
  - [ ] WCAG checks pass (automated + manual screen-reader walk-through)
  - [ ] Both backends pass integration tests against same instance
  - [ ] `docker compose up` reproduces the full stack end-to-end
  - [ ] README has a clear runbook

## Relevant prior art

- The test pyramid: unit tests in each `apps/*` + integration tests in
  `apps/shared/` + end-to-end tests running both stacks.
- `docs/plan.md` Chapter 15 "integration test: same instance, both backends
  produce equivalent-quality schedules" already committed.

<!--
TO FILL IN CHAPTER 14:
- Format for each AC:
    **AC-NN:** <statement>
    **Verifies:** FR-XX / NFR-YY
    **Tested via:** unit / integration / e2e / manual
- "Equivalent-quality" between backends deserves definition — exact assignment
  match is too strong (solver is non-deterministic); objective within ±5% or
  identical coverage is more realistic. Pick your definition here.
- Any AC that says "manual" should have a scripted walk-through — don't leave
  the signoff ambiguous.
- This file IS the v1.0 gate. If an AC can't be written for a feature, the
  feature isn't spec'd well enough — push back into 04 or 05.
-->
