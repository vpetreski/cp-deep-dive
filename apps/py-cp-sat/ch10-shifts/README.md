# Chapter 10 — Time, shifts, transitions (Python)

The bridge chapter between puzzle-solving and NSP. We introduce:

- A 3D boolean/integer grid over `(nurse, day, shift)`.
- Per-day "exactly one of {OFF, DAY, NIGHT}" via `OnlyEnforceIf` ladders.
- Coverage constraints per (day, shift).
- **Forbidden transitions** via `AddAutomaton` — the classic
  "no DAY shift right after a NIGHT shift" rule from real hospital rosters.
- A workload cap (max shifts per nurse per week).

Demo: 5 nurses x 7 days x {OFF, DAY, NIGHT}, coverage_min = 1.

## Run

```bash
# From apps/py-cp-sat/
uv sync --all-packages
uv run python -m py_cp_sat_ch10
```

## Test

```bash
# From apps/py-cp-sat/
uv run pytest ch10-shifts/ -v
```

## Exercises

See `src/py_cp_sat_ch10/solutions/`:

| File | Exercise |
|---|---|
| `exercise_10_1_rest_after_night.py` | Strengthen the automaton — NIGHT -> OFF the very next day |
| `exercise_10_2_coverage_bump.py` | Parametrize coverage_min and verify the solver still copes |
| `exercise_10_3_weekend_off.py` | At least one weekend day OFF per nurse |
| `exercise_10_4_infeasible.py` | Demonstrate INFEASIBLE when demand exceeds capacity |

See `docs/chapters/10-time-shifts-transitions.md` for the full teaching spec (when written).
