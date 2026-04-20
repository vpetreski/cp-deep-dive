# Chapter 11 — NSP v1 — toy instance, hard constraints only

Model with every textbook **hard constraint** (HC-1..HC-8) and no objective. A
feasible schedule proves the instance is solvable within the rules; any
objective-driven preferences land in Chapter 12.

See [`docs/chapters/11-nsp-v1-hard-constraints.md`](../../../docs/chapters/11-nsp-v1-hard-constraints.md)
for the full teaching walkthrough. The solver core lives in
[`nsp-core/`](../nsp-core/) so chapters 12 and 13 (and `apps/py-api`) can share
the same implementation.

## Hard constraints covered

| Code | Description |
|------|-------------|
| HC-1 | Coverage: each `(day, shiftId)` cell has min..max nurses assigned |
| HC-2 | One shift per day per nurse |
| HC-3 | Forbidden shift transitions (e.g. no `N → D`) |
| HC-4 | Max consecutive working days (sliding window) |
| HC-5 | Max consecutive nights |
| HC-6 | Minimum rest hours between shifts (derived into HC-3 pairs) |
| HC-7 | Skill match on coverage cells |
| HC-8 | Contract hours over rolling 7-day window |

## Run

```bash
# Default: data/nsp/toy-01.json
uv run python -m py_cp_sat_ch11

# Custom instance, verbose search log, 60-second budget
uv run python -m py_cp_sat_ch11 path/to/instance.json --time-limit 60 --log
```

Expected output on `toy-01.json`: status `optimal` (hard-only models terminate
as optimal once any feasible schedule is found; CP-SAT returns `OPTIMAL` in
the absence of an objective) and an ASCII roster. Example:

```
Nurse       | d00 d01 d02 d03 d04 d05 d06
------------------------------------------
N1 Alice    |   D   --  N   --  D   --  D
N2 Bob      |   --  D   --  D   --  N   --
N3 Carmen   |   N   N   D   --  N   D   N
```

## Tests

```bash
uv run pytest ch11-nsp-v1 -v
```

Tests solve both toy instances end-to-end and run the standalone `validate_schedule`
on the returned `Schedule` to certify zero hard-constraint violations.

## Exercises

See [`src/py_cp_sat_ch11/solutions/`](src/py_cp_sat_ch11/solutions/) for five
exercise implementations (11-A through 11-E) matching the chapter doc:

- **11-A** Tighten coverage until infeasible; use `sufficient_assumptions_for_infeasibility`.
- **11-B** Add a sixth nurse and observe wall-time change.
- **11-C** Three-tier skill granularity with "mid-or-better" virtual tag.
- **11-D** Presolve inspection comparing "skip cell" vs "create+pin to zero".
- **11-E** JSON certificate round-trip via the standalone validator.
