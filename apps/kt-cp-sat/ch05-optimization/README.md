# ch05-optimization — objectives, bounds, streaming incumbents

Three flavors of optimization, all through `cpsat-kt`:

1. **0/1 Knapsack** — canonical COP: `maximize Σ v·x` subject to `Σ w·x ≤ W`.
2. **Bin Packing** — integer decision + symmetry-breaking + multi-bin capacity.
3. **`solveFlow` streaming** — observe every incumbent as the solver improves it.

## Run

```bash
./gradlew :ch05-optimization:run    # Main — knapsack + bin-packing + streaming
./gradlew :ch05-optimization:test   # Kotest specs
```

## Files

| File | What it models |
|---|---|
| `Knapsack.kt` | `solveKnapsack(items, capacity)` with `weightedSum` + `maximize`. |
| `BinPacking.kt` | `solveBinPacking(items, capacity)` with `inBin[i,b]` + `exactlyOne` + `use[b]` booleans and a symmetry breaker. |
| `Streaming.kt` | `streamKnapsack(...)` returns `Flow<Incumbent>` via `solveFlow`. |
| `Main.kt` | Runs all three and prints timings. |
| `solutions/Ex0501EarlyStop.kt` | Ex 5.1 — compare time/gap cut-offs. |
| `solutions/Ex0502LexObjective.kt` | Ex 5.2 — lexicographic (primary+secondary) objective. |

## DSL features exercised

- `boolVarList`, `weightedSum`, `maximize`/`minimize`, `exactlyOne`
- `solveBlocking { randomSeed = 42; numSearchWorkers = 4 }`
- `solveFlow { maxSolutions = N; rawProto { relativeGapLimit = 0.05 } }`
- `lexicographic { primary; secondary }` + `solveLexicographic(stages)`
- `SolveResult.Optimal` / `Feasible` / `Infeasible` pattern matching
