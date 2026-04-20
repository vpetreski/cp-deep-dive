# benchmarks/results/

One subdirectory per benchmark run, named by run-id (e.g. `2026-04-baseline`
or ISO-ish timestamp like `2026-04-19T211500`).

## Layout

Inside each run directory:

```
results.csv                     <- all runs, one row per (solver, instance)
<instance-id>-<solver>.json     <- one per run, with the incumbent trajectory
```

## CSV schema

`results.csv` is UTF-8, comma-separated, with the following columns:

| Column          | Type   | Description                                               |
|-----------------|--------|-----------------------------------------------------------|
| `timestamp`     | string | ISO-8601 UTC of when this solver run started.             |
| `instance_id`   | string | The `id` field from the NSP instance JSON.                |
| `solver`        | string | `cpsat`, `timefold`, `choco`.                             |
| `status`        | string | `optimal`, `feasible`, `infeasible`, `unknown`, `error`.  |
| `objective`     | number | Soft-penalty objective (lower is better). Empty if none.  |
| `bound`         | number | Best dual bound; only CP-SAT reports it today.            |
| `gap`           | number | `(objective - bound) / |objective|` clamped to `[0, 1]`.  |
| `solve_seconds` | number | Solver-reported wall time (what the solver saw).          |
| `wall_seconds`  | number | Runner-measured wall time including subprocess spawn.     |

`wall_seconds - solve_seconds` captures the one-off cost of forking a JVM
or a Python interpreter. On toy instances that gap is often the majority
of the total — don't mistake it for solve cost.

## Per-run JSON schema

Each `<instance>-<solver>.json` mirrors the CSV row plus an incumbent
trajectory:

```json
{
  "timestamp": "2026-04-19T21:15:00Z",
  "instanceId": "toy-01",
  "solver": "cpsat",
  "status": "optimal",
  "objective": 0.0,
  "bound": 0.0,
  "gap": 0.0,
  "solveSeconds": 0.12,
  "wallSeconds": 1.83,
  "trajectory": [
    {"tSeconds": 0.12, "objective": 0.0}
  ]
}
```

The current adapter set records only the final incumbent because neither
Choco's minimal CLI nor the Timefold CLI stream intermediate scores yet.
The CP-SAT adapter has the plumbing to emit a full trajectory — the bench
wrapper just needs a progress callback wired in.

## Reproducing

```bash
./benchmarks/run-baseline.sh            # -> results/2026-04-baseline/
./benchmarks/run-baseline.sh my-run-id  # -> results/my-run-id/
```

Override env vars:

- `SOLVERS=cpsat,choco` — subset the solver list.
- `INSTANCES=data/nsp/toy-01.json` — single-instance runs.
- `TIME_LIMIT=60` — longer budget.

The runner honours `--solvers`, `--instances`, `--time-limit`, `--out`,
`--run-id`, and `--project-root`.

## Solver notes

- **cpsat** — runs `nsp_core.solve(objective="weighted")` via a thin
  `scripts/cpsat_bench.py` wrapper that emits one machine-readable line.
- **timefold** — `apps/alt-solver/timefold` app; objective = `-softScore` so
  "smaller is better" matches the repo-wide convention.
- **choco** — `apps/alt-solver/choco` app; objective = the model's
  minimised total (SC-1 + SC-2 spread).

Three solvers, three paradigms — their objectives are comparable in
direction (smaller is better) but not in absolute units: CP-SAT's weighted
sum embeds the full `ObjectiveWeights` (`preference=10, fairness=5, ...`),
Timefold and Choco use flat `1`-per-unit weights in these ports. Compare
feasibility and convergence shape first, objective values second.
