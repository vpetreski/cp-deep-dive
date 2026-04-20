# Choco Solver — NSP stub

Placeholder for a future port of the Nurse Scheduling Problem to
[Choco Solver](https://choco-solver.org/), an academic / research-oriented
Java CP solver with excellent global constraint coverage.

## Status

Not implemented. Tracked in `docs/plan.md`; will be scheduled after the
CP-SAT NSP chapters (11-13) are complete.

## What to expect here

- Kotlin module using `org.choco-solver:choco-solver`
- Same instance format as `apps/py-cp-sat/` and `apps/kt-cp-sat/ch11..13`
- Side-by-side DSL comparison with `cpsat-kt`

## Why Choco?

- Different internals: classical AC-5 / domain-store propagation (as
  opposed to CP-SAT's lazy clause generation + learning).
- Rich library of global constraints (especially scheduling: `diffn`,
  `cumulative`, `geost`).
- Good for "explain why this is infeasible" workflows via its tracer.

See `docs/knowledge/ecosystem/` in the parent repo for the full
comparative notes (to be written).
