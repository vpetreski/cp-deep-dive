# Chapter 09 — Intervals, NoOverlap, Cumulative — Job-Shop (Python)

Job-Shop Scheduling with `IntervalVar` + `AddNoOverlap` — the first chapter
where we stop reasoning about integer time points and start reasoning about
*intervals* that occupy machines.

## What this chapter shows

- One `IntervalVar` per operation (`start`, `duration`, `end`).
- `AddNoOverlap` per machine — CP-SAT's disjunctive propagator.
- Precedence inside a job via simple `end_of(op_i) <= start_of(op_{i+1})`.
- `AddMaxEquality` to define the makespan, then `minimize` it.
- A matplotlib Gantt chart rendered to PNG.

Two demo instances:
- **3x3 textbook** — optimum makespan = 11.
- **5x4 demo** — bigger, exercises more machines, renders to PNG.

## Run

```bash
# From apps/py-cp-sat/
uv sync --all-packages
uv run python -m py_cp_sat_ch09
```

The Gantt chart lands at `./build/ch09_5x4_gantt.png` (override with
`CH09_OUT_DIR=/tmp/gantt`).

## Test

```bash
# From apps/py-cp-sat/
uv run pytest ch09-jobshop/ -v
```

## Exercises

See `src/py_cp_sat_ch09/solutions/`:

| File | Exercise |
|---|---|
| `exercise_9_1_cumulative_staffing.py` | Swap `AddNoOverlap` on machine 0 for `AddCumulative` (capacity 2) |
| `exercise_9_2_release_times.py` | Per-job release times — earliest start per job |
| `exercise_9_3_optional_intervals.py` | Alternative machine via `NewOptionalIntervalVar` |
| `exercise_9_4_minimize_flow_time.py` | Swap the objective from makespan to total completion time |

See `docs/chapters/09-job-shop-intervals.md` for the full teaching spec (when written).
