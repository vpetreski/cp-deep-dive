# 02 — User Stories

> **Status:** placeholder — fill in Chapter 14
> **Last updated:** 2026-04-19

## Purpose of this section

The primary user flows expressed as Given/When/Then stories, grouped by persona
and prioritized. Every functional requirement in `04-functional-requirements.md`
should trace back to at least one story here.

## Outline of content to fill

- [ ] Personas (named + short bio: e.g. "Sam the Scheduler", "Nina the Nurse
      Manager")
- [ ] User stories in Given/When/Then format, grouped by persona
  - [ ] Create/upload an NSP instance
  - [ ] Configure constraints + preferences
  - [ ] Run a solve + observe progress
  - [ ] Inspect the resulting schedule (coverage, fairness, preference
        satisfaction)
  - [ ] Manually edit and re-validate a cell
  - [ ] Compare two schedules or two solver runs
  - [ ] Export the schedule (PDF / ICS / CSV)
- [ ] Priority ranking (MUST / SHOULD / COULD for v1.0)

## Relevant prior art

- `docs/knowledge/nurse-scheduling/overview.md` for realistic nurse-manager
  personas and real-world flows.
- INRC-I / INRC-II competition descriptions for what "running a solve" looks
  like operationally.

<!--
TO FILL IN CHAPTER 14:
- One story per user-facing capability. Format:
    **US-NN:** As a <persona>, I want to <action>, so that <outcome>.
    - **Given** <precondition>
    - **When** <action>
    - **Then** <observable result>
- Avoid internal/infra stories here — this is user-facing only. Solver internals
  go to 04-functional-requirements.md.
- Each story must be achievable in one user session (minutes, not days).
- Prioritize ruthlessly. v1.0 probably has <10 MUST stories.
-->
