# Chapter 12 — NSP v2: Soft Constraints, Preferences, Fairness

> **Phase 5: Nurse Scheduling I/II/III** · Estimated: ~4h · Status: ready-to-start · Last updated: 2026-04-19

## Goal

Add an **objective** to the feasibility model from Chapter 11 — nurse preferences, preferred-partner coupling, workload balance, and fairness across the team. Learn three objective-design paradigms (weighted sum, lexicographic, max-min fairness) and how to tune weights without the schedule descending into chaos.

## Before you start

- **Prerequisites:** Chapter 11 (NSP v1 hard constraints), Chapter 5 (objectives, bounds, callbacks).
- **Required reading:**
  - [`docs/knowledge/nurse-scheduling/overview.md`](../knowledge/nurse-scheduling/overview.md) §5 (soft-constraint taxonomy S1–S10), §6 (objective shapes).
  - [`docs/knowledge/cp-sat/overview.md`](../knowledge/cp-sat/overview.md) §5 (Objective, lexicographic pattern).
- **Environment:** Extend `apps/py-cp-sat/ch12-nsp-v2/` from v1. `cpsat-kt` must support `lexicographic { }` or the equivalent two-step solve-fix-resolve idiom.

## Concepts introduced this chapter

- **Soft constraint** — a rule that can be violated, at a cost added to the objective.
- **Weighted-sum objective** — single scalar objective `Σ wᵢ · violationᵢ`.
- **Lexicographic objective** — hierarchical: solve objective 1 to optimality, fix it, optimize objective 2, fix, etc.
- **Max-min fairness** — minimize `max(x) − min(x)` to equalize workload.
- **Sum-of-deviations fairness** — minimize `Σ |xᵢ − target|`; smoother gradient, different behavior.
- **Pareto front** — the set of non-dominated solutions under multiple objectives. Useful to *visualize* tradeoffs even if you ship one weighted scalar.
- **Scalarization** — turning a multi-objective problem into a single-objective one. Weighted sum is one scheme.
- **Preference score** — a per-`(nurse, day, shift)` integer; positive = nurse likes it (reward), negative = dislikes (penalty).

## 1. Intuition

v1 gave you a *legal* schedule — the hospital's lawyer won't object. But the nurses might: Alice gets three weekends while Bob gets zero; Carol hates overnights but keeps getting them. A schedule that's legal but miserable is almost as bad as no schedule.

The fix is to give the solver a *dial*: "optimize something humans care about." That something is usually a bag of soft rules, each with a weight. The solver finds the schedule minimizing total weighted pain.

Two pitfalls to dodge:

1. **Weight tyranny.** If weights are wrong, the objective drifts — maybe weekend fairness crushes everyone onto bad shifts. You'll iterate.
2. **Averaging hides outliers.** Minimizing *average* preference violation lets you concentrate the pain on one nurse. Max-min fairness forces the worst-off nurse's pain to be the floor everyone lives with.

Lexicographic is the escape hatch when the *categories* have clear priority: coverage always beats fairness, fairness always beats personal preferences. You optimize them in order.

## 2. Formal definition

### 2.1 Soft constraints in NSP v2

We keep every hard constraint from Chapter 11 and add:

| # | Soft rule | Formal shape | Encoding |
|---|---|---|---|
| S1 | Preference score | per-`(n,d,s)` integer `p[n,d,s]` | Linear in objective: `Σ p[n,d,s] · x[n,d,s]` |
| S2 | Weekend fairness | `max_n w[n] − min_n w[n]` should be small | `AddMaxEquality`, `AddMinEquality`, objective term |
| S3 | Workload balance | `max_n hours[n] − min_n hours[n]` | Same as S2 |
| S4 | Preferred partner | nurse `p` and nurse `q` prefer same shift | Boolean `together[d,s] = x[p,d,s] ∧ x[q,d,s]`, reward per `together` |
| S5 | Hard-no overnight for nurse `X` | `x[X, d, NIGHT] = 0 ∀d` | This is a *hard* rule disguised as preference — encode as hard if truly hard |
| S6 | Weekend-off requests | nurse `n` wants weekend `w` off | Soft: penalty when `x[n, d∈weekend_w, *] = 1` |

