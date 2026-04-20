# Chapter 11 — NSP v1: Toy Instance, Hard Constraints Only

> **Phase 5: Nurse Scheduling I/II/III** · Estimated: ~4h · Status: ready-to-start · Last updated: 2026-04-19

## Goal

Assemble a **full nurse-rostering model** for a 5-nurse × 14-day × 3-shift toy instance with *every* hard constraint from the textbook: coverage, availability, skill mix, min/max shifts per week, minimum rest, forbidden transitions, max consecutive days, and fixed days off. Output a readable ASCII schedule and validate it with a standalone script. By the end you can hand a printed roster to a nurse manager and say, "these are the rules and this is a certified-feasible assignment."

## Before you start

- **Prerequisites:** Chapters 4 (puzzles), 5 (optimization), 6 (globals), 9 (intervals), 10 (shifts + transitions).
- **Required reading:**
  - [`docs/knowledge/nurse-scheduling/overview.md`](../knowledge/nurse-scheduling/overview.md) §3 (formal model), §4 (hard-constraint taxonomy H1–H13), §10 (CP-SAT patterns), §11 (worked toy example).
  - [`docs/knowledge/cp-sat/overview.md`](../knowledge/cp-sat/overview.md) §4 (constraint catalogue) and §10 (best practices — keep bounds tight, globals > decompositions).
- **Environment:** Python project at `apps/py-cp-sat/ch11-nsp-v1/`, Kotlin project at `apps/kt-cp-sat/ch11-nsp-v1/`, shared schemas in `apps/shared/schemas/`.

## Concepts introduced this chapter

- **Instance** — one JSON blob containing all parameters; the input to your solver.
- **Instance schema** — JSON Schema (`nsp-instance.schema.json`) that validates any instance file before solving. The schema is the contract between loaders, solver, and UI.
- **Coverage matrix** — `demand[d, s, k]` grid; the heart of the hospital's needs.
- **Skill mix** — per-shift minimum counts segmented by skill (e.g., "at least 1 senior per night").
- **Per-nurse contract** — `{ minShifts, maxShifts, weeklyHoursCap, availability, fixedAssignments }`.
- **Certificate** — a fully grounded schedule, printable, checkable, and (crucially) **independently validatable** without the solver.
- **Presolve report** — what CP-SAT tells you it simplified before searching; a quick sanity check that your bounds aren't sloppy.

## 1. Intuition

You've built the pieces. Now you assemble them into one model.

Think of the instance as a *rulebook* and a *canvas*: the rulebook says who's who, who can't work which days, how many nurses a Tuesday evening needs. The canvas is the 5×14×3 grid of booleans. CP-SAT's job is to fill the canvas such that every page of the rulebook is satisfied. Your job is twofold:
1. Encode every page as a constraint with no holes.
2. Give the solver tight variable bounds and strong global constraints so search is short.

