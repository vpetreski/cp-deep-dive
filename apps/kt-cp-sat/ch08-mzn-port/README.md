# ch08-mzn-port — port of the toy NSP from MiniZinc to `cpsat-kt`

Same instance as `apps/mzn/toy-nsp.mzn`; same three hard constraints; same
objective. The Kotlin port reads 1:1 against the declarative MZN — this
chapter is the "here's the same model in both languages" proof.

## Run

```bash
./gradlew :ch08-mzn-port:run    # Main — prints the grid and spread
./gradlew :ch08-mzn-port:test   # Kotest specs (parity check skips if minizinc missing)
```

## Files

| File | What it does |
|---|---|
| `ToyNsp.kt` | `solveToyNsp(instance)` — HC-1, HC-2, HC-3, minimize `max(totals) - min(totals)`. |
| `Main.kt` | Runs the demo instance (3 nurses x 7 days x 2 shifts) and prints the grid. |
| `solutions/Ex0801WeekendOff.kt` | Ex 8.1 — cap each nurse's weekend slots. |
| `solutions/Ex0802BiggerInstance.kt` | Ex 8.2 — how does the spread change as workload bounds tighten? |

## Parity

`ParitySpec` invokes the MiniZinc CLI (via the runner from `ch07-mzn-bridge`)
and compares its optimum spread with the one `solveToyNsp` reports. The test
silently passes when `minizinc` isn't on PATH, so the rest of the suite still
runs on machines without MZN.

## What the port illustrates

- `cpModel { ... }` as the declarative surface.
- `exactlyOne` + `atMostOne` as a compact HC-1/HC-2 encoding.
- `weightedSum` for totals.
- `intVar` auxiliaries (`maxT`, `minT`) to model `max - min` as a linear expression.
- `minimize { spread }`.
