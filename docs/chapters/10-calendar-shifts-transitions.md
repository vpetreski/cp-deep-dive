# Chapter 10 — Calendar, Shifts, Transitions

> **Phase 4: Scheduling primitives** · Estimated: ~3h · Status: ready-to-start · Last updated: 2026-04-19

## Goal

Encode *calendar time*, shifts-per-day, and the **transition rules** between shifts — the beating heart of the Nurse Scheduling Problem. By the end of this chapter you can take a one-nurse, 14-day toy instance, express hard rules like "no morning after night" and "max 5 consecutive working days," and solve it in both Python and Kotlin.

## Before you start

- **Prerequisites:** Chapters 6 (global constraints — especially `Automaton`) and 9 (`IntervalVar`, `NoOverlap`, `Cumulative`).
- **Required reading:**
  - [`docs/knowledge/nurse-scheduling/overview.md`](../knowledge/nurse-scheduling/overview.md) §3 (Formal model), §4 (Hard constraints H3, H4, H5, H6, H12, H13), §10.4–10.6 (CP-SAT patterns).
  - [`docs/knowledge/cp-sat/overview.md`](../knowledge/cp-sat/overview.md) §4 (Automaton) and §8 (Scheduling primitives).
- **Environment:** `uv sync` in `apps/py-cp-sat/ch10-shifts/`, Gradle composite build in `apps/kt-cp-sat/ch10-shifts/`. `cpsat-kt` must support `automaton(...)` (added in Chapter 6); if missing, add it before starting.

## Concepts introduced this chapter

