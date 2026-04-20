# ch10-shifts — calendar-aware shift scheduling (NSP warm-up)

A compact 5-nurse × 7-day × 2-shift roster that introduces the four constraint
shapes every Nurse Scheduling Problem revisits: coverage, one-per-day,
calendar-aware transitions (here: no NIGHT then DAY), and workload bounds.
Chapters 11-13 scale exactly this model to the full NSP.

## Run

```bash
./gradlew :ch10-shifts:run    # Main — solves the demo, prints the calendar
./gradlew :ch10-shifts:test   # Kotest specs
```

## Files

| File | What it does |
|---|---|
| `ShiftModel.kt` | `solveShifts(instance)` — BoolVar grid + coverage + one-per-day + Night->Day rest + workload + spread objective. |
| `Main.kt` | Solves `DEMO_SHIFTS` and prints the calendar via `renderCalendar`. |
| `solutions/Ex1001MaxConsecutiveNights.kt` | Add a sliding-window cap on consecutive nights per nurse. |
| `solutions/Ex1002WeekendFairness.kt` | Add weekend-fairness objective (minimize weekendSpread, tiebreak on totalSpread). |

## Model at a glance

Decision variable: `x[n, d, s]` — BoolVar, 1 iff nurse `n` takes shift `s` on day `d`.

Constraints:

- Coverage: `sum_n x[n, d, s] >= coverage` for every `(d, s)`.
- One per day: `sum_s x[n, d, s] <= 1` for every `(n, d)`.
- Night->Day forbidden: `x[n, d, NIGHT] + x[n, d+1, DAY] <= 1`.
- Workload: `minWork <= sum_{d, s} x[n, d, s] <= maxWork`.

Objective: minimize `max(totals) - min(totals)` — fairness across nurses.

## DSL features exercised

- `cpModel { boolVar("name") }` with 3D arrays
- `atMostOne(list)` for per-nurse per-day slot
- `weightedSum(vars, coefs) ge / le / eq` for coverage and transitions
- Aux IntVars for `maxT`, `minT`, and `spread` (DSL has no `addMaxEquality`)
- `minimize { spread }`
- `solveBlocking { randomSeed; maxTimeInSeconds; numSearchWorkers }`
