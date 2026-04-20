# Chapter 04 — Classic puzzles (Python)

N-Queens, SEND+MORE=MONEY, and 9x9 Sudoku, all modeled with CP-SAT
`AllDifferent` constraints.

## What this chapter shows

- `AllDifferent` on a derived expression (``q[i] + i``) for diagonals.
- Half-reification via `only_enforce_if` (Exercise 4.3).
- Symmetry-breaking for enumeration speed (Exercise 4.5).
- The 27-`AllDifferent` Sudoku formulation.

## Run

```bash
# From apps/py-cp-sat/
uv sync --all-packages
uv run python -m py_cp_sat_ch04
```

Expected first lines:

```
=== N-Queens (n=8) ===
Status: OPTIMAL
Queen columns -> rows: [0, 4, 7, 5, 2, 6, 1, 3]
...
Total distinct solutions: 92
...
=== SEND + MORE = MONEY ===
Status: OPTIMAL
  S = 9
  E = 5
  ...
  9567 + 1085 = 10652
```

## Test

```bash
# From apps/py-cp-sat/
uv run pytest ch04-puzzles/ -v
```

## Exercises

See `solutions/`:

| File | Exercise |
|---|---|
| `exercise_4_1_scaling.py` | N-Queens wall-time vs N |
| `exercise_4_2_two_two_four.py` | Enumerate TWO+TWO=FOUR |
| `exercise_4_3_reified_send_more.py` | Reified `E is odd` implication |
| `exercise_4_5_symmetry_breaking.py` | `q[0] < q[n-1]` cuts search |

See `docs/chapters/04-classic-puzzles.md` for the full teaching spec.
