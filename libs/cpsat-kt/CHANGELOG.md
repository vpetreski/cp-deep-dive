# Changelog

All notable changes to `cpsat-kt` are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions use
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2026-04-19

Initial release. API is experimental -- expect breaking changes in 0.x.

### Added
- `CpModel` + `cpModel { }` top-level builder with `toJava()` escape hatch.
- `IntVar`, `BoolVar`, and factory functions (`intVar`, `boolVar`,
  `intVarList`, `boolVarList`, `constant`) with `IntRange`, `LongRange`,
  and sparse `Iterable<Long>` domain overloads.
- `LinearExpr` sealed interface with operator overloading (`+`, `-`, `*`,
  unary `-`) and `sum` / `weightedSum` helpers.
- `Relation` + `RelOp` with infix `eq`, `neq`, `le`, `lt`, `ge`, `gt`.
- `constraint { }` builder that collects `Relation`s and emits them as
  CP-SAT constraints, with support for `onlyEnforceIf`.
- Global constraints: `allDifferent`, `exactlyOne`, `atMostOne`,
  `atLeastOne`, `boolOr`, `boolAnd`, `implies`, `element`, `inverse`,
  `table`, `forbidden`, `circuit`, `automaton`, `noOverlap`, `cumulative`,
  `reservoir`, `lexLeq`, `channelEq`.
- Reification helpers: `enforceIf`, `enforceIfAll`, `enforceIfAny`.
- `IntervalVar` + `interval { }` and `optionalInterval { }` builders.
- Objectives: `minimize { }`, `maximize { }`, and lexicographic solving
  via `lexicographic { }` + `solveLexicographic(...)`.
- `SolverParams` DSL with typed fields and a `rawProto` escape hatch for
  arbitrary `SatParameters` tweaks.
- Sealed `SolveResult` (`Optimal`, `Feasible`, `Infeasible`, `Unknown`,
  `ModelInvalid`) + `Assignment` with `operator fun get` for every
  variable type.
- Blocking `solveBlocking(...)`, suspending `solve(...)`, and streaming
  `solveFlow(...)` (returns `Flow<Solution>`).
- Native-library loader that runs automatically on first use.

[0.1.0]: https://github.com/vanjap/cp-deep-dive/releases/tag/v0.1.0
