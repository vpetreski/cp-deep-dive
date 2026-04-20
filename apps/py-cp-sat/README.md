# py-cp-sat — Python chapter code

uv-managed workspace for the Python side of the `cp-deep-dive` learning ladder. Each
`chNN-*` subdirectory is its own workspace member, with an isolated `pyproject.toml`
and a `src/py_cp_sat_chNN/main.py` entry point.

Runs on Python 3.12+ with `ortools >= 9.15` and pytest.

## Setup

```bash
# Installs all workspace members + dev tools in a single venv at .venv/
uv sync
```

## Run a chapter

```bash
# Chapter 2 — Hello CP-SAT (only fully-working chapter as of scaffolding)
uv run python -m py_cp_sat_ch02

# Or equivalently
uv run python -m py_cp_sat_ch02.main
```

## Test

```bash
# Whole workspace
uv run pytest

# Just one chapter
uv run pytest ch02-hello/
```

## Lint + type-check

```bash
uv run ruff check .
uv run ruff format --check .
uv run mypy .
```

## Layout

```
apps/py-cp-sat/
├── pyproject.toml           <- workspace root (ruff/mypy/pytest config + dev deps)
├── .python-version          <- 3.12
├── ch02-hello/              <- fully working: solves 3x+2y=12, x+y<=5, maximize x+y
├── ch04-puzzles/            <- stub (N-Queens, SEND+MORE=MONEY)
├── ch05-optimization/       <- stub (knapsack, callbacks)
├── ch06-globals/            <- stub (Circuit, Table, Automaton tour)
├── ch09-jobshop/            <- stub (intervals, NoOverlap, Cumulative)
├── ch10-shifts/             <- stub (shift grid + automaton transitions)
├── ch11-nsp-v1/             <- stub (toy NSP, hard constraints only)
├── ch12-nsp-v2/             <- stub (NSP with soft constraints + fairness)
└── ch13-nsp-v3/             <- stub (INRC-II benchmark-scale + tuning)
```

Chapters not present: `ch01` (theory only, no code), `ch03` (Kotlin-only — we build
`cpsat-kt` there), `ch07`/`ch08` (MiniZinc).

See [`../../docs/plan.md`](../../docs/plan.md) for the full chapter map.