### 2.2 Three objective flavors

**Weighted sum.** One objective:

```
min   w_pref · Σ preference_penalties
    + w_fair  · weekend_spread
    + w_load  · workload_spread
    + w_partner · (−Σ together[p,q])
```

Reward-style terms get negative weight; penalty-style terms get positive weight.

**Lexicographic.** Solve in priority order.

```
1. minimize coverage_violations          → obj1*
2. add constraint obj1 == obj1*;  minimize preference_penalties   → obj2*
3. add obj2 == obj2*;             minimize weekend_spread         → obj3*
```

Coverage is usually hard (not soft), but the pattern generalizes to any ordered pair of soft objectives.

**Max-min fairness.**

```
maximize  min_n satisfaction[n]
```

A classic CP pattern: add an auxiliary `t` with `t <= satisfaction[n] ∀n`, then maximize `t`.

### 2.3 Cross-language mapping

| Concept | Python | Kotlin (`cpsat-kt`) |
|---|---|---|
| Weighted sum | `model.minimize(sum(w_i * term_i ...))` | `minimize { sum(...) }` |
| Two-step lex | `model.add(obj1 == best1); model.minimize(obj2)` | `lexicographic { primary { … }; secondary { … } }` |
| `max/min` equality | `model.add_max_equality(t, xs)` | `constraint { t eq max(xs) }` / dedicated helper |
| AND reification | `model.add_bool_and(...).only_enforce_if(b)` | `enforceIf(b) { … }` |

### 2.4 Numeric scaling

CP-SAT is integer-only. Multiply preference scores by 100 (or 1000) to give weights room, then divide at report time. Don't mix integer and fractional terms via different magnitudes without explicit scaling.

## 3. Worked example by hand

**3-nurse, 3-day, 2-shift (D, N) problem.** Every slot needs 1 nurse. All nurses eligible for all shifts.

Preferences (positive = preferred, negative = dispreferred):

| | d0-D | d0-N | d1-D | d1-N | d2-D | d2-N |
|---|---|---|---|---|---|---|
| A | +3 | -2 | +3 | -2 | +3 | -2 |
| B | 0  | +1 | 0  | +1 | 0  | +1 |
| C | -2 | 0  | -2 | 0  | -2 | 0  |

Workload target: 2 shifts per nurse.

**Weighted-sum (with `w_pref=1`, `w_fair=0`, ignoring other terms):**

Higher score = more preferred. We *maximize* total preference. Best assignment:

- A: three Days → preference = +9
- B: ??? — but we only have 3 D-slots and 3 N-slots, 6 slots, 3 nurses × 2 each.

Plug A on all Days → Days filled (3 slots). Then B and C split Nights (3 slots). But B's total must be 2, C's must be 2. A already has 3. Infeasible if max=2 per nurse is hard.

Adjust: allow A up to 3 shifts.
- A: d0-D, d1-D, d2-D → pref = +9 (but workload 3)
- B: d0-N, d1-N → pref = +2, workload 2
- C: d2-N → pref = 0, workload 1

Workload spread = 3 − 1 = 2. Unbalanced. If we add `w_load=5`:

- Total objective: `+9 + 2 + 0 − 5 × 2 = +1`.
- Alternative "fair" schedule: A gets 2 shifts, B gets 2, C gets 2. Workload spread = 0. Total pref might be lower (A loses a Day) — maybe `+6 + 2 + 0 = +8`. Objective = +8. **Better.**

So with `w_load=5`, the solver picks the fair schedule. With `w_load=0` it picks the unfair one. This is the knob-tuning experience you'll run in Exercise 12-E.

## 4. Python implementation