A real nurse manager would grade your schedule on three things: *is it legal* (labour law + collective agreement), *is it covered* (every slot staffed), *is it humane* (the soft stuff — that's Chapter 12). v1 focuses on the first two.

## 2. Formal definition

### 2.1 Sets and parameters

| Symbol | Meaning | Value in toy |
|---|---|---|
| `N` | nurses | 5 (A, B, C, D, E) |
| `D` | days in horizon | 14 |
| `S` | shifts per day | 3 (Day, Evening, Night) — plus implicit Off |
| `K` | skills | 2 (junior, senior) |
| `demand[d, s, k]` | nurses with skill k needed on day d shift s | see JSON |
| `skill[n, k]` | whether nurse n has skill k | see JSON |
| `avail[n, d, s]` | 1 if nurse n can work shift s on day d | see JSON (vacation etc → 0) |
| `fixed[n, d, s]` | 1 if nurse n is pre-assigned | sparse |
| `minShifts[n]` | min shifts per horizon | 6 |
| `maxShifts[n]` | max shifts per horizon | 10 |
| `maxConsec[n]` | max consecutive working days | 5 |
| `forbidden` | forbidden shift-transition pairs | `{(N, D)}` (no Night → Day next morning) |

### 2.2 Variables

```
x[n, d, s] ∈ {0, 1}     ∀ n ∈ N, d ∈ D, s ∈ S
```

Plus (optional) auxiliaries for readability:
```
works[n, d]   ∈ {0, 1}   := ∑_s x[n, d, s]       (1 iff nurse works any shift on d)
```

### 2.3 Hard constraints — all eight

```
(H1) Coverage (per skill):
     ∑_{n : skill[n,k]=1} x[n, d, s] ≥ demand[d, s, k]     ∀ d, s, k

(H2) At most one shift per day:
     ∑_{s ∈ S} x[n, d, s] ≤ 1                             ∀ n, d

(H3) Forbidden transitions:
     x[n, d, s1] + x[n, d+1, s2] ≤ 1                      ∀ n, d<|D|-1, (s1,s2) ∈ forbidden

(H4) Minimum rest (11h) — reduces to H3 under fixed shift timings.

(H5) Max consecutive working days:
     ∑_{d'=d..d+maxConsec} works[n, d'] ≤ maxConsec[n]    ∀ n, d
     (sliding window of size maxConsec+1)

(H6) Min/max shifts per horizon:
     minShifts[n] ≤ ∑_{d,s} x[n, d, s] ≤ maxShifts[n]     ∀ n

(H7) Availability:
     x[n, d, s] ≤ avail[n, d, s]                          ∀ n, d, s
     (or simply remove the variable when avail=0, which is tighter — see §4)

(H8) Skill mix — already covered by H1 if demand is per skill.

Fixed assignments: x[n, d, s] = 1 iff fixed[n, d, s] = 1.
```

### 2.4 Cross-language mapping

| Concept | Python | Kotlin (`cpsat-kt`) |
|---|---|---|
| Build model | `cp_model.CpModel()` | `cpModel { ... }` |
| 3-D bool grid | dict `x[(n,d,s)]` of `new_bool_var` | `HashMap<Triple<...>, BoolVar>` |
| `works[n,d]` aux | `x_works = {(n, d): model.new_bool_var(...)}` + linking | `boolVar(...)` + linking constraint |
| Skip unavail cell | Don't create var for `avail=0` | Same |
| Coverage | `model.add(sum(x[n,d,s] for n) >= demand[d,s,k])` | `constraint { sum(...) ge demand[d,s,k] }` |

### 2.5 Shared instance schema

All loaders and validators must agree on one schema. Put it in `apps/shared/schemas/nsp-instance.schema.json` (JSON Schema draft 2020-12). Inline outline:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://cp-deep-dive/schemas/nsp-instance-v1.json",
  "title": "NSP Instance v1",
  "type": "object",
  "required": ["horizon", "shifts", "skills", "nurses", "demand"],
  "properties": {
    "horizon": {
      "type": "object",
      "required": ["startDate", "days"],
      "properties": {
        "startDate": { "type": "string", "format": "date" },
        "days": { "type": "integer", "minimum": 1, "maximum": 365 }
      }
    },
    "shifts": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["id", "startHour", "endHour"],
        "properties": {
          "id": { "type": "string", "enum": ["D", "E", "N"] },
          "startHour": { "type": "integer", "minimum": 0, "maximum": 47 },
          "endHour": { "type": "integer", "minimum": 0, "maximum": 47 }
        }
      }
    },
    "skills": {
      "type": "array",
      "items": { "type": "string" },
      "examples": [["junior", "senior"]]
    },
    "nurses": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["id", "skills", "minShifts", "maxShifts", "maxConsec"],
        "properties": {
          "id": { "type": "string" },
          "skills": { "type": "array", "items": { "type": "string" } },
          "minShifts": { "type": "integer", "minimum": 0 },
          "maxShifts": { "type": "integer", "minimum": 0 },
          "maxConsec": { "type": "integer", "minimum": 1 },
          "unavailable": {
            "type": "array",
            "items": {
              "type": "object",
              "required": ["day"],
              "properties": {
                "day": { "type": "integer" },
                "shift": { "type": "string" }
              }
            }
          },
          "fixed": {
            "type": "array",
            "items": {
              "type": "object",
              "required": ["day", "shift"],
              "properties": {
                "day": { "type": "integer" },
                "shift": { "type": "string" }
              }
            }
          }
        }
      }
    },
    "demand": {
      "description": "3-D array [day][shift][skill] of required counts",
      "type": "array",
      "items": {
        "type": "array",
        "items": {
          "type": "object",
          "additionalProperties": { "type": "integer", "minimum": 0 }
        }
      }
    },
    "forbiddenTransitions": {
      "type": "array",
      "items": {
        "type": "array",
        "items": { "type": "string" },
        "minItems": 2,
        "maxItems": 2
      },
      "default": [["N", "D"]]
    }
  }
}
```

### 2.6 Toy instance

```jsonc
// data/nsp/toy-01.json
{
  "horizon": { "startDate": "2026-05-04", "days": 14 },
  "shifts": [
    { "id": "D", "startHour": 7,  "endHour": 15 },
    { "id": "E", "startHour": 15, "endHour": 23 },
    { "id": "N", "startHour": 23, "endHour": 33 }
  ],
  "skills": ["junior", "senior"],
  "nurses": [
    { "id": "A", "skills": ["senior"], "minShifts": 6, "maxShifts": 10, "maxConsec": 5,
      "unavailable": [{ "day": 3 }] },
    { "id": "B", "skills": ["senior"], "minShifts": 6, "maxShifts": 10, "maxConsec": 5 },
    { "id": "C", "skills": ["junior"], "minShifts": 6, "maxShifts": 10, "maxConsec": 5,
      "fixed":       [{ "day": 0, "shift": "D" }] },
    { "id": "D", "skills": ["junior"], "minShifts": 6, "maxShifts": 10, "maxConsec": 5 },
    { "id": "E", "skills": ["junior"], "minShifts": 6, "maxShifts": 10, "maxConsec": 5,
      "unavailable": [{ "day": 10, "shift": "N" }, { "day": 11, "shift": "N" }] }
  ],
  "demand": "… 14 × 3 × 2 matrix, see data/nsp/toy-01.json …",
  "forbiddenTransitions": [["N", "D"]]
}
```

A realistic demand pattern: Day = 2 nurses, Evening = 2 nurses, Night = 1 nurse; "at least 1 senior" on Day and Night.

## 3. Worked example by hand

A mini-instance we can feasibility-check without code:

- 2 nurses (A = senior, B = junior)
- 3 days
- 2 shifts (D, N)
- Demand: every (day, D) needs 1, every (day, N) needs 1.
- `minShifts = 2`, `maxShifts = 4` per nurse, `maxConsec = 3`, no forbidden pairs.
- No availability blocks.

**Counting.** 6 slots, each filled by exactly 1 of 2 nurses. Each nurse must do between 2 and 4 slots. Total filled = 6, so the two totals sum to 6 → possible splits: (2,4), (3,3), (4,2).

**Enumerate (3,3):** Nurse A does 3 slots, B does 3. Many concrete assignments; one:

| | Day 0 | Day 1 | Day 2 |
|---|---|---|---|
| A | D | off | N |
| B | N | D | D → wait, but then D slot day 2 is B, and N slot day 2 is… |

Wait — both D and N on day 2 need 1 nurse. If A=N and B=D on day 2, both are covered. Let's redo:

| | Day 0 | Day 1 | Day 2 |
|---|---|---|---|
| A | D | N | off |
| B | N | D | ??? |

Day 2 needs a D and an N. A is off → both shifts fall on B. But H2 says B ≤ 1 shift/day. **Infeasible split**: you can't finish with a 2-3 split where one nurse is off on day 2.

This tells you something: **every day needs both nurses working** (because 2 slots, 1 nurse/slot, and 1 nurse can't cover 2 slots). So the only feasible schedule shape has every nurse working every day. Now A and B each do exactly 3 shifts. Enumerate:

| Day 0 | Day 1 | Day 2 | A's sequence | B's sequence | Valid? |
|---|---|---|---|---|---|
| A=D, B=N | A=D, B=N | A=D, B=N | D,D,D | N,N,N | Y |
| A=D, B=N | A=D, B=N | A=N, B=D | D,D,N | N,N,D | Y |
| A=D, B=N | A=N, B=D | A=D, B=N | D,N,D | N,D,N | Y |
| A=D, B=N | A=N, B=D | A=N, B=D | D,N,N | N,D,D | Y |
| … | | | | | |

Feasible count = 8 (by symmetry: each day independently chooses which of A/B is on D). Run your solver with `enumerate_all_solutions = True` on this mini-instance; confirm it prints 8.

## 4. Python implementation

Project layout:
```
apps/py-cp-sat/ch11-nsp-v1/
├── pyproject.toml
├── src/
│   ├── instance.py         # load + validate instance
│   ├── model.py            # build_model()
│   ├── solve.py            # CLI: solve path/to/instance.json
│   └── render.py           # ASCII schedule
└── tests/
    └── test_solve.py
