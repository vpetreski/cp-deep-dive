---
name: Spec-driven development for non-trivial apps
description: Before implementing any end-user application, write and lock a full markdown specification in a dedicated specs/ folder
type: feedback
originSessionId: d0dadf92-e84d-4489-a081-925467834f50
---
Non-trivial applications must have a full spec written before implementation begins. The spec lives in a dedicated `specs/<app-name>/` folder as structured markdown (overview, goals, user stories, domain model, API contract, UI/UX, acceptance criteria). The spec is reviewed, iterated, and locked before code is written. Both Vanja and Claude reference the spec during implementation.

**Why:** Vanja strongly believes in spec-driven development. Specs reduce rework, clarify scope, and give both humans and agents a shared source of truth. "Figuring it out as we code" for an app is explicitly rejected.

**How to apply:**
- Before Phase 7 (app implementation) in this project, write `specs/nsp-app/` with sub-files: overview, vision/goals, user stories, domain model, functional requirements, non-functional requirements, API contract, UI/UX, data model, acceptance criteria.
- Lock the spec via explicit Vanja approval before writing app code.
- During implementation, if the spec is wrong, update the spec first, then the code.
- Never write app code that contradicts the locked spec without a spec amendment.
