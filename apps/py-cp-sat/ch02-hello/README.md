# Chapter 2 — Hello, CP-SAT (Python)

Solves the tiny linear feasibility-plus-objective model from the chapter:

```
3x + 2y = 12
x + y   <= 5
x, y    in [0, 10]
maximize x + y
```

## Run

```bash
uv run python -m py_cp_sat_ch02
```

Expected output:

```
Status: OPTIMAL
x = 2, y = 3, objective = 5
```

## Test

```bash
uv run pytest ch02-hello/
```