```

```python
# apps/py-cp-sat/ch11-nsp-v1/src/instance.py
"""Load and validate NSP instance JSON against the shared schema."""
import json, pathlib
from dataclasses import dataclass
from datetime import date
from typing import Any

import jsonschema


SCHEMA_PATH = pathlib.Path(__file__).resolve().parents[3] / \
    "shared/schemas/nsp-instance.schema.json"


@dataclass(frozen=True)
class Nurse:
    id: str
    skills: frozenset[str]
    min_shifts: int
    max_shifts: int
    max_consec: int
    unavailable: frozenset[tuple[int, str | None]]
    fixed: frozenset[tuple[int, str]]


@dataclass(frozen=True)
class Instance:
    start_date: date
    days: int
    shifts: tuple[str, ...]
    skills: tuple[str, ...]
    nurses: tuple[Nurse, ...]
    demand: dict[tuple[int, str, str], int]       # (d, s, k) -> required
    forbidden_pairs: tuple[tuple[str, str], ...]


def load(path: str | pathlib.Path) -> Instance:
    raw: dict[str, Any] = json.loads(pathlib.Path(path).read_text())
    schema = json.loads(SCHEMA_PATH.read_text())
    jsonschema.validate(raw, schema)

    shifts = tuple(s["id"] for s in raw["shifts"])
    skills = tuple(raw["skills"])

    nurses = tuple(
        Nurse(
            id=n["id"],
            skills=frozenset(n["skills"]),
            min_shifts=n["minShifts"],
            max_shifts=n["maxShifts"],
            max_consec=n["maxConsec"],
            unavailable=frozenset(
                (u["day"], u.get("shift")) for u in n.get("unavailable", [])
            ),
            fixed=frozenset(
                (f["day"], f["shift"]) for f in n.get("fixed", [])
            ),
        )
        for n in raw["nurses"]
    )
    # demand normalization
    demand = {}
    for d_idx, per_day in enumerate(raw["demand"]):
        for s_idx, per_shift in enumerate(per_day):
            for k, req in per_shift.items():
                demand[(d_idx, shifts[s_idx], k)] = req

    return Instance(
        start_date=date.fromisoformat(raw["horizon"]["startDate"]),
        days=raw["horizon"]["days"],
        shifts=shifts,
        skills=skills,
        nurses=nurses,
        demand=demand,
        forbidden_pairs=tuple(tuple(p) for p in raw.get("forbiddenTransitions", [["N","D"]])),
    )
