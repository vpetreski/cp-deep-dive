# ch07-mzn-bridge — drive MiniZinc from Kotlin

A compact wrapper around the `minizinc` CLI. The models themselves live in
`apps/mzn/` so the Python and Kotlin chapters can share them — this module
just shells out, parses the block-structured stdout, and returns a typed
result.

## Prerequisites

Install MiniZinc (`brew install minizinc`, or download from
https://www.minizinc.org/software.html). If the binary is missing, every
`MiniZincRunner.run` call returns `MzStatus.UNAVAILABLE` and `Main` prints a
helpful note. Tests gracefully skip the live runs.

## Run

```bash
./gradlew :ch07-mzn-bridge:run    # Main — runs nqueens, knapsack, toy-nsp through MZN
./gradlew :ch07-mzn-bridge:test   # Kotest specs (parser tests always run; live tests skip if needed)
```

## Files

| File | What it does |
|---|---|
| `MiniZincRunner.kt` | `MiniZincRunner.run(model, data)` → `MiniZincResult`; also exposes a pure `parse` for fixture-driven tests. |
| `Main.kt` | Runs the three shared `apps/mzn` models and prints the last solution block. |
| `solutions/Ex0701SolverComparison.kt` | Ex 7.1 — try cp-sat / gecode / chuffed on the same model. |
| `solutions/Ex0702InlineModel.kt` | Ex 7.2 — generate a `.mzn` + `.dzn` on the fly and run it. |

## What it parses

- `----------` ends a solution block (one "incumbent").
- `==========` declares the previous solution optimal.
- `=====UNSATISFIABLE=====` / `=====UNKNOWN=====` map to the corresponding `MzStatus`.
- Anything else goes into the current block buffer verbatim.

Callers can split a block into `key=value` pairs with `parseKeyValues`, which
is enough for the toy MZN models we ship.

## Scope

This is a teaching wrapper, not a production bridge. For real use you'd want
`minizinc-python` or to round-trip FlatZinc through CP-SAT directly.