```python
# apps/py-cp-sat/ch12-nsp-v2/src/model_v2.py
"""Extend NSP v1 with preferences, fairness, and workload balance."""
from ortools.sat.python import cp_model
from dataclasses import dataclass

from ch11_nsp_v1.src.model import build_model as build_hard_only   # reuse


@dataclass
class Weights:
    pref: int = 10
    weekend_spread: int = 100
    workload_spread: int = 50
    partner_bonus: int = 5


def add_soft_objective(
    model: cp_model.CpModel,
    x,
    instance,
    preferences: dict,          # (nurse_id, day, shift) -> int (higher = more preferred)
    partners: list,             # list of (n1_id, n2_id) pairs
    weights: Weights,
):
    N = instance.nurses
    D = range(instance.days)
    S = instance.shifts
    horizon = instance.days

    # --- Preference term ---
    # Negate scores so that maximizing preference = minimizing `−score · x`.
    pref_terms = []
    for (n_id, d, s), p in preferences.items():
        key = (n_id, d, s)
        if key in x:
            pref_terms.append(-p * x[key])     # lower is better

    # --- Weekend spread ---
    weekend_days = [d for d in D if (instance.start_date.toordinal() + d) % 7 in (5, 6)]
    w_count = {}
    for n in N:
        wc = model.new_int_var(0, len(weekend_days), f"wkend_{n.id}")
        model.add(wc == sum(x[(n.id, d, s)]
                            for d in weekend_days for s in S if (n.id, d, s) in x))
        w_count[n.id] = wc

    max_w = model.new_int_var(0, len(weekend_days), "max_wkend")
    min_w = model.new_int_var(0, len(weekend_days), "min_wkend")
    model.add_max_equality(max_w, list(w_count.values()))
    model.add_min_equality(min_w, list(w_count.values()))
    weekend_spread = max_w - min_w

    # --- Workload (shift count) spread ---
    load_count = {}
    for n in N:
        lc = model.new_int_var(0, horizon, f"load_{n.id}")
        model.add(lc == sum(x[(n.id, d, s)]
                            for d in D for s in S if (n.id, d, s) in x))
        load_count[n.id] = lc

    max_l = model.new_int_var(0, horizon, "max_load")
    min_l = model.new_int_var(0, horizon, "min_load")
    model.add_max_equality(max_l, list(load_count.values()))
    model.add_min_equality(min_l, list(load_count.values()))
    workload_spread = max_l - min_l

    # --- Partner bonus ---
    partner_terms = []
    for (n1, n2) in partners:
        for d in D:
            for s in S:
                k1, k2 = (n1, d, s), (n2, d, s)
                if k1 in x and k2 in x:
                    together = model.new_bool_var(f"together_{n1}_{n2}_{d}_{s}")
                    model.add(together <= x[k1])
                    model.add(together <= x[k2])
                    model.add(together >= x[k1] + x[k2] - 1)
                    partner_terms.append(-together)    # reward

    # --- Assemble ---
    objective = (
        weights.pref            * sum(pref_terms) +
        weights.weekend_spread  * weekend_spread +
        weights.workload_spread * workload_spread +
        weights.partner_bonus   * sum(partner_terms)
    )
    model.minimize(objective)

    return {
        "weekend_spread": weekend_spread,
        "workload_spread": workload_spread,
        "pref_terms": pref_terms,
        "partner_terms": partner_terms,
    }
```

```python
# apps/py-cp-sat/ch12-nsp-v2/src/lexicographic.py
"""Two-step lex: first minimize pref violations, then minimize fairness spread."""
from ortools.sat.python import cp_model

def solve_lex(model, x, instance, preferences, weights):
    # Step 1: build & solve pref-only objective
    pref_expr = sum(-p * x[(n_id, d, s)]
                    for (n_id, d, s), p in preferences.items() if (n_id, d, s) in x)
    model.minimize(pref_expr)

    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = 30.0
    status1 = solver.solve(model)
    if status1 not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        return status1

    best_pref = int(solver.objective_value)
    print(f"Lex step 1: preference obj = {best_pref}")

    # Step 2: lock pref at best, minimize workload spread
    model.add(pref_expr == best_pref)

    load = {}   # assume built elsewhere and passed in — skipped for brevity
    load_spread = model.new_int_var(0, instance.days, "load_spread")
    # ... compute spread as before ...
    model.minimize(load_spread)

    status2 = solver.solve(model)
    return status2
```

