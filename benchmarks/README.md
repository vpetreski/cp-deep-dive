# benchmarks/

Benchmark results for NSP solvers. Populated starting Chapter 13.

## Layout

- `results/` — CSV/JSON of benchmark runs (instance × params × timing × objective)
- `plots/` — optional matplotlib plots
- `README.md` — methodology + how to reproduce

## Methodology (placeholder)

To be filled when Chapter 13 runs the first real benchmarks.

Expected contents once populated:
- Which instances are run (toy, NSPLib, INRC-I, INRC-II) and where they live
  in `data/nsp/`.
- Wall-clock budgets (time-to-first-feasible, time-to-optimal-or-gap).
- Parameter sweeps (`num_search_workers`, `linearization_level`,
  `cp_model_presolve`, LNS knobs).
- How we compare Python vs Kotlin runs (wall-clock should be within noise —
  the solver is C++ under both).
- How we compare CP-SAT against MiniZinc-on-Gecode/Chuffed and optionally HiGHS.

## Reproducibility

Each result file should include:
- Solver version (`ortools` version for Py, `ortools-java` version for Kt)
- `cpsat-kt` version (for Kotlin runs)
- Hardware (CPU model, RAM, OS, kernel)
- Commit SHA of the repo at run-time
- Random seed
- Full parameter set (as JSON)
- Raw solver search log (gzipped if large)
