# ch04-puzzles — classic CP puzzles in Kotlin

Three canonical puzzles modeled with `cpsat-kt`:

1. **N-Queens** — the `AllDifferent(q)`, `AllDifferent(q[i]+i)`, `AllDifferent(q[i]-i)` trick.
2. **SEND + MORE = MONEY** — one `AllDifferent` + one linear equality.
3. **9×9 Sudoku** — three overlapping `AllDifferent` families (rows, columns, 3×3 boxes).

## Run

```bash
./gradlew :ch04-puzzles:run         # runs Main — prints boards + timings
./gradlew :ch04-puzzles:test        # runs the Kotest specs
```

### Individual solutions (exercises)

```bash
./gradlew :ch04-puzzles:run -PmainClass=io.vanja.cpsat.ch04.solutions.Ex0402CryptVariantsKt
```

The `solutions/` files have their own `main` entry points; use the classpath runner
from IntelliJ or `kotlinc -script`-style wrapping. For CLI simplicity they all live
under the same subproject and can be invoked via `java -cp build/install/... Ex0402...`.

## Files

| File | What it models |
|---|---|
| `NQueens.kt` | `solveNQueens(n)` — returns `NQueensResult(columns)`. |
| `SendMoreMoney.kt` | `solveSendMoreMoney()` — unique digit assignment. |
| `Sudoku.kt` | `solveSudoku(clues)` — 9×9 with `1..9` cells and three AllDifferent families. |
| `Main.kt` | Runs all three back-to-back with timings. |
| `solutions/Ex0401Symmetry.kt` | Exercise 4.5 — solution-count with/without `q[0] < q[n-1]`. |
| `solutions/Ex0402CryptVariants.kt` | Exercise 4.2 — SEND+MOST, CROSS+ROADS, TWO+TWO=FOUR enum. |
| `solutions/Ex0403ReifiedCrypt.kt` | Exercise 4.3 — reified `odd_e` + conditional `M+O≥5`. |

## Expected output (excerpt)

```
=== 1. N-Queens ===
  n=4 -> columns=[1, 3, 0, 2]
    . Q . .
    . . . Q
    Q . . .
    . . Q .
  (solved in 30ms)

  n=8 -> columns=[...]
  ...

=== 2. SEND + MORE = MONEY ===
    SEND  = 9567
  + MORE  = 1085
  -------
  = MONEY = 10652
  ...

=== 3. Classic Sudoku ===
  5 3 4 | 6 7 8 | 9 1 2
  6 7 2 | 1 9 5 | 3 4 8
  ...
```

## DSL features exercised

- `intVar` / `intVarList` with `IntRange` domains
- `allDifferent(Iterable<IntVar>)` and `allDifferent(Iterable<LinearExpr>)`
- `constraint { +(expr eq expr) }` (LinearExpr-to-LinearExpr equality)
- `solveBlocking { randomSeed = 42 }` for reproducible runs
- Exercise 4.1 uses `solveFlow { rawProto { enumerateAllSolutions = true } }`
- Exercise 4.3 uses `channelEq` + `enforceIf`