```python
# apps/py-cp-sat/ch12-nsp-v2/src/max_min_fairness.py
"""Max-min fairness: maximize the worst-off nurse's satisfaction."""
from ortools.sat.python import cp_model

def add_max_min_fairness(model, x, instance, preferences):
    sat = {}
    for n in instance.nurses:
        s_var = model.new_int_var(-10_000, 10_000, f"sat_{n.id}")
        model.add(s_var == sum(
            preferences.get((n.id, d, sh), 0) * x[(n.id, d, sh)]
            for d in range(instance.days)
            for sh in instance.shifts
            if (n.id, d, sh) in x
        ))
        sat[n.id] = s_var

    t = model.new_int_var(-10_000, 10_000, "floor")
    for n in instance.nurses:
        model.add(t <= sat[n.id])
    model.maximize(t)
    return t, sat
```

```python
# apps/py-cp-sat/ch12-nsp-v2/src/pareto.py
"""Sketch a Pareto frontier over (workload_spread, pref_penalty)."""
import itertools, csv

def sweep(build_model_with_weights, weight_grid, outfile="pareto.csv"):
    rows = []
    for w_pref, w_fair in itertools.product(*weight_grid):
        model, aux = build_model_with_weights(w_pref=w_pref, w_fair=w_fair)
        solver = cp_model.CpSolver()
        solver.parameters.max_time_in_seconds = 15.0
        status = solver.solve(model)
        if status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
            continue
        rows.append({
            "w_pref": w_pref, "w_fair": w_fair,
            "obj": solver.objective_value,
            "pref_penalty": solver.value(aux["pref_sum"]),
            "workload_spread": solver.value(aux["workload_spread"]),
        })
    with open(outfile, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=rows[0].keys())
        writer.writeheader(); writer.writerows(rows)
```

Plot the CSV with any tool — matplotlib, Recharts in the Chapter 16 UI, or a spreadsheet.

## 5. Kotlin implementation (via `cpsat-kt`)

```kotlin
// apps/kt-cp-sat/ch12-nsp-v2/src/main/kotlin/Soft.kt
package ch12

import io.vanja.cpsat.*
import ch11.*

data class Weights(
    val pref: Long = 10,
    val weekendSpread: Long = 100,
    val workloadSpread: Long = 50,
    val partnerBonus: Long = 5,
)

data class SoftAux(
    val weekendSpread: IntVar,
    val workloadSpread: IntVar,
    val prefTerms: List<LinearExpr>,
    val partnerTerms: List<LinearExpr>,
)

fun CpModel.addSoftObjective(
    vars: ModelVars,
    preferences: Map<Triple<String, Int, String>, Int>,
    partners: List<Pair<String, String>>,
    weights: Weights,
): SoftAux {
    val inst = vars.instance
    val N = inst.nurses
    val D = 0 until inst.horizon.days
    val S = inst.shifts.map { it.id }

    // Preference terms (minimize: so negate)
    val prefTerms = preferences.mapNotNull { (key, p) ->
        vars.x[key]?.let { -p.toLong() * it }
    }

    // Weekend spread via max/min
    val weekendDays = D.filter {
        (java.time.LocalDate.parse(inst.horizon.startDate).plusDays(it.toLong())
            .dayOfWeek.value) in listOf(6, 7) // Sat, Sun
    }
    val wCount = N.associate { n ->
        val v = intVar("wk_${n.id}", 0L..weekendDays.size.toLong())
        val sumExpr = sum(weekendDays.flatMap { d ->
            S.mapNotNull { vars.x[Triple(n.id, d, it)] }
        })
        constraint { v eq sumExpr }
        n.id to v
    }
    val maxW = intVar("maxW", 0L..weekendDays.size.toLong())
    val minW = intVar("minW", 0L..weekendDays.size.toLong())
    maxEquality(maxW, wCount.values.toList())
    minEquality(minW, wCount.values.toList())

    val weekendSpread = intVar("weekendSpread", 0L..weekendDays.size.toLong())
    constraint { weekendSpread eq (maxW - minW) }

    // Workload spread
    val hz = inst.horizon.days.toLong()
    val load = N.associate { n ->
        val v = intVar("load_${n.id}", 0L..hz)
        val sumExpr = sum(D.flatMap { d -> S.mapNotNull { vars.x[Triple(n.id, d, it)] } })
        constraint { v eq sumExpr }
        n.id to v
    }
    val maxL = intVar("maxL", 0L..hz); maxEquality(maxL, load.values.toList())
    val minL = intVar("minL", 0L..hz); minEquality(minL, load.values.toList())
    val workloadSpread = intVar("workloadSpread", 0L..hz)
    constraint { workloadSpread eq (maxL - minL) }

    // Partner reward
    val partnerTerms = mutableListOf<LinearExpr>()
    for ((n1, n2) in partners) for (d in D) for (s in S) {
        val a = vars.x[Triple(n1, d, s)]
        val b = vars.x[Triple(n2, d, s)]
        if (a != null && b != null) {
            val t = boolVar("together_${n1}_${n2}_${d}_$s")
            constraint { t le a }
            constraint { t le b }
            constraint { t ge (a + b - 1) }
            partnerTerms += -t
        }
    }

    // Objective
    minimize {
        weights.pref * sum(prefTerms) +
        weights.weekendSpread * weekendSpread +
        weights.workloadSpread * workloadSpread +
        weights.partnerBonus * sum(partnerTerms)
    }

    return SoftAux(weekendSpread, workloadSpread, prefTerms, partnerTerms)
}
```

