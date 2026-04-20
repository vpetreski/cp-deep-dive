# alt-solver/timefold — NSP via Timefold Solver

A port of the Nurse Scheduling Problem (NSP) to [Timefold
Solver](https://timefold.ai/) `1.16.0`, the Apache-2.0 fork of OptaPlanner.
Built for the Ch18 ecosystem-port retrospective in this repo: it's how we
compare CP-SAT's constraint-programming view against Timefold's local-search
metaheuristic view on the same instance format.

## Scope

Ch18 comparability set (documented in
[`docs/chapters/18-ecosystem-port-retrospective.md`](../../../docs/chapters/18-ecosystem-port-retrospective.md)):

| Constraint | Meaning                                |
|------------|----------------------------------------|
| HC-1       | Coverage (exactly 1 nurse per slot)    |
| HC-2       | One shift per nurse per day            |
| HC-3       | Forbidden transitions                  |
| HC-4       | Max consecutive working days (5)       |
| HC-5       | Max consecutive nights (3)             |
| SC-1       | Preference honoring (avoid / day-off)  |
| SC-2       | Fairness (min/max shift-count spread)  |

Cut for Ch18: **HC-6** (min rest hours — collapsed into HC-3 via
`forbiddenTransitions` at load time), **HC-7** (skill match — toys are
single-skill), **HC-8** (contract hours — requires duration arithmetic
that doesn't pay its way in the paradigm comparison).

## Layout

```
src/main/kotlin/io/vanja/altsolver/timefold/
├── Instance.kt                 <- kotlinx.serialization mirror of data/nsp/schema.json
├── Domain.kt                   <- @PlanningEntity, @PlanningSolution, facts
├── NspConstraintProvider.kt    <- ConstraintStreams for HC-1..HC-5 + SC-1..SC-2
├── SolutionFactory.kt          <- InstanceJson -> NspSolution (+ plumbs preferences)
├── Output.kt                   <- ScheduleAssignment record + grid renderer
└── Main.kt                     <- CLI + reusable `solveNsp(...)`
```

## Requirements

- JDK 25
- Gradle 9.4.1 (via wrapper)
- Kotlin 2.3.20

## Run

```bash
# From this directory:
./gradlew run --args="../../../data/nsp/toy-01.json 30"
./gradlew run --args="../../../data/nsp/toy-02.json 30"
```

The CLI prints:
1. Instance shape (nurses, days, shifts, slots)
2. Time limit + random seed (fixed at `42` for reproducibility)
3. Final Timefold score `HhHARD/SsSOFT`
4. Feasibility + wall time
5. A nurse-by-day grid (unassigned cells show as `.`)

Example output on `toy-01`:

```
Score: 0hard/-1soft
Feasible: true   Wall time: 5006ms

nurse  d00 d01 d02 d03 d04 d05 d06
N1     D   N   .   D   N   N   .
N2     N   .   D   N   .   .   D
N3     .   D   N   .   D   D   N
```

## Test

```bash
./gradlew test
```

Three specs: `Toy01SolveSpec`, `Toy02SolveSpec`, `ConstraintSemanticsSpec`.
Solver specs use reduced time budgets (3 s / 8 s / 15 s) to keep CI fast
while still giving local search room to converge on toy instances.

## Design notes

### Companion state for solver-lifetime data

`ConstraintProvider` is instantiated reflectively by Timefold, so per-solve
configuration (the `forbiddenTransitions` set, the preference table) can't
live as instance state. We plumb it on the companion object via
`NspConstraintProvider.installForbiddenTransitions(...)` and
`registerPreference(...)`, populated in `NspSolutionFactory.build(...)`.

The textbook-correct alternative is a `@ConstraintConfiguration` bean
attached to the solution. We skip it here because it pulls in another class
and doesn't change the paradigm comparison — the Ch18 retrospective notes
this as a follow-up cleanup.

### SC-2 fairness pipeline

Two-step groupBy reduces `(nurse, count)` to `(minCount, maxCount)` and then
penalises `max - min`:

```kotlin
f.forEach(ShiftAssignment::class.java)
    .groupBy(ShiftAssignment::nurse, ConstraintCollectors.count())
    .groupBy(
        ConstraintCollectors.min { _: Nurse?, c: Int -> c },
        ConstraintCollectors.max { _: Nurse?, c: Int -> c },
    )
    .penalize(HardSoftScore.ONE_SOFT) { lo, hi -> hi - lo }
    .asConstraint("SC-2 shift-count fairness")
```

`loadBalance` + `LoadBalance.unfairness()` gives a smoother fairness
signal but returns `BigDecimal`, which doesn't fit cleanly in
`HardSoftScore` (`HardSoftBigDecimalScore` would work but multiplies
boilerplate). Spread is good enough for a Ch18-scale comparison.
