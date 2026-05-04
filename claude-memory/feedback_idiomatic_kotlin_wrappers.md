---
name: Build idiomatic Kotlin wrappers over Java APIs
description: When a Kotlin project consumes a Java-only library, wrap it into a first-class Kotlin DSL/library — don't call Java directly from Kotlin examples
type: feedback
originSessionId: d0dadf92-e84d-4489-a081-925467834f50
---
Do not use Java-only APIs (e.g. `com.google.ortools.sat.CpModel`) directly from Kotlin code in examples or apps. Instead, build a full idiomatic Kotlin wrapper (DSL, extension fns, operator overloads, sealed result types, coroutines) as a first-class artifact of the project, then use that wrapper everywhere.

**Why:** Vanja wants full idiomatic Kotlin experience (operator overloads, DSL builders, coroutines, sealed classes, range sugar). Calling Java classes directly from Kotlin is noisy and loses Kotlin's ergonomics. The wrapper is also a potentially publishable artifact on its own.

**How to apply:**
- For this project specifically: `libs/cpsat-kt/` is our wrapper over OR-Tools CP-SAT Java API. Every Kotlin example/app/doc uses `cpsat-kt`, never raw Java directly (except one chapter where we show Java pain to motivate the wrapper).
- General rule: if a Kotlin project would otherwise call a Java-only library 3+ times, build a thin idiomatic Kotlin module for it first.
- Design the wrapper to be publishable standalone (clean module boundary, own build, own README).
