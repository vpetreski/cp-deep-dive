# Timefold Solver — NSP stub

Placeholder for a future port of the Nurse Scheduling Problem to
[Timefold Solver](https://timefold.ai/) (the OptaPlanner fork), to compare
the constraint-streams / local-search approach against CP-SAT's
constraint-propagation approach.

## Status

Not implemented. Tracked in `docs/plan.md`; will be scheduled after the
CP-SAT NSP chapters (11-13) are complete.

## What to expect here

- Kotlin module using `timefold-solver-core` + `timefold-solver-constreams`
- Same instance format as `apps/py-cp-sat/` and `apps/kt-cp-sat/ch11..13`
- Benchmark harness comparing wall-clock + solution quality vs. CP-SAT

## Why Timefold?

- Different paradigm: local search with construction heuristics, not
  complete search. Good for huge instances where CP-SAT runs out of time.
- Java/Kotlin-first API, generates explainable "constraint violation" output.
- Widely used in enterprise scheduling.

See `docs/knowledge/ecosystem/` in the parent repo for the full
comparative notes (to be written).
