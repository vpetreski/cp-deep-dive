# Chapter 06 — Global-constraints tour (Python)

Hands-on vocabulary: one runnable example per global constraint.

## What this chapter shows

- `add_circuit` — TSP on 8 cities.
- `add_allowed_assignments` (Table) — nurse/ward/skill policy lookup.
- `add_element` — variable-index array lookup (cost minimum).
- `add_automaton` — "no more than 3 consecutive night shifts" as a DFA.
- `add_inverse` — tasks ↔ nurses bijection with a pinned forward edge.
- Manual lex-less-or-equal — the ortools Python binding at this version does not ship `add_lexicographic_less_equal`, so we post the boolean "still-equal prefix" encoding by hand; the count-reduction test in `tests/test_globals.py::test_lex_breaking_reduces_solution_count` confirms it breaks symmetry.
- `add_reservoir_constraint` — running-sum fence for a series of signed deltas.

## Run

```bash
# From apps/py-cp-sat/
uv sync --all-packages
uv run python -m py_cp_sat_ch06
```

## Test

```bash
# From apps/py-cp-sat/
uv run pytest ch06-globals/ -v
```

## Exercises

See `src/py_cp_sat_ch06/solutions/`:

| File | Exercise |
|---|---|
| `exercise_6_1_mtz_tsp.py` | TSP with MTZ sub-tour elimination vs `add_circuit` |
| `exercise_6_2_two_consecutive_nights.py` | Automaton bounded at 2 consecutive nights |
| `exercise_6_4_element_variable_values.py` | `add_element` with a variable values array |
| `exercise_6_5_lex_breaks_symmetry.py` | Ratio count check — lex collapses `n!` permutation twins |

See `docs/chapters/06-global-constraints-tour.md` for the full teaching spec.
