# Chapter 13 — NSP v3: Benchmark-Scale & Solver Tuning

> **Phase 5: Nurse Scheduling I/II/III** · Estimated: ~6h · Status: ready-to-start · Last updated: 2026-04-19

## Goal

Take an INRC-II benchmark instance (30–120 nurses × 4–8 weeks), get a baseline solve, and **tune the solver** — workers, hints, linearization, presolve, LNS — to find the single biggest win on your hardware. Compare Python vs Kotlin (should be within noise — the solver is C++). Emit a benchmark table you can cite.

## Before you start

- **Prerequisites:** Chapters 11 (v1 hard), 12 (v2 soft/fairness), Chapter 5 (objectives + callbacks).
- **Required reading:**
  - [`docs/knowledge/nurse-scheduling/overview.md`](../knowledge/nurse-scheduling/overview.md) §8 (INRC-I/II), §10.9 (CP-SAT tips for NSP).
  - [`docs/knowledge/cp-sat/overview.md`](../knowledge/cp-sat/overview.md) §9 (search & parameters, tuning order of operations) and §10 (best practices).
  - Krupke's CP-SAT Primer §7 (under the hood) and §10 (benchmarking): <https://d-krupke.github.io/cpsat-primer/>
- **Environment:** INRC-II dataset fetched to `data/nsp/inrc2/`; loader in `tools/nsp-loader/`.

## Concepts introduced this chapter

- **Benchmark instance** — a publicly-available, standardized problem you can cite. INRC-II: `n030w4`, `n040w4`, `n050w4`, ..., `n120w8`.
- **Time-to-first-feasible (TTF)** vs **Time-to-optimal-or-gap (TTO)** — two distinct performance metrics.
- **Primal-dual gap** — `(incumbent − bound) / |incumbent|`; stop target for real work.
- **Solution hint** — a suggested value for a variable; CP-SAT repairs infeasible hints and builds from partial ones.
- **`num_search_workers`** — parallel portfolio size. More workers != faster.
- **`linearization_level`** — how aggressively CP-SAT builds LP relaxations (0=off, 1=default, 2=aggressive).
- **`cp_model_presolve`** — on by default; disable only to debug.
- **Large Neighborhood Search (LNS)** — destroy-and-repair heuristic running as a worker.
- **Diversified portfolio** — multiple workers configured differently to explore different regions.
- **Decomposition** — split the problem into sub-problems (per-week) solved sequentially.
- **Search log** — the `log_search_progress` output; your primary diagnostic.

## 1. Intuition

Your v2 model works on a toy; INRC-II has 60 nurses × 28 days × ~4 shifts × 4 skills. That's tens of thousands of booleans and a heavy coverage matrix. The solver may find a first feasible in 2 seconds, then grind for hours improving the objective.

The win isn't one magic parameter. It's:

1. **Read the log.** Most "I need help" moments are spelled out in the progress log — "LP relaxation tight," "LNS struck at 23 seconds," "symmetry breaking is on."
2. **Tighten the model first.** Every loose bound or missed global is a multiplier on search cost.
3. **Hint from last month.** Warm starts kill time-to-first-feasible.
4. **Parallel, but don't over-subscribe.** Match *physical* cores; hyperthreading often hurts.
5. **Cap with a relative gap.** Optimality is luxury; 2% gap is fine.

Kotlin vs Python: **the solver is identical C++.** Any time difference is JVM warm-up, protobuf marshaling, or your own hot-path code. Expect them to converge within ±10% on a fair run.

## 2. Formal definition

### 2.1 Benchmark metrics

Define metrics in `benchmarks/metrics.py` / `Metrics.kt`:

| Metric | Definition |
|---|---|
| `TTF` | Wall seconds from `solve()` start to first callback with `objective_value < ∞` |
| `TTO` | Wall seconds to reach `OPTIMAL` (or time-out) |
| `TT5pct` | Wall seconds to reach `(obj − bound) / obj ≤ 0.05` |
| `best_obj` | Best incumbent objective at termination |
| `best_bound` | Best dual bound at termination |
| `gap@t` | `(best_obj − best_bound) / best_obj` at wall time `t` |
| `num_branches` | Total branches across all workers |
| `num_conflicts` | Total conflicts learned (SAT core activity) |

### 2.2 Parameter sweep design

Use a small factorial design — don't brute-force all 300 parameters.