```

```python
# apps/py-cp-sat/ch11-nsp-v1/src/model.py
from ortools.sat.python import cp_model

from .instance import Instance, Nurse


def _cell_allowed(n: Nurse, d: int, s: str) -> bool:
    for (ud, us) in n.unavailable:
        if ud == d and (us is None or us == s):
            return False
    return True


def build_model(inst: Instance):
    model = cp_model.CpModel()
    N, D, S = inst.nurses, range(inst.days), inst.shifts

    # ----- Variables -----
    x: dict[tuple[str, int, str], cp_model.IntVar] = {}
    for n in N:
        for d in D:
            for s in S:
                if _cell_allowed(n, d, s):
                    x[(n.id, d, s)] = model.new_bool_var(f"x_{n.id}_{d}_{s}")
                # else: variable omitted → H7 enforced structurally

    # Fixed assignments
    for n in N:
        for (fd, fs) in n.fixed:
            if (n.id, fd, fs) not in x:
                raise ValueError(f"Fixed assignment conflicts with unavailability: {n.id} {fd} {fs}")
            model.add(x[(n.id, fd, fs)] == 1)

    # ----- Constraints -----
    # H1: coverage per (d, s, k)
    for (d, s, k), required in inst.demand.items():
        eligible = [x[(n.id, d, s)] for n in N if k in n.skills and (n.id, d, s) in x]
        model.add(sum(eligible) >= required)

    # H2: at most one shift per day per nurse
    for n in N:
        for d in D:
            cells = [x[(n.id, d, s)] for s in S if (n.id, d, s) in x]
            if cells:
                model.add(sum(cells) <= 1)

    # H3: forbidden transitions
    for n in N:
        for d in range(inst.days - 1):
            for (s1, s2) in inst.forbidden_pairs:
                if (n.id, d, s1) in x and (n.id, d + 1, s2) in x:
                    model.add(x[(n.id, d, s1)] + x[(n.id, d + 1, s2)] <= 1)

    # H5: max consecutive working days
    for n in N:
        W = n.max_consec + 1
        if W > inst.days:
            continue
        for d0 in range(inst.days - W + 1):
            window = []
            for d in range(d0, d0 + W):
                for s in S:
                    if (n.id, d, s) in x:
                        window.append(x[(n.id, d, s)])
            if window:
                model.add(sum(window) <= n.max_consec)

    # H6: min/max shifts per horizon
    for n in N:
        total = [x[(n.id, d, s)] for d in D for s in S if (n.id, d, s) in x]
        model.add(sum(total) >= n.min_shifts)
        model.add(sum(total) <= n.max_shifts)

    return model, x
