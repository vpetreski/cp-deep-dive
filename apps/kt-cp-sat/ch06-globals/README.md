# ch06-globals — a tour of CP-SAT's global constraints

Six tiny models, one per constraint.

## Run

```bash
./gradlew :ch06-globals:run    # Main — runs all 6 demos in order
./gradlew :ch06-globals:test   # Kotest specs
```

## Files

| File | Constraint | What it demonstrates |
|---|---|---|
| `AllDifferentDemo.kt` | `allDifferent` | Pick n distinct values that hit a target sum. |
| `ElementDemo.kt` | `element` | `values[index] = target` — indexing a constant array by a variable. |
| `TableDemo.kt` | `table` | Enumerate allowed (day, shift, nurse) tuples. |
| `CircuitTsp.kt` | `circuit` | Minimal 8-city TSP on a regular octagon. |
| `AutomatonDemo.kt` | `automaton` | "No 4 consecutive nights" encoded as a DFA. |
| `InverseDemo.kt` | `inverse` | Two arrays that are each other's permutation inverse. |
| `Main.kt` | — | Runs each demo with timings. |
| `solutions/Ex0601EnumerateTours.kt` | Ex 6.1 | Stream up to N TSP incumbents via `solveFlow`. |
| `solutions/Ex0602CostlyAssignment.kt` | Ex 6.2 | Minimum-cost nurse↔shift assignment via `inverse` + `element`. |
| `solutions/Ex0603NoThreeSame.kt` | Ex 6.3 | 3-symbol automaton — no 3 consecutive DAYs or NIGHTs. |

## DSL features exercised

- `allDifferent(vars)` (+ expression overload for diagonals)
- `element(index, LongArray, target)`
- `table(vars, List<LongArray>)`
- `circuit(Iterable<Triple<Int, Int, BoolVar>>)`
- `automaton(vars, startState, transitions, finalStates)`
- `inverse(f, g)`
- `channelEq(bool, expr, value)` (in Ex 6.3)
- `weightedSum`, `minimize`, `solveFlow`