| Factor | Levels | Reason |
|---|---|---|
| `num_search_workers` | 1, 2, 4, 8 (physical cores) | Parallelism sweet spot |
| `linearization_level` | 0, 1, 2 | LP-heaviness sensitivity |
| `cp_model_presolve` | on, off | Confirm presolve's contribution |
| `add_hint` | off, warm-start from v2's toy | Warm-start value |
| `relative_gap_limit` | 0.0 (optimal), 0.05, 0.10 | Production realism |
| `max_time_in_seconds` | 60, 300 | Short vs long budgets |

That's 4 × 3 × 2 × 2 × 3 × 2 = **288 runs**, which is too many. Sweep one-at-a-time from a sensible default (workers=8, lin=1, presolve=on, no hint, gap=0, time=300s). Change one factor, re-run, record.

### 2.3 Decomposition

INRC-II instances are *dynamic*: week 1's decisions constrain week 2. A standard decomposition:

```
for each week w in 1..W:
    freeze previous weeks' decisions
    solve week w with coverage + rolling carryovers
    commit solution to "history"
end
```

This trades optimality for tractability. Use as a *fallback* when the monolithic solve can't hit a gap target.

## 3. Worked example by hand

Not applicable at this scale — you can't solve INRC-II on paper. Instead, **estimate**:

For `n040w4` (40 nurses × 28 days × 4 shifts × 4 skills):
- Booleans ≈ 40 × 28 × 4 = 4,480 (before presolve removes unavailable cells)
- Coverage constraints ≈ 28 × 4 × 4 = 448
- Forbidden-transition pairs ≈ 27 × 40 × {pairs} ≈ 2,000 per pair type
- Sliding-window max-consec ≈ 25 windows × 40 nurses = 1,000

Total: ~10,000 active constraints. CP-SAT's presolve typically removes 30–60%. You'll see this in the log: "presolved: X vars, Y constraints."

## 4. Python implementation

```python
# tools/nsp-loader/inrc2/loader.py
"""Parse INRC-II XML/JSON instances into our schema."""
import xml.etree.ElementTree as ET
from pathlib import Path

from nsp_shared.instance import Instance, Nurse     # the Chapter 11 dataclass

def load_inrc2(scenario: Path, history: Path, weeks: list[Path]) -> Instance:
    # INRC-II splits data across scenario.xml, history-N.xml, and per-week demand.
    # ... parsing elided; see the INRC-II simulator for the canonical schema.
    ...
```

```python
# apps/py-cp-sat/ch13-nsp-v3/src/benchmark.py
"""Run the parameter sweep over one INRC-II instance."""
import dataclasses, csv, time
from ortools.sat.python import cp_model

from ch11_nsp_v1.src.model import build_model as build_hard
from ch12_nsp_v2.src.model_v2 import add_soft_objective, Weights


@dataclasses.dataclass
class RunResult:
    params: dict
    status: str
    ttf: float
    tto: float
    best_obj: int | None
    best_bound: float | None
    gap: float
    num_branches: int
    num_conflicts: int
    wall_time: float


class IncumbentRecorder(cp_model.CpSolverSolutionCallback):
    """Records the wall clock at every intermediate solution."""
    def __init__(self):
        super().__init__()
        self.trace = []
        self.start_ns = time.monotonic_ns()

    def on_solution_callback(self):
        elapsed = (time.monotonic_ns() - self.start_ns) / 1e9
        self.trace.append((elapsed, self.objective_value, self.best_objective_bound))


def run_once(instance, weights, params: dict) -> RunResult:
    model, x = build_hard(instance)
    aux = add_soft_objective(model, x, instance, preferences={}, partners=[],
                             weights=weights or Weights())

    solver = cp_model.CpSolver()
    for k, v in params.items():
        setattr(solver.parameters, k, v)

    recorder = IncumbentRecorder()
    solver.parameters.enumerate_all_solutions = False
    start = time.monotonic()
    status = solver.solve(model, recorder)
    wall = time.monotonic() - start

    first_feasible_ttf = recorder.trace[0][0] if recorder.trace else float("inf")
    tto = wall if status == cp_model.OPTIMAL else float("inf")

    best_obj = int(solver.objective_value) if status in (cp_model.OPTIMAL, cp_model.FEASIBLE) else None
    best_bound = solver.best_objective_bound if best_obj is not None else None
    gap = (best_obj - best_bound) / max(abs(best_obj), 1) if best_obj else float("inf")

    return RunResult(
        params=params,
        status=solver.status_name(status),
        ttf=first_feasible_ttf,
        tto=tto,
        best_obj=best_obj,
        best_bound=best_bound,
        gap=gap,
        num_branches=solver.num_branches,
        num_conflicts=solver.num_conflicts,
        wall_time=wall,
    )


def sweep(instance, weights, param_grid: list[dict], outfile: str):
    rows = []
    for p in param_grid:
        print(f"Running: {p}")
        res = run_once(instance, weights, p)
        rows.append({**res.__dict__, **p})
    with open(outfile, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=rows[0].keys())
        writer.writeheader()
        for row in rows:
            # Flatten `params` for CSV
            row = {k: v for k, v in row.items() if k != "params"}
            writer.writerow(row)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--instance", required=True)
    parser.add_argument("--out", default="bench.csv")
    args = parser.parse_args()

    from ch11_nsp_v1.src.instance import load as load_v1
    inst = load_v1(args.instance)

    # One-at-a-time sweep
    base = dict(max_time_in_seconds=300.0, num_search_workers=8,
                linearization_level=1, cp_model_presolve=True,
                log_search_progress=False, relative_gap_limit=0.0)
    grid = []
    # Vary workers
    for w in (1, 2, 4, 8):
        grid.append({**base, "num_search_workers": w})
    # Vary linearization
    for l in (0, 1, 2):
        grid.append({**base, "linearization_level": l})
    # Vary presolve
    for p in (True, False):
        grid.append({**base, "cp_model_presolve": p})
    # Vary gap
    for g in (0.0, 0.05, 0.10):
        grid.append({**base, "relative_gap_limit": g})

    sweep(inst, weights=None, param_grid=grid, outfile=args.out)
```