```

```python
# apps/py-cp-sat/ch11-nsp-v1/src/solve.py
import argparse
import sys

from ortools.sat.python import cp_model

from .instance import load
from .model import build_model
from .render import render_ascii


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("instance")
    parser.add_argument("--time-limit", type=float, default=30.0)
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--log", action="store_true")
    args = parser.parse_args(argv)

    inst = load(args.instance)
    model, x = build_model(inst)
    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = args.time_limit
    solver.parameters.num_search_workers = args.workers
    solver.parameters.log_search_progress = args.log

    status = solver.solve(model)
    print(f"Status: {solver.status_name(status)}")
    print(f"Wall time: {solver.wall_time:.2f}s   branches: {solver.num_branches}")

    if status in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        print(render_ascii(inst, solver, x))
        return 0
    if status == cp_model.INFEASIBLE:
        print("Model is infeasible. Rerun with --log to inspect.")
        return 2
    return 1


if __name__ == "__main__":
    sys.exit(main())
```

```python
# apps/py-cp-sat/ch11-nsp-v1/src/render.py
from .instance import Instance


def render_ascii(inst: Instance, solver, x) -> str:
    lines = []
    header = "     | " + " ".join(f"d{d:02d}" for d in range(inst.days))
    lines.append(header)
    lines.append("-" * len(header))
    for n in inst.nurses:
        row = [f"{n.id:<4} |"]
        for d in range(inst.days):
            cell = "off"
            for s in inst.shifts:
                if (n.id, d, s) in x and solver.value(x[(n.id, d, s)]) == 1:
                    cell = f" {s} "
                    break
            row.append(cell.rjust(3))
        lines.append(" ".join(row))
    return "\n".join(lines)
```

```python
# tools/validate-schedule.py — standalone validator
"""Given an instance JSON and a schedule JSON, verify every hard constraint."""
import json, sys, pathlib
from collections import Counter

def validate(instance_path, schedule_path):
    inst = json.loads(pathlib.Path(instance_path).read_text())
    sched = json.loads(pathlib.Path(schedule_path).read_text())

    errors = []
    # H1 coverage
    for d in range(inst["horizon"]["days"]):
        for s_idx, s in enumerate(sh["id"] for sh in inst["shifts"]):
            for k, required in inst["demand"][d][s_idx].items():
                staffed = sum(1 for row in sched["assignments"]
                              if row["day"] == d and row["shift"] == s
                              and k in next(n["skills"] for n in inst["nurses"]
                                            if n["id"] == row["nurse"]))
                if staffed < required:
                    errors.append(f"Coverage: d{d}/{s}/{k} needs {required}, got {staffed}")
    # ... H2–H8 (omitted for brevity; implement each) ...
    return errors

if __name__ == "__main__":
    errs = validate(sys.argv[1], sys.argv[2])
    if errs:
        print("\n".join(errs)); sys.exit(1)
    print("Schedule is valid.")
