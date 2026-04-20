---
name: Workflow — scaffolding-first, then guided chapters
description: How Vanja and Claude collaborate through the learning phases, starting from README and working through chapters interactively
type: project
originSessionId: d0dadf92-e84d-4489-a081-925467834f50
---
Collaboration pattern for this project:

1. **Plan locked first.** Vanja reviews `docs/plan.md`, iterates with Claude, gives explicit green light. No chapter work starts before lock-in.
2. **Claude scaffolds everything fully after lock-in.** Before Vanja touches Chapter 1, the full repo skeleton exists: `libs/cpsat-kt/` with designed API + stubs, `apps/*/` with chapter folders ready, `docs/chapters/NN-*.md` with full explanations and exercises, `specs/nsp-app/` structure.
3. **Vanja works chapter-by-chapter starting at `README.md`.** README is the navigation hub. It links to plan → chapters → knowledge → code.
4. **Per-chapter flow:** read chapter MD → do exercises (Python + Kotlin) → ask Claude questions → commit → self-check → next chapter.
5. **Claude monitors, teaches, updates.** As Vanja works, Claude answers questions, reviews code, updates docs/plan/chapter files to reflect what was actually learned vs. what was planned, marks chapters done in `plan.md`.

**How to apply:**
- Don't start building any chapter content/code until Vanja says "locked" or "green light."
- After green light, scaffold everything in one pass before Vanja opens README.
- Keep README as the canonical entry point; every navigational edit goes there.
- When Vanja completes a chapter, mark it done in `plan.md` and (if something new was learned) add to relevant `docs/knowledge/<area>/overview.md`.