### Hint-driven warm-start

```python
# apps/py-cp-sat/ch13-nsp-v3/src/warm_start.py
def add_hints_from_previous_solution(model, x, previous_schedule):
    """Previous schedule: dict (n_id, d, s) -> 0/1."""
    for key, val in previous_schedule.items():
        if key in x:
            model.add_hint(x[key], val)
```

Wire into the sweep:

```python
solver.parameters.repair_hint = True
```

### MILP sanity check (HiGHS)

```python
# apps/py-cp-sat/ch13-nsp-v3/src/highs_baseline.py
"""Formulate the hard-constraint NSP as a MILP and solve with HiGHS."""
import highspy

def build_highs(instance):
    h = highspy.Highs()
    # ... express H1–H8 as linear constraints; x as integer 0/1 ...
    return h
```

Compare times. HiGHS is a fine MILP; for NSP-shaped combinatorial logic, expect CP-SAT to dominate on all but the smallest instances.

## 5. Kotlin implementation (via `cpsat-kt`)

```kotlin
// apps/kt-cp-sat/ch13-nsp-v3/src/main/kotlin/Benchmark.kt
package ch13

import io.vanja.cpsat.*
import ch11.*
import ch12.*
import java.nio.file.Path
import kotlin.time.measureTimedValue

data class RunResult(
    val params: Map<String, Any>,
    val status: String,
    val ttf: Double,
    val tto: Double,
    val bestObj: Long?,
    val bestBound: Double?,
    val gap: Double,
    val wallSeconds: Double,
)

fun runOnce(
    inst: InstanceDef,
    params: Map<String, Any>,
    weights: Weights = Weights(),
): RunResult {
    val (model, vars) = buildModel(inst)
    model.addSoftObjective(vars, preferences = emptyMap(), partners = emptyList(), weights)

    // Stream incumbents via solveFlow to capture TTF
    var ttf = Double.POSITIVE_INFINITY
    val startNs = System.nanoTime()

    // For a one-shot final result:
    val (result, dur) = measureTimedValue {
        model.solveBlocking {
            maxTimeInSeconds = (params["maxTimeInSeconds"] as? Double) ?: 300.0
            numSearchWorkers = (params["numSearchWorkers"] as? Int) ?: 8
            linearizationLevel = (params["linearizationLevel"] as? Int) ?: 1
            cpModelPresolve = (params["cpModelPresolve"] as? Boolean) ?: true
            relativeGapLimit = (params["relativeGapLimit"] as? Double) ?: 0.0
            logSearchProgress = (params["log"] as? Boolean) ?: false
        }
    }

    val wall = dur.inWholeMilliseconds / 1000.0
    val status = result.javaClass.simpleName
    val best = when (result) {
        is SolveResult.Optimal -> result.objective
        is SolveResult.Feasible -> result.objective
        else -> null
    }
    val bound = (result as? SolveResult.Feasible)?.bound?.toDouble()
    val gap = (result as? SolveResult.Feasible)?.gap ?: 0.0

    return RunResult(params, status, ttf, wall, best, bound, gap, wall)
}

fun sweep(inst: InstanceDef, grid: List<Map<String, Any>>, out: Path) {
    val rows = grid.map { runOnce(inst, it) }
    out.toFile().bufferedWriter().use { w ->
        w.write("status,wallSeconds,bestObj,gap,params\n")
        rows.forEach {
            w.write("${it.status},${it.wallSeconds},${it.bestObj},${it.gap},${it.params}\n")
        }
    }
}

fun main(args: Array<String>) {
    val inst = InstanceLoader.load(Path.of(args[0]))
    val base = mapOf<String, Any>(
        "maxTimeInSeconds" to 300.0,
        "numSearchWorkers" to 8,
        "linearizationLevel" to 1,
        "cpModelPresolve" to true,
        "relativeGapLimit" to 0.0,
    )
    val grid = buildList {
        for (w in listOf(1, 2, 4, 8)) add(base + ("numSearchWorkers" to w))
        for (l in listOf(0, 1, 2)) add(base + ("linearizationLevel" to l))
        for (p in listOf(true, false)) add(base + ("cpModelPresolve" to p))
        for (g in listOf(0.0, 0.05, 0.10)) add(base + ("relativeGapLimit" to g))
    }
    sweep(inst, grid, Path.of(args.getOrNull(1) ?: "bench.csv"))
}
```

