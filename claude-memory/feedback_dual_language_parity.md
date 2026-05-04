---
name: Dual Python + Kotlin parity in every example, every doc
description: Every code example, chapter, and doc ships in both Python AND Kotlin (via the cpsat-kt wrapper) — no single-language chapters
type: feedback
originSessionId: d0dadf92-e84d-4489-a081-925467834f50
---
Every example, chapter, and documentation artifact in this project must have both a Python version AND a Kotlin version (using our `cpsat-kt` wrapper library for Kotlin). No chapter is complete with only one language.

**Why:** Comparative learning is the stated goal of the project. The whole point is internalizing the solver's mental model, not one language's API — the dual-language contrast is what makes that stick.

**How to apply:**
- Every chapter folder has `apps/py-*/chNN-*/` AND `apps/kt-*/chNN-*/`.
- Every knowledge doc that shows code shows it in both Python and Kotlin side-by-side.
- Kotlin code always uses `cpsat-kt`, not raw Java OR-Tools API.
- If a chapter temporarily only fits one language (rare), explicitly note the port-to-other is pending.
