# ch09-jobshop — interval-based scheduling with `noOverlap` + Gantt SVG

Classic Job-Shop Scheduling Problem (JSSP): a set of jobs each made of a
strict sequence of operations on fixed machines, minimize makespan.

## Run

```bash
./gradlew :ch09-jobshop:run    # Main — solves the 3x3 demo, writes build/ch09-gantt.svg
./gradlew :ch09-jobshop:test   # Kotest specs
```

Open the generated SVG in a browser to view the schedule:

```bash
open apps/kt-cp-sat/ch09-jobshop/build/ch09-gantt.svg
```

## Files

| File | What it does |
|---|---|
| `JobShop.kt` | `solveJobShop(instance)` — interval per op, precedence, `noOverlap` per machine, makespan aux. |
| `Gantt.kt` | Self-contained SVG rendering of a solved schedule. |
| `Main.kt` | Solves the demo and writes the SVG. |
| `solutions/Ex0901Cumulative.kt` | Ex 9.1 — swap `noOverlap` for `cumulative` on one machine (capacity 2). |
| `solutions/Ex0902OptionalOps.kt` | Ex 9.2 — make the last op optional; trade skipping for penalty. |

## DSL features exercised

- `interval("name") { start = ...; size = ... }`
- `optionalInterval("name", presenceBool) { ... }`
- `noOverlap(intervals)` / `cumulative(intervals, demands, capacity)`
- `enforceIf(bool) { +(relation) }`
- Makespan via aux variable `makespan` plus `end ≤ makespan` for each interval.