## 6. MiniZinc implementation (optional)

MiniZinc + CP-SAT backend on INRC-II is a valid baseline. Skip unless you want to validate your model against a declarative twin.

## 7. Comparison & takeaways

Expected pattern on `n040w4` (rough targets, YMMV on your hardware):

| Config | Wall (s) | Branches | Best obj | Gap |
|---|---|---|---|---|
| Default (8 workers, lin=1) | 180 | 1.2M | 142 | 4% |
| Workers=1 | 520 | 480k | 155 | 12% |
| Workers=2 | 310 | 900k | 148 | 7% |
| Workers=4 | 210 | 1.1M | 144 | 5% |
| lin=0 | 240 | 1.6M | 149 | 7% |
| lin=2 | 170 | 900k | 141 | 3% |
| presolve off | 780 | 4M | 155 | 14% |
| + hint (prev month) | 85 | 600k | 139 | 2% |
| + gap=5% | 72 | 500k | 144 | 5% (reported: `FEASIBLE` with bound) |

Python vs Kotlin: wall-clock within ±10%. JVM warm-up accounts for ~0.5s on first solve; after warm-up, identical.

**Key insight:** The single biggest lever on real NSP is usually **warm-start hints** from last month's schedule. Presolve-on and `linearization_level=2` are close seconds. More workers helps up to physical cores; hyperthreading often *hurts*.

## 8. Exercises

**Exercise 13-A: Find the single best parameter for your hardware.** Run the one-at-a-time sweep above on `n040w4`. Pick the single parameter change that improves TTO the most versus default. Commit the sweep CSV + a 1-paragraph writeup.

<details><summary>Hint</summary>
Expect `linearization_level=2` or warm-start hints to win. If presolve matters more than 10%, you've got a loose-bounds bug in your model.
</details>

**Exercise 13-B: Compare to HiGHS.** Formulate NSP v1 (hard-only) as an MILP, solve with HiGHS. Compare TTF and TTO. Where does CP-SAT win? Where is HiGHS closer? Hypothesize why (strong LP relaxation for coverage, weak for transitions, etc.).

<details><summary>Hint</summary>
Use `highspy` from `pip install highspy`. Encode `x[n,d,s]` as `setIntegrality(HighsVarType.kInteger)`. Model H1 exactly as coverage; H3 as pairwise ≤ 1. Expect HiGHS to struggle on max-consec due to big-M.
</details>

**Exercise 13-C: Decompose week-by-week.** Implement the sequential per-week solver. Compare total objective and total wall-time to the monolithic solve. Does decomposition ever *beat* the monolith? If so, at what instance size?

<details><summary>Hint</summary>
Freeze previous weeks' `x` values; carry rolling counts (consec working days, weekly hours, weekends worked) into the next week's model as parameters. Usually loses on objective but wins on wall-time at the largest sizes.
</details>

