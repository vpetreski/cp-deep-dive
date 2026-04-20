# MiniZinc models (apps/mzn)

A small collection of MiniZinc models that mirror Python chapter code, so you
can solve the same problem declaratively and compare.

## Requirements

Install MiniZinc from <https://www.minizinc.org/software.html>. The included
Gecode solver and bundled OR-Tools CP-SAT backend are enough for every model
in this folder.

## Models

| File | What it is |
|---|---|
| `nqueens.mzn` + `nqueens.dzn` | N-Queens with three `alldifferent`s (Chapter 4) |
| `knapsack.mzn` + `knapsack.dzn` | 15-item 0/1 knapsack (Chapter 5) |
| `sendmore.mzn` | SEND + MORE = MONEY cryptarithmetic (Chapter 4) |
| `toy-nsp.mzn` + `toy-nsp.dzn` | 3 nurses × 7 days × 2 shifts mini-NSP (Chapter 8) |

## Run

```bash
# From apps/mzn/
./run.sh nqueens            # uses the default solver
./run.sh knapsack cp-sat    # solve knapsack via CP-SAT (if installed)
./run.sh sendmore           # cryptarithmetic
./run.sh toy-nsp gecode     # explicit Gecode
```

`run.sh` is a thin wrapper — it resolves `minizinc` from ``$PATH`` and reports a
short message if the binary isn't found.
