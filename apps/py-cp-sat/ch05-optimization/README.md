# Chapter 05 — Optimization (Python)

Objectives, incumbent/bound tracking, and enumeration of all optimal subsets.

## What this chapter shows

- `model.maximize(...)` with a 0/1 knapsack (15-item jewelry shop).
- `CpSolverSolutionCallback` capturing every improving incumbent (wall, obj, bound).
- Bin packing formulation with a "bin-used" indicator plus symmetry breaking.
- Enumerate-all-optimal-subsets pattern: solve, pin objective as equality, drop the objective, enumerate.

## Run

```bash
# From apps/py-cp-sat/
uv sync --all-packages
uv run python -m py_cp_sat_ch05
```

Expected output skeleton:

```
=== 0/1 Knapsack (15 items, capacity=20) ===
Status: OPTIMAL
Value:  53
Bound:  53.0
Chosen: ['gold-ring', 'emerald', 'pearl-necklace', 'diamond', 'amber-stone', 'copper-coin']
Incumbent trace (wall, obj, bound):
  t=...  obj=...  bound=...
...
=== Bin Packing (n=10 items, capacity=10) ===
Status:   OPTIMAL
Bins:     3
  bin0 load=...
  ...
```

## Test

```bash
# From apps/py-cp-sat/
uv run pytest ch05-optimization/ -v
```

## Exercises

See `src/py_cp_sat_ch05/solutions/`:

| File | Exercise |
|---|---|
| `exercise_5_1_bounded_knapsack.py` | Bounded (``0..max_count``) per-item variant |
| `exercise_5_3_early_stop.py` | 5% gap vs proven-optimal tradeoff |
| `exercise_5_4_lex_objective.py` | Lex: max value, then min weight |
| `exercise_5_5_enumerate_optimal.py` | All optimal subsets |

See `docs/chapters/05-optimization-objectives-bounds.md` for the full teaching spec.