**Exercise 13-D: Ten-instance benchmark script.** Write `benchmarks/run_all.py` that loops over `{n030w4, n040w4, n050w4, n060w4, n080w4, n100w4, n120w4}` and `{n030w8, …, n120w8}`. Use a time budget of 5 min per instance. Output a CSV. Commit.

<details><summary>Hint</summary>
Log each instance's outcome with status + best_obj + gap + wall. Use `subprocess.run` or `ProcessPoolExecutor` to isolate JVM restarts / Python instances cleanly.
</details>

**Exercise 13-E: Python vs Kotlin head-to-head.** On `n040w4`, run both stacks 5 times each (restart process between runs; warm up JVM with a 1-second dummy solve first). Record wall-time; compute mean + stdev. Conclude: noise-level? If >10% consistent difference, investigate (it's almost certainly JVM start-up or a model-build inefficiency, not the solver).

<details><summary>Hint</summary>
Suspect culprits: kotlinx-serialization parse time, lambda allocations in `cpsat-kt`, protobuf builder overhead. Run a profiler (async-profiler for JVM, py-spy for Python) to locate.
</details>

## 9. Self-check

<details><summary>Q1: Which parameter helped most? Why?</summary>
Most often warm-start hints (when you have them) or `linearization_level=2` (when LP relaxation is informative). Document yours: it depends on instance shape.
</details>

<details><summary>Q2: How do you know when you've squeezed the model dry vs need search help?</summary>
Log signal: if best bound is moving but incumbent isn't, the search is finding improved LPs but no integer-feasible improvements — more LNS / hints help. If bound plateaus, the LP is tight; tighter model / cuts help. If both plateau, you're at or near optimal.
</details>

<details><summary>Q3: Could a MILP solver (HiGHS) solve this? Try and compare.</summary>
Yes for small instances; becomes dominant for large ones with tight LP relaxations. For NSP-shaped integer logic (max-consec, automata, global coverage), CP-SAT almost always wins because its propagators are stronger than LP bounds on these constraints.
</details>

<details><summary>Q4: Why does hyperthreading sometimes hurt parallel search?</summary>
Two SMT threads sharing a core fight for cache lines and execution ports. CP-SAT workers are CPU-bound; the classical rule "workers = physical cores" holds. With 4 cores + SMT: set to 4, not 8.
</details>

<details><summary>Q5: When should you accept `FEASIBLE` instead of chasing `OPTIMAL`?</summary>
Almost always in production. Set `relative_gap_limit = 0.02`–`0.05`. Nurse managers can't distinguish a 3%-gap schedule from optimal; saving hours of compute for 2% objective delta is a trivial trade.
</details>

## 10. What this unlocks

You have a production-quality NSP solver. Now you'll wrap it in an **app** — starting with Chapter 14, where you *write and lock a spec* before a single line of app code.

## 11. Further reading

- Ceschia et al., "The second international nurse rostering competition," *Ann. OR* 292, 2019 — the canonical benchmark and scoring. [DOI 10.1007/s10479-018-2816-0](https://doi.org/10.1007/s10479-018-2816-0)
- INRC-II simulator / dataset: <https://github.com/samysr/inrc2>, <http://mobiz.vives.be/inrc2/>
- Krupke, "CP-SAT Primer" — comprehensive parameter guide and log interpretation. <https://d-krupke.github.io/cpsat-primer/>
- Krupke, "CP-SAT Log Analyzer" — visualize search progress. <https://github.com/d-krupke/CP-SAT-Log-Analyzer>
- Perron & Didier, "The CP-SAT-LP Solver," CP 2023. [DOI 10.4230/LIPIcs.CP.2023.3](https://drops.dagstuhl.de/storage/00lipics/lipics-vol280-cp2023/LIPIcs.CP.2023.3/LIPIcs.CP.2023.3.pdf)
- Davies, Didier, Perron, "ViolationLS: Constraint-Based Local Search in CP-SAT," CPAIOR 2024 — the LNS worker inside CP-SAT. [DOI 10.1007/978-3-031-60597-0_16](https://link.springer.com/chapter/10.1007/978-3-031-60597-0_16)
- `sat_parameters.proto` — authoritative parameter list: <https://github.com/google/or-tools/blob/stable/ortools/sat/sat_parameters.proto>
- HiGHS project page — MILP comparison: <https://highs.dev/>
