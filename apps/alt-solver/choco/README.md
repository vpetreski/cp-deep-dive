# alt-solver/choco - NSP via Choco Solver

A port of the Nurse Scheduling Problem (NSP) to
[Choco Solver](https://choco-solver.org/) `4.10.18`, an academic /
research-oriented Java CP solver with excellent global-constraint coverage.
Built for the Ch18 ecosystem-port retrospective: it's how we compare CP-SAT's
lazy-clause-generation search against Choco's classical AC-5 / domain-store
propagation on the same instance format.

## Scope

Ch18 comparability set (documented in
[`docs/chapters/18-ecosystem-port-retrospective.md`](../../../docs/chapters/18-ecosystem-port-retrospective.md)):

| Constraint | Meaning                                |
|------------|----------------------------------------|
| HC-1       | Coverage (per-slot min/max)            |
| HC-2       | One shift per nurse per day            |
| HC-3       | Forbidden transitions                  |
| HC-4       | Max consecutive working days (5)       |
| HC-5       | Max consecutive nights (3)             |
| SC-1       | Preference honoring (avoid / day-off)  |
| SC-2       | Fairness (min/max shift-count spread)  |

Cut for Ch18 (same as the Timefold port): **HC-6** (min rest hours - collapsed
into HC-3 via `forbiddenTransitions` at load time), **HC-7** (skill match -
toys are single-skill), **HC-8** (contract hours - requires duration
arithmetic that doesn't pay its way in the paradigm comparison).

## Layout

```
src/main/kotlin/io/vanja/altsolver/choco/
|-- Instance.kt    <- kotlinx.serialization mirror of data/nsp/schema.json
|-- Model.kt       <- Choco Model + constraints for HC-1..HC-5 + SC-1..SC-2
`-- Main.kt        <- CLI entry, JSON schedule writer, solver loop
```

## Requirements

- JDK 25
- Gradle 9.4.1 (via wrapper)
- Kotlin 2.3.20

## Run

From this directory:

```bash
./gradlew run --args="--instance ../../../data/nsp/toy-01.json --time-limit 30"
./gradlew run --args="--instance ../../../data/nsp/toy-02.json --time-limit 30"
```

The CLI prints:

- A JSON schedule on stdout matching
  [`apps/shared/schemas/schedule.schema.json`](../../shared/schemas/schedule.schema.json)
  - so you can pipe it into any downstream validator or the benchmark harness.
- Diagnostics (instance shape, time limit, objective, wall time) on stderr
  so stdout stays pure.

Example stderr:

```
Instance: toy-01  (7d x 3n x 2s)
Time limit: 10s
Feasible: true   Objective: 0
Wall time: 223ms
```

Example stdout (truncated):

```json
{
  "instanceId": "toy-01",
  "generatedAt": "2026-04-19T20:00:00Z",
  "assignments": [
    { "nurseId": "N2", "day": 0, "shiftId": "D" },
    ...
  ]
}
```

## Test

```bash
./gradlew test
```

Two specs: `Toy01SolveSpec` and `Toy02SolveSpec`. Each verifies feasibility
plus HC-2/HC-3/HC-4/HC-5 semantics on the toy instances.

## Design notes

### Model encoding

Each `(nurse, day)` pair is a single `IntVar` in `0..S`, where indices `0..S-1`
code shift ids and `S` codes "off". HC-2 ("one shift per nurse per day") is
structural - a single variable can't take two values - so no constraint is
needed for it. Two auxiliary `BoolVar` arrays (`working` and `isNight`) drive
HC-4 and HC-5 via sliding-window `sum(...) <= k` constraints.

### HC-1 via `count`

Choco's global `count(value, vars, resultVar)` sets `resultVar` to the number
of `vars` equal to `value`; combined with an `IntVar` bounded to `min..max`,
it models the coverage requirement in a single constraint per `(day, shift)`.
This is the main place the paradigm difference shows up - CP-SAT models the
same rule as a sum of booleans.

### Soft objective

The objective is the sum of SC-1 preference penalties plus SC-2 fairness
spread (`max_total - min_total` across nurses). Penalties are materialised as
`IntVar`s via `times(violationBool, weight, penaltyVar)` so Choco can treat
the whole thing as a single `IntVar` to minimise.

### Termination

`solver.limitTime("Ns")` caps wall-clock time. We loop `solver.solve()`
manually (rather than calling `findOptimalSolution`) so we can record the
best incumbent if the budget fires before optimality is proven - the loop
drops out when Choco stops finding better solutions, which the incremental
objective-update builds in automatically.