- **Calendar horizon** — a linear index `d ∈ {0, …, D-1}` mapped to dates + weekday flags.
- **Boolean shift-grid** — 3-D tensor `x[n, d, s] ∈ {0,1}` where 1 means "nurse n works shift s on day d."
- **Interval formulation** — one `OptionalInterval` per `(n, d, s)`; presence literal = the Boolean variable from the grid. Same information, different shape.
- **Forbidden transition** — an ordered pair `(s1, s2)` such that `s1` on day `d` followed by `s2` on day `d+1` is illegal.
- **Automaton (DFA) encoding** — states capture "what happened yesterday"; transitions encode legal moves; `AddAutomaton` forbids everything else in one constraint.
- **Sliding-window constraint** — `∑ x over any k consecutive days ≤ cap` enforces counting rules like "at most 5 in any 7 days."
- **Minimum rest** — gap between end of shift `s1` and start of `s2` expressed as a forbidden pair (or as interval separation if you're using intervals).

## 1. Intuition

Imagine a nurse's month as a filmstrip, one frame per day. Each frame is one of `{Off, Morning, Evening, Night}`. The shift schedule is *just a sequence of frames*. Most interesting rules are **local**: "this frame depends on the previous one." Night-to-morning is illegal (no rest). Five working frames in a row, then you need an off frame. Four nights in a row is forbidden.

Two clean ways to express this:

1. **Boolean grid.** Lay out a 3-D sheet of 0/1 cells. Row = nurse, column = day, layer = shift. Every rule becomes a linear constraint over slices of this sheet. Dead simple; how 99% of real rostering systems actually look.
2. **Automaton.** Treat the nurse's day-to-day sequence as a string over the alphabet `{Off, M, E, N}`. The hospital's rulebook is a regular language. `AddAutomaton` says: this string must be accepted by this DFA. Cleanest when the rules are complex patterns ("after 2 nights, 2 off"); messier when they're raw arithmetic caps.

You'll build **both** here. Then you'll know which knob to reach for in Chapter 11 when the full NSP arrives.

## 2. Formal definition

### 2.1 Sets and parameters

```
D = {0, 1, …, 13}                             days in horizon (14-day toy)
S = {Off, Morning, Evening, Night}            shift codes
hours(s) : S → ℕ                              Off=0, Morning=8, Evening=8, Night=10
weekday(d) ∈ {Mon, …, Sun}                    derived from calendar anchor
isWeekend(d) = weekday(d) ∈ {Sat, Sun}
maxConsec = 5                                 max consecutive working days
forbidden ⊂ S × S                             illegal ordered pairs
```

### 2.2 Decision variables

**Boolean grid formulation** (one nurse for simplicity):

```
x[d, s] ∈ {0, 1}     ∀ d ∈ D, ∀ s ∈ S
```
with `x[d, s] = 1` iff the nurse works shift `s` on day `d`.

**Constraint: exactly one label per day.**

```
∑_{s ∈ S} x[d, s] = 1     ∀ d ∈ D
```

Notice `Off` is a shift: it counts toward "at most one" but contributes zero hours.

### 2.3 Core constraints

| # | Rule | Formula |
|---|---|---|
| T1 | Forbidden transition `(s1, s2)` | `x[d, s1] + x[d+1, s2] ≤ 1`  ∀d < D-1, ∀(s1,s2)∈forbidden |
| T2 | Max 5 consecutive working days | `∑_{d'=d..d+5} (1 − x[d', Off]) ≤ 5`  ∀d such that `d+5 ≤ D-1` |
| T3 | Min 11h rest between shifts | Expressed as a forbidden pair `(Night, Morning)` if Night ends 06:00 and Morning starts 07:00 → only 1h rest → forbidden. The rest rule **reduces to T1** under fixed shift timings. |
| T4 | Exactly 2 weekends off in 4 weeks | `∑_{d ∈ Saturdays} x[d, Off] ≥ 2`  AND  (if Sat off, then Sun off — complete-weekend coupling) |

### 2.4 Automaton view

Alphabet: `{Off=0, M=1, E=2, N=3}`.

States track "what did I do on the previous few days?" Minimum viable DFA for "no N→M, no 6-in-a-row":

```
States: 0 = reset / start
        1..5 = "I have worked k consecutive days (last shift was not N)"
        6..8 = "I just finished a Night on day d−1"

Start state = 0
Accepting states = {0, 1, …, 5}   # any reached terminal state is fine

Transition triples (from, label, to):
  (0, Off, 0)
  (0, M,   1)   (0, E,   1)   (0, N,   6)
  (1, Off, 0)
  (1, M,   2)   (1, E,   2)   (1, N,   6)
  …
  (5, Off, 0)   # must go Off; no transition to M/E/N → implicit reject
  (6, Off, 0)
  (6, M, --- )  # NO triple → rejected: "no morning after night"
  (6, E, 7)
  (6, N, 8)
  (7, Off, 0)   (7, M, 2)   (7, E, 2)   (7, N, 8)
  (8, Off, 0)   (8, N, --- ) # 2 nights then the third N is rejected? depends on rule
```

Any triple not listed is implicitly forbidden. The `AddAutomaton` constraint accepts the sequence if and only if it can walk from start to an accepting state using only listed triples.

### 2.5 Cross-language mapping

| Concept | Python CP-SAT | Kotlin `cpsat-kt` |
|---|---|---|
| Bool grid cell | `model.new_bool_var(f"x_{d}_{s}")` | `boolVar("x_${d}_$s")` |
| Exactly-one | `model.add_exactly_one([x[d,s] for s in S])` | `exactlyOne(S.map { x[d to it] })` |
| Forbidden pair | `model.add(x[d,s1] + x[d+1,s2] <= 1)` | `constraint { x[d, s1] + x[d+1, s2] le 1 }` |
| Sliding window | `model.add(sum(...) <= maxConsec)` | `constraint { sum(...) le maxConsec.toLong() }` |
| Automaton | `model.add_automaton(transition_vars, start, finals, triples)` | `automaton(vars, start, finals, triples)` |
| Interval | `model.new_optional_interval_var(start, size, end, present, name)` | `interval(name) { start=..; size=..; end=..; presence=present }` |

## 3. Worked example by hand

**Instance:** 1 nurse, 3 days, shifts `{Off, M, N}`, forbidden pair `(N, M)`, `maxConsec = 2` working days.

Enumerate all `3^3 = 27` sequences, filter by constraints:

| Day 0 | Day 1 | Day 2 | One-per-day? | No N→M? | ≤2 consecutive working? | Feasible |
|---|---|---|---|---|---|---|
| Off | Off | Off | y | y | y | Y |
| Off | Off | M | y | y | y | Y |
| Off | Off | N | y | y | y | Y |
| Off | M | Off | y | y | y | Y |
| Off | M | M | y | y | y | Y |
| Off | M | N | y | y | y | Y |
| Off | N | Off | y | y | y | Y |
| Off | N | M | y | **NO (N→M)** | — | N |
| Off | N | N | y | y | y | Y |
| M | Off | Off | y | y | y | Y |
| M | Off | M | y | y | y | Y |
| M | Off | N | y | y | y | Y |
| M | M | Off | y | y | y | Y |
| M | M | M | y | y | **NO (3 in a row)** | N |
| M | M | N | y | y | **NO (3 in a row)** | N |
| M | N | Off | y | y | y | Y |
| M | N | M | y | **NO** | — | N |
| M | N | N | y | y | **NO (3 in a row)** | N |
| N | Off | Off | y | y | y | Y |
| N | Off | M | y | y | y | Y |
| N | Off | N | y | y | y | Y |
| N | M | … | y | **NO (N→M)** | — | N |
| N | N | Off | y | y | y | Y |
| N | N | M | y | **NO** | — | N |
| N | N | N | y | y | **NO (3 in a row)** | N |

Count feasible: **17 out of 27**. Run your solver below with `enumerate_all_solutions = True` and check you get 17. If not, you have a bug.

## 4. Python implementation

Project layout: `apps/py-cp-sat/ch10-shifts/`.

```python
# apps/py-cp-sat/ch10-shifts/shifts_grid.py
"""Boolean-grid encoding of a 1-nurse, 14-day schedule."""
from dataclasses import dataclass
from enum import IntEnum
from datetime import date, timedelta

from ortools.sat.python import cp_model


class Shift(IntEnum):
    OFF = 0
    MORNING = 1
    EVENING = 2
    NIGHT = 3


SHIFT_HOURS = {Shift.OFF: 0, Shift.MORNING: 8, Shift.EVENING: 8, Shift.NIGHT: 10}
FORBIDDEN_PAIRS = [
    (Shift.NIGHT, Shift.MORNING),   # insufficient rest
    (Shift.NIGHT, Shift.EVENING),   # house rule
]


@dataclass(frozen=True)
class Instance:
    start_date: date
    horizon_days: int
    max_consec_working: int = 5
    min_weekends_off: int = 2


def build_model(inst: Instance):
    model = cp_model.CpModel()
    D = range(inst.horizon_days)
    S = list(Shift)

    # x[d, s] = 1 iff working shift s on day d
    x = {(d, s): model.new_bool_var(f"x_d{d}_s{s.name}") for d in D for s in S}

    # One label per day (Off counts as a label).
    for d in D:
        model.add_exactly_one(x[d, s] for s in S)

    # Forbidden transitions.
    for d in list(D)[:-1]:
        for (s1, s2) in FORBIDDEN_PAIRS:
            model.add(x[d, s1] + x[d + 1, s2] <= 1)

    # Max consecutive working days (sliding window of size maxConsec+1).
    W = inst.max_consec_working + 1
    for d0 in range(inst.horizon_days - W + 1):
        working_in_window = [
            1 - x[d, Shift.OFF]     # 1 when the nurse is on shift, 0 on Off
            for d in range(d0, d0 + W)
        ]
        model.add(sum(working_in_window) <= inst.max_consec_working)

    # Weekends off: Saturday-and-Sunday coupling + min count.
    saturdays = [d for d in D if (inst.start_date + timedelta(days=d)).weekday() == 5]
    for sat in saturdays:
        sun = sat + 1
        if sun < inst.horizon_days:
            # Complete-weekend: if Sat is Off then Sun is Off (and vice versa).
            model.add(x[sat, Shift.OFF] == x[sun, Shift.OFF])

    model.add(sum(x[d, Shift.OFF] for d in saturdays) >= inst.min_weekends_off)

    return model, x


def solve_and_print(inst: Instance):
    model, x = build_model(inst)
    solver = cp_model.CpSolver()
    solver.parameters.log_search_progress = False
    status = solver.solve(model)

    status_name = solver.status_name(status)
    print(f"Status: {status_name}")
    if status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        return

    print("Day | Date       | Shift")
    for d in range(inst.horizon_days):
        the_date = inst.start_date + timedelta(days=d)
        shift = next(s for s in Shift if solver.value(x[d, s]) == 1)
        weekend = "*" if the_date.weekday() >= 5 else " "
        print(f"{d:3d} | {the_date} | {shift.name:<7} {weekend}")


if __name__ == "__main__":
    solve_and_print(Instance(start_date=date(2026, 5, 4), horizon_days=14))
```

Run it:

```
uv run python shifts_grid.py
```

Expected: a printed 14-day schedule with no `NIGHT → MORNING` transitions, at most 5 consecutive working days, and at least 2 Saturdays off (with matched Sundays).

### Interval formulation (alternative)

```python
# apps/py-cp-sat/ch10-shifts/shifts_intervals.py
"""Interval-based encoding of the same problem."""
from ortools.sat.python import cp_model

from shifts_grid import Instance, Shift, SHIFT_HOURS


def build_model_intervals(inst: Instance):
    model = cp_model.CpModel()

    # Shift start times in hours from horizon start. Each day has 3 windows.
    shift_windows = {
        Shift.MORNING: (7, 15),   # 07:00–15:00
        Shift.EVENING: (15, 23),
        Shift.NIGHT: (23, 33),    # crosses midnight (uses +24h clock)
    }
    horizon_hours = inst.horizon_days * 24 + 24  # buffer for overnight

    intervals = []
    presence = {}
    for d in range(inst.horizon_days):
        for s in (Shift.MORNING, Shift.EVENING, Shift.NIGHT):
            start_h, end_h = shift_windows[s]
            start = model.new_constant(d * 24 + start_h)
            end = model.new_constant(d * 24 + end_h)
            p = model.new_bool_var(f"present_d{d}_s{s.name}")
            iv = model.new_optional_interval_var(
                start, end_h - start_h, end, p, f"iv_d{d}_s{s.name}"
            )
            intervals.append(iv)
            presence[d, s] = p

    # One shift per day: at most one interval per d is present.
    for d in range(inst.horizon_days):
        model.add_at_most_one(presence[d, s] for s in
                              (Shift.MORNING, Shift.EVENING, Shift.NIGHT))

    # NoOverlap on the nurse's intervals — enforces the 11h rest rule automatically,
    # because Night (23:00–09:00 next day) overlaps with next-day Morning (07:00).
    model.add_no_overlap(intervals)

    return model, presence


if __name__ == "__main__":
    from datetime import date
    inst = Instance(start_date=date(2026, 5, 4), horizon_days=14)
    model, presence = build_model_intervals(inst)
    solver = cp_model.CpSolver()
    status = solver.solve(model)
    print(solver.status_name(status))
```

## 5. Kotlin implementation (via `cpsat-kt`)

Project layout: `apps/kt-cp-sat/ch10-shifts/`, composite-build consuming `libs/cpsat-kt`.

```kotlin
// apps/kt-cp-sat/ch10-shifts/src/main/kotlin/Shifts.kt
package ch10

import io.vanja.cpsat.*
import java.time.DayOfWeek
import java.time.LocalDate

enum class Shift(val hours: Long) {
    OFF(0), MORNING(8), EVENING(8), NIGHT(10);
}

val FORBIDDEN_PAIRS = listOf(
    Shift.NIGHT to Shift.MORNING,
    Shift.NIGHT to Shift.EVENING,
)

data class Instance(
    val startDate: LocalDate,
    val horizonDays: Int,
    val maxConsecWorking: Int = 5,
    val minWeekendsOff: Int = 2,
)

fun solve(inst: Instance) {
    val model = cpModel {
        val D = 0 until inst.horizonDays
        val S = Shift.values().toList()

        // x[d, s] = 1 iff working shift s on day d
        val x = HashMap<Pair<Int, Shift>, BoolVar>()
        for (d in D) for (s in S) {
            x[d to s] = boolVar("x_d${d}_s${s.name}")
        }

        // One label per day
        for (d in D) {
            exactlyOne(S.map { x[d to it]!! })
        }

        // Forbidden transitions
        for (d in 0 until inst.horizonDays - 1) {
            for ((s1, s2) in FORBIDDEN_PAIRS) {
                constraint { x[d to s1]!! + x[(d + 1) to s2]!! le 1 }
            }
        }

        // Max consecutive working days
        val W = inst.maxConsecWorking + 1
        for (d0 in 0..inst.horizonDays - W) {
            val working = (d0 until d0 + W).map { d ->
                // "working" = 1 - x[d, Off]
                constant(1) - x[d to Shift.OFF]!!
            }
            constraint { sum(working) le inst.maxConsecWorking.toLong() }
        }

        // Complete weekends + min weekends off
        val saturdays = D.filter {
            inst.startDate.plusDays(it.toLong()).dayOfWeek == DayOfWeek.SATURDAY
        }
        for (sat in saturdays) {
            val sun = sat + 1
            if (sun < inst.horizonDays) {
                constraint { x[sat to Shift.OFF]!! eq x[sun to Shift.OFF]!! }
            }
        }
        constraint {
            sum(saturdays.map { x[it to Shift.OFF]!! }) ge inst.minWeekendsOff.toLong()
        }
    }

    when (val r = model.solveBlocking { logSearchProgress = false }) {
        is SolveResult.Optimal, is SolveResult.Feasible -> printSchedule(inst, r.values)
        is SolveResult.Infeasible -> println("No feasible schedule.")
        is SolveResult.Unknown -> println("Time limit reached.")
        is SolveResult.ModelInvalid -> error(r.message)
    }
}

// omitted: printSchedule helper — iterate days, pick the true shift, print row
```

Same public contract, same output.

### Automaton in `cpsat-kt`

```kotlin
// Sketch: a DFA that forbids N→M and caps consecutive work at 5
val seq = List(inst.horizonDays) { d ->
    intVar("lab_d$d", Shift.values().map { it.ordinal.toLong() })
}
automaton(
    sequence = seq,
    startState = 0,
    finalStates = listOf(0L, 1, 2, 3, 4, 5, 6, 7, 8),
    transitions = listOf(
        Triple(0L, Shift.OFF.ordinal.toLong(), 0L),
        Triple(0L, Shift.MORNING.ordinal.toLong(), 1L),
        // ... full table, mirroring §2.4
    )
)
```

## 6. MiniZinc implementation (skip)

Skip MiniZinc this chapter; you already did the declarative exercise in Chapter 8. For NSP-scale models MiniZinc's `regular`/`cumulative` become useful — revisit in Chapter 12 if curious.

## 7. Comparison & takeaways

| Criterion | Boolean grid | Interval formulation | Automaton |
|---|---|---|---|
| Best when | Fixed-timing shifts, coverage is a sum | Start times are decision variables (home care) | Complex pattern rules ("no 2 nights then day") |
| Rule style | Linear arithmetic | Resource-style (`NoOverlap`) | Regular-language |
| Debugging ease | Trivial (0/1 cells) | Medium | Harder (DFA state) |
| CP-SAT performance on NSP | **Best** (per OR-Tools docs) | Middle | Great for pattern-heavy rules |
| Expressive for rest gap | Forbidden-pair lookup | Free (via `NoOverlap`) | Encoded in states |
| Size | `O(N·D·|S|)` booleans | `O(N·D·|S|)` intervals | `O(N·D)` labels + DFA |

**Key insight:** For fixed-timing NSP, the **Boolean grid is king.** The interval formulation shines when start times are part of the decision (task scheduling, home-care routing). The automaton wins when rules are best expressed as patterns. You'll use the Boolean grid as the primary encoding in Chapter 11 and reach for automaton only for stubborn pattern rules.

## 8. Exercises

Each exercise has hints in a `<details>` block. Commit a solution to `apps/*/ch10-shifts/exercises/`.

**Exercise 10-A: Weekend-off count.** Modify the model so that across a 28-day horizon the nurse gets *at least* 2 full weekends off (Sat AND Sun both Off). Currently we enforce "Sat Off ⇔ Sun Off" via an equality. Generalize to N weekends.

<details><summary>Hint</summary>
Introduce `weekendOff[w] = x[sat_w, Off] ∧ x[sun_w, Off]` via `AddBoolAnd` / `enforceIf`. Then `sum(weekendOff) ≥ 2`. Acceptance: 28-day horizon with demand so tight that exactly 2 weekends are possible — solver must find them.
</details>

**Exercise 10-B: Encode "exactly 2 weekends off in 4 weeks."** Upgrade from `≥ 2` to `= 2`. Prove the change is visible in the search log (fewer feasible solutions enumerated).

<details><summary>Hint</summary>
Swap `≥` for `==`. Run with `enumerate_all_solutions = True` and a tiny horizon (4 weekends only). The count should drop.
</details>

**Exercise 10-C: Sketch the automaton.** Draw a state machine that forbids "more than 3 consecutive nights." Paste as ASCII art in your chapter note; implement with `AddAutomaton` in Python and verify it produces the same feasibility set as a sliding-window sum on `Night`-only booleans.

<details><summary>Hint</summary>
4 states: 0 = no recent night, 1 = 1 night yesterday, 2 = 2 nights, 3 = 3 nights. Transition on N from state `k` to `k+1` for `k<3`; from state 3 on N → no triple (reject). Any non-N in any state → state 0.
</details>

**Exercise 10-D: Hours cap.** Add a hard 40h/week cap. Use `SHIFT_HOURS[s] * x[d, s]` summed over each rolling 7-day window.

<details><summary>Hint</summary>
Build the window sum with `sum(SHIFT_HOURS[s] * x[d,s] for d in window for s in Shift)`. Acceptance: setting cap to 32h should make some previously-feasible schedules infeasible.
</details>

**Exercise 10-E: Compare encodings head-to-head.** Implement the same instance with (a) Boolean grid + forbidden pairs, (b) Boolean grid + `AddAutomaton` (replacing forbidden pairs + max-consec-working), (c) interval + `NoOverlap`. Time each on horizon = 28 days, log `num_branches` and `wall_time`.

<details><summary>Hint</summary>
Use `solver.parameters.log_search_progress = True` and `solver.num_branches`, `solver.wall_time`. Record results in a small markdown table in your chapter note. Expected: grid+forbidden ~ interval < automaton on branches; wall times all under a second for 1 nurse.
</details>

## 9. Self-check

<details><summary>Q1: When is the Boolean grid better than intervals? When worse?</summary>
Grid is better for fixed-timing shift scheduling (coverage = sum, rules = linear arithmetic). Intervals are better when start times are decision variables (task scheduling, home-care), or when the "rest gap" between shifts is the decision variable — `NoOverlap` handles the gap for free.
</details>

<details><summary>Q2: Sketch the automaton for "no morning after night."</summary>
Two states: 0 = "yesterday was not Night," 1 = "yesterday was Night." Transitions from state 0: Off/M/E/N all allowed, leading to appropriate state. Transitions from state 1: Off → 0, E → 0, N → 1. No triple for `(1, M, *)` → M is rejected after N. Final states = {0, 1}.
</details>

<details><summary>Q3: How do you encode "exactly 2 weekends off in 4 weeks"?</summary>
Create `weekendOff[w]` as the AND of Saturday-Off and Sunday-Off for weekend `w`. Constrain `sum_w weekendOff[w] == 2`. The AND is encoded as `weekendOff[w] ⇔ (x[sat, Off] ∧ x[sun, Off])` via two implications or `AddBoolAnd(...).OnlyEnforceIf(weekendOff[w])` plus the converse.
</details>

<details><summary>Q4: Why does forbidden-pair encoding reduce to the rest-hours rule?</summary>
Rest hours are *parameters* of the shift templates. Once you fix Morning = 07:00–15:00 and Night = 23:00–09:00 (next day), "11h rest" is a property of the pair — it's either legal or not. The forbidden-pair table is the precomputation of which 2-shift sequences violate the rest rule.
</details>

<details><summary>Q5: Why does CP-SAT's presolve often eliminate the Boolean grid's `exactly_one` constraints?</summary>
Because they turn into single-variable domains after presolve: if `x[d, MORNING] + x[d, EVENING] + x[d, NIGHT] + x[d, OFF] == 1` and some are fixed, the rest are implied. Presolve reifies the grid into a single "which shift" integer variable and runs cheaper propagators. Check with `fill_tightened_domains_in_response = True`.
</details>

## 10. What this unlocks

With shifts and transitions in hand, you have the full vocabulary to assemble **NSP v1** — many nurses, coverage demand, skill mix — in Chapter 11.

## 11. Further reading

- [Google OR-Tools — Employee scheduling](https://developers.google.com/optimization/scheduling/employee_scheduling) — the canonical nurse-ish example in CP-SAT.
- [OR-Tools `shift_scheduling_sat.py`](https://github.com/google/or-tools/blob/stable/examples/python/shift_scheduling_sat.py) — production-scale reference. Study the `add_soft_sequence_constraint` helper.
- Burke et al., "The state of the art of nurse rostering," *J. Scheduling* 7, 2004. [DOI 10.1023/B:JOSH.0000046076.75950.0b](https://doi.org/10.1023/B:JOSH.0000046076.75950.0b)
- Pesant, "A regular language membership constraint for finite sequences of variables," CP 2004 — the paper behind `AddAutomaton`/`regular`. [DOI 10.1007/978-3-540-30201-8_36](https://doi.org/10.1007/978-3-540-30201-8_36)
- [`docs/knowledge/nurse-scheduling/overview.md`](../knowledge/nurse-scheduling/overview.md) §4 (H3–H6) — hard-constraint taxonomy.
- [`docs/knowledge/cp-sat/overview.md`](../knowledge/cp-sat/overview.md) §8 — scheduling primitives deep dive.