Lexicographic via the DSL:

```kotlin
cpModel {
    // … hard constraints …
    lexicographic {
        step("pref") { minimize { sum(prefTerms) } }
        step("fairness") { minimize { weekendSpread + workloadSpread } }
    }
}.solveBlocking()
```

Under the hood `lexicographic { }` wraps the CP-SAT "solve → pin objective → re-solve" pattern.

## 6. MiniZinc implementation (skip)

Skip. You've already contrasted declarative and imperative; v2 is about paradigms of *objective design*, which MiniZinc exposes via `solve minimize`/`solve satisfy` but doesn't change the story.

## 7. Comparison & takeaways

| Axis | Weighted sum | Lexicographic | Max-min fairness |
|---|---|---|---|
| Implementation | 1 solve | 2–N sequential solves | 1 solve with auxiliary `t` |
| Produces "balanced" results | If weights are right | Yes, by construction | Yes, by construction |
| Sensitivity to weights | High | Zero (no weights) | N/A |
| Explains to HR | Hard ("why weight 17?") | Easy (priority order) | Easy ("worst-off wins") |
| Solver difficulty | Baseline | Harder (chained solves) | Baseline–medium |
| CP-SAT support | Native | Manual pattern | Native |

**Key insight:** For production NSP, **use lex for the *category* hierarchy** (legal > clinical > personal > cosmetic) and **weighted sum within categories**. Pure weighted-sum across categories is a weight-tuning nightmare; pure lex across *every* soft rule is over-constrained.

## 8. Exercises

**Exercise 12-A: Weighted-sum vs lexicographic.** Solve the same toy instance three times: (a) weighted sum with `w_pref=1, w_fair=100`, (b) weighted sum with `w_pref=100, w_fair=1`, (c) lex: first pref, then fair. Compare the rendered schedules. Which looks most like what you'd sign off on?

<details><summary>Hint</summary>
Capture (objective, pref_penalty, workload_spread) for each. Lex will always Pareto-dominate *in lex order*. Weighted sum can find tradeoffs lex can't.
</details>

**Exercise 12-B: Implement max-min fairness.** Maximize the floor satisfaction. Compare the floor achieved to the *average* satisfaction in the weighted-sum solution. Observe how max-min pushes the worst-off up at the cost of average.

<details><summary>Hint</summary>
Add `t` with `t <= satisfaction[n] ∀n`, maximize `t`. Report both `t*` and `sum(satisfaction)`.
</details>

**Exercise 12-C: Gut-feel weight tuning.** Draft a schedule by hand for the toy instance that looks "fair" to you. Compute its weekend spread, workload spread, and preference penalty. Now tune weights in the weighted-sum model until the solver returns something within 10% of your gut schedule. Commit the weights + your rationale.