```

Run:

```bash
cd apps/py-cp-sat/ch11-nsp-v1
uv run python -m src.solve ../../../data/nsp/toy-01.json --log
```

## 5. Kotlin implementation (via `cpsat-kt`)

Project layout: `apps/kt-cp-sat/ch11-nsp-v1/`.

```kotlin
// apps/kt-cp-sat/ch11-nsp-v1/src/main/kotlin/Instance.kt
package ch11

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText

@Serializable data class Horizon(val startDate: String, val days: Int)
@Serializable data class ShiftDef(val id: String, val startHour: Int, val endHour: Int)
@Serializable data class Unavailable(val day: Int, val shift: String? = null)
@Serializable data class Fixed(val day: Int, val shift: String)
@Serializable data class NurseDef(
    val id: String,
    val skills: List<String>,
    val minShifts: Int,
    val maxShifts: Int,
    val maxConsec: Int,
    val unavailable: List<Unavailable> = emptyList(),
    val fixed: List<Fixed> = emptyList(),
)
@Serializable data class InstanceDef(
    val horizon: Horizon,
    val shifts: List<ShiftDef>,
    val skills: List<String>,
    val nurses: List<NurseDef>,
    val demand: List<List<Map<String, Int>>>,
    val forbiddenTransitions: List<List<String>> = listOf(listOf("N", "D")),
)

object InstanceLoader {
    private val json = Json { ignoreUnknownKeys = true }
    fun load(p: Path): InstanceDef = json.decodeFromString(p.readText())
}
```

```kotlin
// apps/kt-cp-sat/ch11-nsp-v1/src/main/kotlin/Model.kt
package ch11

import io.vanja.cpsat.*

data class ModelVars(
    val x: Map<Triple<String, Int, String>, BoolVar>,
    val instance: InstanceDef,
)

fun buildModel(inst: InstanceDef): Pair<CpModel, ModelVars> {
    val N = inst.nurses
    val D = 0 until inst.horizon.days
    val S = inst.shifts.map { it.id }

    lateinit var vars: ModelVars

    val model = cpModel {
        val x = HashMap<Triple<String, Int, String>, BoolVar>()

        fun cellAllowed(n: NurseDef, d: Int, s: String): Boolean =
            n.unavailable.none { it.day == d && (it.shift == null || it.shift == s) }

        for (n in N) for (d in D) for (s in S) {
            if (cellAllowed(n, d, s)) {
                x[Triple(n.id, d, s)] = boolVar("x_${n.id}_${d}_$s")
            }
        }

        // Fixed assignments
        for (n in N) for (f in n.fixed) {
            val key = Triple(n.id, f.day, f.shift)
            require(key in x) { "Fixed-assignment conflict: ${n.id} d${f.day} ${f.shift}" }
            constraint { x[key]!! eq 1 }
        }

        // H1: coverage per (d, s, k)
        for (d in D) for ((sIdx, s) in S.withIndex()) {
            for ((k, required) in inst.demand[d][sIdx]) {
                val eligible = N.filter { k in it.skills }
                    .mapNotNull { x[Triple(it.id, d, s)] }
                if (eligible.isNotEmpty()) {
                    constraint { sum(eligible) ge required.toLong() }
                }
            }
        }

        // H2: at most one shift per day per nurse
        for (n in N) for (d in D) {
            val cells = S.mapNotNull { x[Triple(n.id, d, it)] }
            if (cells.isNotEmpty()) constraint { sum(cells) le 1 }
        }

        // H3: forbidden transitions
        for (n in N) for (d in 0 until inst.horizon.days - 1) {
            for (pair in inst.forbiddenTransitions) {
                val (s1, s2) = pair[0] to pair[1]
                val a = x[Triple(n.id, d, s1)]
                val b = x[Triple(n.id, d + 1, s2)]
                if (a != null && b != null) constraint { a + b le 1 }
            }
        }

        // H5: max consecutive working days
        for (n in N) {
            val W = n.maxConsec + 1
            if (W > inst.horizon.days) continue
            for (d0 in 0..inst.horizon.days - W) {
                val window = buildList {
                    for (d in d0 until d0 + W) for (s in S) {
                        x[Triple(n.id, d, s)]?.let { add(it) }
                    }
                }
                if (window.isNotEmpty()) {
                    constraint { sum(window) le n.maxConsec.toLong() }
                }
            }
        }

        // H6: min/max shifts per horizon
        for (n in N) {
            val total = buildList {
                for (d in D) for (s in S) {
                    x[Triple(n.id, d, s)]?.let { add(it) }
                }
            }
            constraint { sum(total) ge n.minShifts.toLong() }
            constraint { sum(total) le n.maxShifts.toLong() }
        }

        vars = ModelVars(x, inst)
    }
    return model to vars
}
```

```kotlin
// apps/kt-cp-sat/ch11-nsp-v1/src/main/kotlin/Main.kt
package ch11

