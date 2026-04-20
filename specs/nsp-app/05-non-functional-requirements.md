# 05 — Non-Functional Requirements

> **Status:** placeholder — fill in Chapter 14
> **Last updated:** 2026-04-19

## Purpose of this section

The qualities the system must have — performance, accessibility, security,
observability, data retention. NFRs are testable: each must have a target
number or a pass/fail check.

## Outline of content to fill

- [ ] **Performance targets**
  - [ ] Solve wall-clock budgets:
    - Toy instance (5 nurses × 14 days): first feasible ≤ 2s, optimal ≤ 30s
    - INRC-II mid-size (30–50 nurses × 4 weeks): first feasible ≤ 30s, good
      solution ≤ 5min
  - [ ] API p95 for non-solve endpoints ≤ 200ms
  - [ ] Web TTI (time-to-interactive) on roster-grid screen ≤ 2s on a
        mid-range laptop
- [ ] **Scalability**
  - [ ] Simultaneous solves per backend instance (throughput target)
  - [ ] Largest instance shape we commit to supporting
- [ ] **Accessibility**
  - [ ] WCAG 2.1 AA for the web app (color contrast, keyboard navigation,
        screen-reader labels on schedule grid)
- [ ] **Security**
  - [ ] API key auth on all non-public endpoints
  - [ ] Input validation (file size ceiling, schema validation before solve)
  - [ ] No secrets in logs
- [ ] **Observability**
  - [ ] OpenTelemetry traces for solve lifecycle
  - [ ] Structured JSON logs with request IDs
  - [ ] Solver search log captured per run
- [ ] **Data retention**
  - [ ] Instance and schedule records retained ≥ 30 days (local SQLite
        baseline); policy for production later

## Relevant prior art

- `docs/plan.md` §2 Application stack — OpenTelemetry + structured JSON logs
  are already chosen.
- `docs/knowledge/cp-sat/overview.md` for realistic solver timing expectations
  on NSP-shaped instances.

<!--
TO FILL IN CHAPTER 14:
- Every NFR must be testable. Use concrete numbers, not adjectives.
- Cross-link to ACs in 09-acceptance-criteria.md — each NFR has at least one
  automated or manual check.
- Don't over-engineer v1.0 NFRs. This is a teaching app. Reasonable defaults
  beat hardening we can't justify.
- Security baseline: API key is enough for v1.0. OAuth/SSO goes on the
  non-goals list in 01-vision-and-goals.md if we don't intend to build it.
-->
