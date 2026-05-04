---
name: First-class artifacts in cp-deep-dive
description: The non-obvious artifacts we're producing beyond learning chapters — cpsat-kt library, NSP spec, web app — with their role and publish plan
type: project
originSessionId: d0dadf92-e84d-4489-a081-925467834f50
---
Beyond the learning chapters, this repo produces three first-class artifacts:

1. **`libs/cpsat-kt/`** — idiomatic Kotlin DSL/wrapper over OR-Tools CP-SAT Java API. Designed as a standalone module with its own build, README, and tests. Used by every Kotlin example in the repo. Eventually publishable to Maven Central (coord: `io.vanja:cpsat-kt:X.Y.Z`, pre-1.0 until NSP app ships).

2. **`specs/nsp-app/`** — full markdown specification for the nurse-scheduling end-to-end application, written and locked before Phase 7 implementation.

3. **`apps/nsp-app/`** — the end-to-end NSP application itself: Python backend (FastAPI), Kotlin backend (Ktor 3), web frontend (Vite + React 19 + React Router v7 framework mode + Tailwind 4 + shadcn/ui), both backends solving the same spec with a shared OpenAPI contract.

**Why this matters:** `cpsat-kt` is what makes the "dual-language parity" rule tractable — without it, Kotlin examples would call Java directly and feel un-Kotlin. The spec is the agreement Vanja and Claude work from during the build phase. The app is the capstone deliverable.

**How to apply:**
- Always use `cpsat-kt` in Kotlin code, never raw `com.google.ortools.sat.*`.
- Reference the spec during app implementation; amend the spec if reality diverges.
- When advising on architecture, remember these are publishable artifacts — clean boundaries, no coupling to learning chapters.
