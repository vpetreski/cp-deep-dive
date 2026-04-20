# Chapter 08 — MiniZinc prototype, then Python port (Python)

Prototype a tiny NSP in MiniZinc, then port it to CP-SAT and confirm parity.

## What this chapter shows

- A 3-nurse × 7-day × 2-shift toy NSP covering HC-1 (coverage), HC-2 (single-shift-per-day), HC-3 (workload window).
- Objective: minimize ``max(totals) - min(totals)`` — the classic "fair spread" target.
- A Python port side-by-side with `apps/mzn/toy-nsp.mzn`.
- Parity test (skipped when MiniZinc isn't installed).

## Run

```bash
# From apps/py-cp-sat/
uv sync --all-packages
uv run python -m py_cp_sat_ch08
```

## Test

```bash
# From apps/py-cp-sat/
uv run pytest ch08-mzn-prototype/ -v
```

## Exercises

See `src/py_cp_sat_ch08/solutions/`:

| File | Exercise |
|---|---|
| `exercise_8_1_larger_instance.py` | Scale nurses/days/shifts up and resolve |
| `exercise_8_2_tight_workload.py` | Demonstrate infeasibility when demand exceeds capacity |

See `docs/chapters/08-minizinc-prototype-then-port.md` for the full teaching spec.