<details><summary>Hint</summary>
Start with all weights = 100. Halve/double the offending term iteratively. Warning: if you can't match your gut with *any* weights, the gut schedule is Pareto-dominated by the solver's.
</details>

**Exercise 12-D: Compare max-min to sum-of-deviations.** Implement `min Σ |hours[n] − target|` where `target = mean(hours)`. Compare to `min max(hours) − min(hours)`. Is the resulting schedule qualitatively different?

<details><summary>Hint</summary>
Sum-of-deviations is smoother → may not push the tails as hard. Max-min chops off the extremes more aggressively. A sum-of-deviations model accepts one outlier if total is lower.
</details>

**Exercise 12-E: Pareto sweep.** Run the 25-point grid `w_pref ∈ {1,10,50,100,500}, w_fair ∈ {1,10,50,100,500}`. Plot (pref_penalty, workload_spread). Note which points are Pareto-dominated.

<details><summary>Hint</summary>
Use `apps/py-cp-sat/ch12-nsp-v2/src/pareto.py`. Export to CSV; plot with matplotlib or paste into the chapter note as a table. Pareto-dominated = some other point is ≤ in both axes.
</details>

## 9. Self-check

<details><summary>Q1: Why is "sum of preference violations" often a terrible objective?</summary>
Because the solver can concentrate all violations on one nurse (sum is unchanged if you move from spreading to bunching). Average-style objectives tolerate outliers. Use max-min or weighted combinations when concentration matters.
</details>

<details><summary>Q2: How does lexicographic optimization work in CP-SAT?</summary>
Solve the primary objective to optimality, record `obj1*`, add the constraint `primary == obj1*`, then minimize the next objective. Repeat. CP-SAT has no built-in lex; it's a user-facing pattern.
</details>

<details><summary>Q3: How sensitive is the solution to weight tuning?</summary>
Often very. A 2× change in one weight can flip which rule "wins." Best practice: lex across categories (legal → clinical → personal), weighted sum within categories, and always run a small Pareto sweep before shipping weights.
</details>

<details><summary>Q4: Why multiply preference scores by 100?</summary>
Because CP-SAT is integer-only. Scaling lets you represent "half a point" as 50 and "one tenth of a point" as 10 without losing precision. Divide back at report time.
</details>

<details><summary>Q5: What's the lower-bound behavior of the objective during search?</summary>
CP-SAT's LP-relaxation produces a *best bound* that is non-decreasing (for minimize) / non-increasing (for maximize). When best bound == incumbent, you have `OPTIMAL`. The gap is `(incumbent − bound) / |incumbent|`. This is why the log is essential — you see both values.
</details>

## 10. What this unlocks

With objectives under control, you can take on **realistic-scale instances** — INRC-II, 30–120 nurses, 4-week horizons — in **Chapter 13**, where solver tuning becomes the main event.

## 11. Further reading

- Ceschia et al., "The second international nurse rostering competition," *Ann. OR* 292, 2019 — defines the canonical soft-constraint set. [DOI 10.1007/s10479-018-2816-0](https://doi.org/10.1007/s10479-018-2816-0)
- Pessoa, Sadykov et al. on lexicographic optimization in MIP/CP, especially survey form in Deb, *Multi-objective Optimization Using Evolutionary Algorithms*, 2001, ch. 2. [book page](https://www.wiley.com/en-us/Multi-Objective+Optimization+using+Evolutionary+Algorithms-p-9780471873396)
- Rawls's *max-min* fairness formalized in OR: Bertsimas, Farias, Trichakis, "On the efficiency-fairness trade-off," *Management Science* 58, 2012.
- [OR-Tools `shift_scheduling_sat.py`](https://github.com/google/or-tools/blob/stable/examples/python/shift_scheduling_sat.py) — production-scale example mixing hard and soft.
- Krupke's CP-SAT Primer §6: modeling objectives. [d-krupke/cpsat-primer](https://d-krupke.github.io/cpsat-primer/)
- [`docs/knowledge/nurse-scheduling/overview.md`](../knowledge/nurse-scheduling/overview.md) §5–6 — soft-constraint taxonomy and objective shapes.
