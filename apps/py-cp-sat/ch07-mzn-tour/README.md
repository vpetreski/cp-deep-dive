# Chapter 07 — MiniZinc tour (Python)

Run the shared MiniZinc models in `apps/mzn/` and confirm they agree with the
Python CP-SAT versions built in Chapters 4 and 5.

## What this chapter shows

- How to shell out to `minizinc` from Python via `subprocess`.
- Parsers that extract solver output into structured data (`value = N`, letter assignments).
- Parity checks between MiniZinc and OR-Tools CP-SAT on the same instance.
- Graceful degradation when MiniZinc isn't installed — the chapter still imports, tests skip, `main()` prints a banner.

## Prerequisites

Install MiniZinc: <https://www.minizinc.org/software.html>. Tests that call the
`minizinc` binary are skipped automatically when it isn't on `$PATH`.

## Run

```bash
# From apps/py-cp-sat/
uv sync --all-packages
uv run python -m py_cp_sat_ch07
```

If MiniZinc is absent you'll see a one-line banner; otherwise the Python code
runs each shared `.mzn` and checks parity with the CP-SAT twin.

## Test

```bash
# From apps/py-cp-sat/
uv run pytest ch07-mzn-tour/ -v
```

## Exercises

See `src/py_cp_sat_ch07/solutions/`:

| File | Exercise |
|---|---|
| `exercise_7_1_multi_solver.py` | Run `knapsack.mzn` with several MiniZinc backends |

See `docs/chapters/07-minizinc-tour.md` for the full teaching spec.