import io.vanja.cpsat.SolveResult
import java.nio.file.Paths

fun main(args: Array<String>) {
    val inst = InstanceLoader.load(Paths.get(args[0]))
    val (model, vars) = buildModel(inst)

    val result = model.solveBlocking {
        maxTimeInSeconds = 30.0
        numSearchWorkers = 8
        logSearchProgress = args.contains("--log")
    }

    when (result) {
        is SolveResult.Optimal, is SolveResult.Feasible -> println(renderAscii(inst, vars, result.values))
        is SolveResult.Infeasible -> { println("Infeasible."); kotlin.system.exitProcess(2) }
        is SolveResult.Unknown -> { println("Time limit reached."); kotlin.system.exitProcess(1) }
        is SolveResult.ModelInvalid -> error(result.message)
    }
}

fun renderAscii(inst: InstanceDef, v: ModelVars, assn: Assignment): String {
    val lines = mutableListOf<String>()
    lines += "     | " + (0 until inst.horizon.days).joinToString(" ") { "d%02d".format(it) }
    lines += "-".repeat(lines.first().length)
    for (n in inst.nurses) {
        val row = StringBuilder("${n.id.padEnd(4)} |")
        for (d in 0 until inst.horizon.days) {
            var cell = "off"
            for (s in inst.shifts.map { it.id }) {
                val b = v.x[Triple(n.id, d, s)]
                if (b != null && assn[b]) { cell = " $s "; break }
            }
            row.append(" ").append(cell.padStart(3))
        }
        lines += row.toString()
    }
    return lines.joinToString("\n")
}
```

Run:

```bash
cd apps/kt-cp-sat/ch11-nsp-v1
./gradlew run --args="../../../data/nsp/toy-01.json --log"
```

## 6. MiniZinc implementation (skip)

We already proved MiniZinc carries its weight on small models in Chapter 8. For NSP v1 it would just be a translation — not worth the ceremony. You can revisit in Chapter 13 to sanity-check benchmark instance parses.

## 7. Comparison & takeaways

| Observation | Python | Kotlin |
|---|---|---|
| LOC for the solver | ~120 | ~150 (more ceremony for kotlinx-serialization) |
| Readability of model code | **Win** (operator overloading) | Very close (with `cpsat-kt` DSL) |
| Validation-first path (JSON Schema) | native jsonschema | `kotlinx-serialization` + optional external JSON Schema |
| Solve wall-time | ~0.1s on toy | ~0.1s on toy |
| Observability (log) | identical | identical |

**Key insight:** For NSP, **skip the variable when `avail=0`**. Don't create it and then constrain it to 0. Skipping is smaller, tighter, and lets presolve eliminate the entire skeleton. This is the single biggest "pattern" payoff in the chapter.

## 8. Exercises

**Exercise 11-A: Tighten coverage until infeasible.** Crank Day-shift demand from 2 to 5 (you only have 5 nurses total, many of whom can't be on Day every day). Run. Capture the `INFEASIBLE` status and the log. *Prove infeasibility via assumptions + `sufficient_assumptions_for_infeasibility()`*: wrap each demand constraint in an assumption literal, ask the solver for the minimal infeasible subset, and show which day is the bottleneck.

<details><summary>Hint</summary>
```python
assums = {}
for key, val in demand_constraints.items():
    a = model.new_bool_var(f"assume_{key}"); assums[a] = key
    val.only_enforce_if(a)
    model.add_assumption(a)
solver.solve(model)
core = solver.sufficient_assumptions_for_infeasibility()
```
Acceptance: script prints the offending `(d, s, k)` tuples.
</details>

**Exercise 11-B: Add nurse #6.** Extend `toy-01.json` to 6 nurses (one new senior, junior, or mix — your choice). Re-run. Does wall-time change? Does the shape of the schedule change? Commit `toy-02.json` and your observations.

<details><summary>Hint</summary>
Wall-time might even go down because coverage becomes slack. Note: more nurses means more variables but also more flexibility — classical trade-off.
</details>

**Exercise 11-C: Skill-level granularity.** Replace the two-tier `junior/senior` with three levels `junior/mid/senior`. Require "at least 1 senior AND at least 1 mid-or-better" per Night shift. Write it as two coverage rows on the demand side, not as a new constraint type.

<details><summary>Hint</summary>
Add `"mid-or-better"` as a virtual skill tag on every non-junior nurse. Coverage constraint uses the virtual tag. Avoids hand-coded logic.
</details>

**Exercise 11-D: Presolve inspection.** Turn on `fill_tightened_domains_in_response = True`. Inspect how many variables survived presolve. Compare with your unavailability-skip trick (step in §4): try both the "skip cell" variant and the "create var + constrain to 0" variant. Record the presolve size of each.

<details><summary>Hint</summary>
```python
solver.parameters.fill_tightened_domains_in_response = True
resp = solver.response_proto
print(resp.tightened_variables)
```
</details>

**Exercise 11-E: Certificate round-trip.** After solving, dump the schedule as JSON (`{ assignments: [{nurse, day, shift}, ...] }`). Feed it through `tools/validate-schedule.py`. Make the validator output "OK" or the list of violated constraints. No solver involvement.

<details><summary>Hint</summary>
Implement H2–H8 in the validator. Test by mutating one cell and confirming the validator catches it.
</details>

## 9. Self-check

<details><summary>Q1: Is your schedule a certificate you could hand to a nurse manager?</summary>
Only if the validator in Exercise 11-E says OK and the rendered ASCII is readable. The validator is the certificate; the ASCII is the human-readable view.
</details>

<details><summary>Q2: Can you prove infeasibility when coverage is too tight?</summary>
Yes — via `add_assumption` on each coverage constraint, run solve, then `sufficient_assumptions_for_infeasibility()` returns a minimal conflicting set. That's a formal infeasibility certificate.
</details>

<details><summary>Q3: How long does it take? Is the presolved model size what you expected?</summary>
Toy-01: sub-second, ~200 binary vars after presolve (some get pinned by fixed assignments and availability gaps). If you see 420 raw vars, check whether you created cells for unavailable slots.
</details>

<details><summary>Q4: Why is "skip unavailable cells" better than "create and constrain to 0"?</summary>
Both are logically equivalent. But skipping means 0 variables, 0 constraints for those cells — nothing for propagators to chew on. Creating-then-constraining gives presolve extra work to collapse. Presolve usually succeeds, but why pay the tax?
</details>

<details><summary>Q5: Where do forbidden transitions fit — H3 or H4?</summary>
Both. H4 (min rest hours) reduces to H3 (forbidden pairs) under fixed shift timings. You only need the pair table; the "11h" number is pre-baked into which pairs are listed.
</details>

## 10. What this unlocks

With a feasible-only NSP model in hand, you're ready to add an objective — preferences, fairness, workload balance — in **Chapter 12**.

## 11. Further reading

- [OR-Tools employee scheduling](https://developers.google.com/optimization/scheduling/employee_scheduling) — production-flavoured worked example using exactly this Boolean-grid shape.
- [OR-Tools `shift_scheduling_sat.py`](https://github.com/google/or-tools/blob/stable/examples/python/shift_scheduling_sat.py) — larger production-style reference; note the `negated_bounded_span` helper.
- Van den Bergh et al., "Personnel scheduling: A literature review," *EJOR* 226, 2013. [DOI 10.1016/j.ejor.2012.11.029](https://doi.org/10.1016/j.ejor.2012.11.029) — positions hard-constraint NSP within the broader family.
- Ceschia et al., "The second international nurse rostering competition," *Ann. OR* 292, 2019. [DOI 10.1007/s10479-018-2816-0](https://doi.org/10.1007/s10479-018-2816-0) — the dataset you'll graduate to in Chapter 13.
- [JSON Schema draft 2020-12 spec](https://json-schema.org/draft/2020-12/release-notes.html) — for `nsp-instance.schema.json`.
- [`docs/knowledge/nurse-scheduling/overview.md`](../knowledge/nurse-scheduling/overview.md) — full hard/soft-constraint taxonomy reference.
