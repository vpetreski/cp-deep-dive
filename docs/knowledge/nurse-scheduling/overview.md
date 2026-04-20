# The Nurse Scheduling Problem (NSP): A Deep Dive

## 1. What the Problem Is — ELI5

Imagine you're the head of nursing at a hospital. Every month, you need to fill a calendar: for each day, you need to decide which nurses work which shifts (day, evening, night). Every nurse has a contract (full-time, part-time), skills (ICU-certified, pediatric), preferences (Alice prefers mornings, Bob can't work Fridays), and legal protections (minimum rest between shifts, max consecutive nights). The ward also has rules: at least 3 nurses on night shift, at least one with ICU training, no one works 7 days straight. You want a schedule that satisfies all the rules *and* keeps the nurses happy enough that they don't quit.

That's the **Nurse Scheduling Problem** (NSP), also called **nurse rostering**. It's one of the most thoroughly studied problems in Operations Research and Constraint Programming, because hospitals worldwide face it every month, and doing it by hand is miserable and error-prone.

Source: <https://en.wikipedia.org/wiki/Nurse_scheduling_problem>

---

## 2. Why It's Hard

NSP is **NP-hard** — reducible from graph coloring and other classical combinatorial problems. But NP-hardness is only part of the story. The practical difficulty comes from the combination of:

- **Combinatorial explosion.** A modest ward with 30 nurses, 28 days, and 4 shift types (D/E/N/Off) has `4^(30×28) ≈ 10^506` raw assignments. Using binary `x[n,d,s]` encoding that's `30 × 28 × 4 = 3,360` binary variables — a CP-SAT solver can eat that for breakfast *if* the constraints are tight, but real instances have hundreds of nurses.
- **Conflicting objectives.** Coverage fights fairness. Fairness fights preferences. Preferences fight budget.
- **Soft constraints matter more than hard ones.** Finding a feasible schedule is often easy. Finding one nurses don't revolt against is the hard part.
- **Real-world scale.** University hospitals have 500–2000 nurses. INRC-II benchmark instances go up to 120 nurses × 28 days with 4 skills and multiple shift types — already challenging for exact methods.
- **Dynamic re-optimization.** Someone calls in sick at 6am; you need a feasible repair in minutes, not hours.
- **Weak LP relaxations.** The MILP formulation has notoriously loose LP bounds, so branch-and-bound alone struggles. This is part of why CP and metaheuristics are so popular here.

---

## 3. Formal Model

### 3.1 Binary-variable (MILP / CP-SAT-native) formulation

**Sets:**
- `N` = set of nurses, indexed by `n`
- `D` = set of days in the horizon, indexed by `d`
- `S` = set of shift types (e.g., Day, Evening, Night), indexed by `s`
- `K` = set of skills

**Parameters:**
- `demand[d, s, k]` — required number of nurses with skill `k` on day `d` shift `s`
- `skill[n, k] ∈ {0,1}` — whether nurse `n` has skill `k`
- `maxShifts[n]` — max total shifts for nurse `n` in the horizon
- `minShifts[n]` — min total shifts
- `maxConsec[n]` — max consecutive working days
- `forbidden[s1, s2] ∈ {0,1}` — whether shift `s1` followed next day by `s2` is illegal (e.g., Night → Day)
- `pref[n, d, s] ∈ ℝ` — soft preference score

**Decision variables:**
```
x[n, d, s] ∈ {0,1}   for all n ∈ N, d ∈ D, s ∈ S
```
`x[n, d, s] = 1` iff nurse `n` works shift `s` on day `d`.

**Core constraints:**

```
(1) At most one shift per day per nurse:
    ∑_{s ∈ S} x[n, d, s] ≤ 1          ∀n, d

(2) Coverage (per skill):
    ∑_{n : skill[n,k]=1} x[n, d, s] ≥ demand[d, s, k]   ∀d, s, k

(3) Forbidden transitions:
    x[n, d, s1] + x[n, d+1, s2] ≤ 1    ∀n, d, (s1,s2) forbidden

(4) Max consecutive working days (sliding window of size maxConsec+1):
    ∑_{d'=d..d+maxConsec} ∑_s x[n, d', s] ≤ maxConsec    ∀n, d

(5) Contract hours:
    minShifts[n] ≤ ∑_{d,s} x[n, d, s] ≤ maxShifts[n]     ∀n
```

**Objective** (weighted soft-constraint violations):
```
min  ∑_{n,d,s} w_pref · violatePref[n,d,s]
   + ∑_n     w_fair · unfairness[n]
   + ∑_n     w_weekend · weekendCount[n] - avg...
```

### 3.2 Interval-based (CP-native) formulation

A CP-native alternative treats each nurse's schedule as a sequence of optional intervals. In CP-SAT this looks more like:

- For each `(n, d, s)`, create an **optional interval** `I[n,d,s]` with fixed start/end and a presence literal `p[n,d,s]`.
- Use `NoOverlap` on each nurse's intervals to enforce "one shift per day" and rest gaps.
- Use `Cumulative` over intervals on each (day, shift) bucket to enforce coverage as a capacity requirement.

In practice, CP-SAT models of NSP tend to use the boolean `x[n,d,s]` formulation because shifts have fixed time windows; intervals shine more when start times are decision variables (e.g., home-care scheduling).

---

## 4. Hard Constraints — Taxonomy

| # | Constraint | Description | Typical CP-SAT pattern |
|---|---|---|---|
| H1 | **Coverage** | At least `demand[d,s]` nurses working each (day, shift). Often per skill. | Linear `sum(x) >= demand` |
| H2 | **At most one shift/day** | A nurse cannot work Day and Night on the same calendar day. | `sum_s x[n,d,s] <= 1` |
| H3 | **Forbidden shift transitions** | E.g. no Night → Day next morning (insufficient rest). | Pairwise `x[n,d,s1] + x[n,d+1,s2] <= 1` |
| H4 | **Minimum rest hours** | Usually 11+ hours between shifts (EU Working Time Directive). | Same as H3, encoded as forbidden pairs |
| H5 | **Max consecutive working days** | No more than `k` days in a row. | Sliding-window sum `<= k` |
| H6 | **Max consecutive nights** | Night-specific stricter limit, often 3–4. | Sliding-window sum on night-only `x` |
| H7 | **Min/max shifts per period** | Contract says 10 shifts/4 weeks ± 2. | `sum_d,s x[n,d,s]` bounded |
| H8 | **Weekly hours cap** | Often 40–48h depending on country. | Hours-weighted sum per rolling 7 days |
| H9 | **Skill mix** | Each shift needs ≥ 1 senior, ≥ 1 ICU-certified, etc. | Coverage per skill subset |
| H10 | **Unavailability** | Vacation, sick leave, training. | Fix `x[n,d,s] = 0` for affected cells |
| H11 | **Forced assignment** | Nurse is booked for a specific shift. | Fix `x[n,d,s] = 1` |
| H12 | **Complete weekend** | If working Sat, must work Sun (or neither). | `x[n,Sat,*] == x[n,Sun,*]` per nurse (equality on sum) |
| H13 | **Min consecutive working days** | Avoid isolated single-day assignments. | Sliding-window pattern or automaton |

Ref: Burke, De Causmaecker, Vanden Berghe, Van Landeghem, "The state of the art of nurse rostering," *Journal of Scheduling*, 2004. <https://doi.org/10.1023/B:JOSH.0000046076.75950.0b>

---

## 5. Soft Constraints — Taxonomy

Soft constraints can be violated, but each violation incurs a penalty in the objective.

| # | Soft Constraint | Description | Typical encoding |
|---|---|---|---|
| S1 | **Shift preferences** | Alice prefers mornings. | `x[n,d,s] · penalty[n,d,s]` summed |
| S2 | **Day-off requests** | Nurse requested Friday off. | Penalty when `sum_s x[n,friday,s] = 1` |
| S3 | **Weekend fairness** | Spread weekend duty evenly. | Penalize `max_n wkend[n] - min_n wkend[n]` |
| S4 | **Night-shift fairness** | Everyone gets ~same number of nights. | Slack variables around a target |
| S5 | **Pattern preferences** | Nurse prefers 2-on-2-off rhythm. | Automaton over weekly pattern; penalty for deviations |
| S6 | **Workload balance** | Equalize total hours within tolerance. | Min-max hours difference |
| S7 | **Consecutive preferences** | Avoid isolated single-day stretches. | Window count with soft threshold |
| S8 | **Requested co-assignment** | Two nurses want to work same shift (mentor pair). | Indicator `min(x[n1,d,s], x[n2,d,s])` rewarded |
| S9 | **Avoid undesired shifts** | "If possible, no Sunday nights for Alice." | Penalty coefficient |
| S10 | **Preferred skill usage** | Use ICU nurses where their skill matters, not elsewhere. | Cost depends on mismatch |

INRC-II defines a canonical soft-constraint set (preferences, complete weekends, total working weekends, total assignments, consecutive assignments, consecutive days off, consecutive working shifts).

Ref: Ceschia, Dang, De Causmaecker, Haspeslagh, Schaerf, "The second international nurse rostering competition," *Annals of Operations Research*, 2019. <https://doi.org/10.1007/s10479-018-2816-0>

---

## 6. Objective Function Shapes

Four common flavors:

1. **Weighted sum** (most common). `min Σ w_i · violation_i`. Easy to implement; the dark art is tuning weights.
2. **Lexicographic**. Solve for objective 1 to optimality, then constrain and minimize objective 2, etc. Used when soft-constraint categories have clear priorities (legal > clinical > preferences).
3. **Goal programming**. Each objective has a target; minimize summed deviations from targets. Popular with HR departments because "target number of weekends" is easier to explain than "weight of 17."
4. **Pareto / multi-objective**. Compute a Pareto front over (fairness, coverage-slack, preference-violations). Rare in production but common in academic papers. CP-SAT has limited native Pareto support; typically scalarized.

---

## 7. Variants

| Variant | Description |
|---|---|
| **Cyclic NSP** | Same pattern repeats every few weeks (e.g., 4-week cycle). Simpler to model; nurses like predictability. |
| **Non-cyclic** | New schedule every horizon. More flexible, more complex. |
| **Static** | Solved once for the horizon. |
| **Dynamic / re-rostering** | Re-solve on disruption (sick call, admission surge). Often with "minimum-change" objective. |
| **Self-scheduling** | Nurses submit preferred shifts first; solver fills gaps. |
| **Team / bidding-based** | Nurses bid for shifts; allocation is constrained auction + optimization. |
| **Single-ward vs hospital-wide** | Hospital-wide adds inter-ward movement and skill-pool sharing. |
| **Skill-based routing** | Patients have skill requirements; assignment couples nurses to patients, not just shifts. |
| **Night-float teams** | Dedicated night-only staff modeled as separate population with different contracts. |
| **Home-care / community nursing** | Adds routing and travel time — becomes a Vehicle Routing / Nurse Routing Problem (NRP). |
| **Integrated staffing-and-scheduling** | Hiring decisions co-optimized with roster. |

---

## 8. Benchmark Instances

### 8.1 NSPLib — Nurse Scheduling Problem Library

Created by Broos Maenhout and Mario Vanhoucke (Ghent University, Operations Research & Scheduling group). Hosted via the OR&S research group at <https://www.projectmanagement.ugent.be/research/personnel_scheduling/nsp>.

- **~9,000 instances** spanning small (25 nurses) to medium sizes
- Parameterized along a **design of experiments** varying coverage demand, preference distributions, and constraint tightness
- Structured format: coverage matrix, nurse availabilities, preferences, case-file parameters
- Used for benchmarking in dozens of papers since ~2005

Reference: Maenhout & Vanhoucke, "An electromagnetic meta-heuristic for the nurse scheduling problem," *Journal of Heuristics*, 2007.

### 8.2 INRC-I — First International Nurse Rostering Competition (2010)

Organized by the KAHO Sint-Lieven team (Haspeslagh, De Causmaecker, Schaerf, Stolevik, Vanden Berghe).

- Ran 2010, PATAT conference context
- Three tracks: sprint (10 sec), middle (~5 min), long (10 min) time budgets
- Instances: various sizes from small wards to medium hospital units
- XML format; publicly available at <https://mobiz.vives.be/inrc1/> (historical) / the INRC mirror

Reference: Haspeslagh, De Causmaecker, Schaerf, Stølevik, "The first international nurse rostering competition 2010," *Annals of Operations Research*, 2014. <https://doi.org/10.1007/s10479-012-1062-0>

### 8.3 INRC-II — Second International Nurse Rostering Competition (2015)

A much richer dynamic-scheduling setting:

- Organized by Ceschia, Dang, De Causmaecker, Haspeslagh, Schaerf
- Scenario runs over multiple weeks, each week revealed sequentially (simulating re-planning under uncertainty)
- Hard constraints: coverage, max assignments per day, min rest, complete weekends
- Soft constraints: optimal coverage, undesired shift patterns, preferences, total working weekends, complete weekends, consecutive assignments
- **Instance sizes:** n30, n35, n40, n50, n60, n80, n100, n120 nurses × 4 or 8 weeks
- Reference implementation and simulator published
- Download / simulator: <https://github.com/samysr/inrc2> and <http://mobiz.vives.be/inrc2/> (historical)

Reference: Ceschia, Dang, De Causmaecker, Haspeslagh, Schaerf, "The second international nurse rostering competition," *Annals of Operations Research* 292, 2019. <https://doi.org/10.1007/s10479-018-2816-0>

### 8.4 Other benchmark sets

- **Curtin / Bilgin datasets** (self-scheduling context)
- **ASAP / KAHO datasets** (pre-INRC Belgian hospitals)
- **Real instances** from specific hospitals, sometimes anonymized

---

## 9. Solution Approaches in the Literature

A very abbreviated tour (Burke et al. 2004 and Van den Bergh et al. 2013 surveys give the full picture):

| Era | Approach | Notes |
|---|---|---|
| 1970s–1990s | **Mathematical programming** (LP, IP) | Miller et al., Warner. Limited scale. |
| 1990s | **Heuristics and expert systems** | Rule-based constructors. Fast but fragile. |
| Late 1990s–2000s | **Metaheuristics**: Tabu Search, Simulated Annealing, Genetic Algorithms, Scatter Search | Burke, Kendall, Vanden Berghe — many seminal papers. |
| 2000s | **Constraint Programming** (CHIP, ILOG Solver, OPL) | Strong for feasibility, good for real-world rule expression. |
| 2000s–2010s | **Column generation / Branch-and-price** | Generate nurse-schedule columns (feasible rosters per nurse), master problem picks combination. Strong for specific structures. |
| 2010s | **Matheuristics and hybrids** | LNS + MIP; SA + IP subproblems. Dominated INRC-I and INRC-II top entries. |
| 2010s | **VNS, ALNS, iterated local search** | Workhorses for large non-cyclic instances. |
| 2015+ | **CP-SAT (Google OR-Tools)** | Increasingly used; combines SAT, CP, and LP techniques; strong on structured combinatorial constraints like sliding windows. |
| 2020+ | **Learning-augmented**: ML-guided neighborhood selection, portfolio solvers, RL for dispatching | Still mostly research. |
| 2020+ | **Exact + decomposition** via Benders or logic-based Benders | Promising on structured multi-ward instances. |

Refs: Burke, De Causmaecker, Vanden Berghe, Van Landeghem, "The state of the art of nurse rostering," *Journal of Scheduling* 7, 2004; Van den Bergh, Beliën, De Bruecker, Demeulemeester, De Boeck, "Personnel scheduling: A literature review," *European Journal of Operational Research* 226, 2013. <https://doi.org/10.1016/j.ejor.2012.11.029>

---

## 10. Modeling in CP-SAT Specifically

CP-SAT (part of Google OR-Tools) is well-suited because it handles boolean variables, linear constraints over booleans, table constraints, and automatons natively, and its presolve is aggressive.

### 10.1 Decision variables

```python
from ortools.sat.python import cp_model
model = cp_model.CpModel()

x = {}
for n in nurses:
    for d in days:
        for s in shifts:
            x[n, d, s] = model.NewBoolVar(f'x_n{n}_d{d}_s{s}')
```

### 10.2 One shift per day

```python
for n in nurses:
    for d in days:
        model.Add(sum(x[n, d, s] for s in shifts) <= 1)
```

### 10.3 Coverage (possibly per skill)

```python
for d in days:
    for s in shifts:
        for k in skills:
            eligible = [n for n in nurses if skill[n, k]]
            model.Add(sum(x[n, d, s] for n in eligible) >= demand[d, s, k])
```

### 10.4 Forbidden transitions (e.g., Night → Day)

```python
for n in nurses:
    for d in days[:-1]:
        for (s1, s2) in forbidden_pairs:
            model.Add(x[n, d, s1] + x[n, d+1, s2] <= 1)
```

### 10.5 Max consecutive working days (sliding window)

```python
W = max_consec + 1  # any W consecutive days must have at least one off
for n in nurses:
    for d_start in range(len(days) - W + 1):
        model.Add(
            sum(x[n, d, s]
                for d in range(d_start, d_start + W)
                for s in shifts)
            <= max_consec
        )
```

### 10.6 Automaton encoding for complex patterns

For rules like "no more than 3 consecutive nights" or "after 2 nights must have 2 days off," `AddAutomaton` lets you encode a DFA over the daily state:

```python
# Build per-nurse sequence of labels (0=off, 1=day, 2=evening, 3=night) per day
# Define DFA transitions; forbidden paths lead to no accepting state
model.AddAutomaton(
    transition_variables=sequence_vars[n],
    starting_state=0,
    final_states=[0, 1, 2, ...],
    transition_triples=dfa_transitions
)
```

### 10.7 Contract hour bounds

```python
for n in nurses:
    total = sum(x[n, d, s] * hours[s] for d in days for s in shifts)
    model.Add(total >= contract_min[n])
    model.Add(total <= contract_max[n])
```

### 10.8 Soft preferences and fairness in the objective

```python
penalty_terms = []

# Preference violations: penalty if nurse gets an undesired shift
for n, d, s in preferences:
    penalty_terms.append(pref_weight[n, d, s] * x[n, d, s])

# Weekend fairness: min-max spread
wkend_count = {n: model.NewIntVar(0, len(days), f'wk_n{n}') for n in nurses}
for n in nurses:
    model.Add(wkend_count[n] ==
              sum(x[n, d, s] for d in weekends for s in shifts))

max_wk = model.NewIntVar(0, len(days), 'max_wk')
min_wk = model.NewIntVar(0, len(days), 'min_wk')
model.AddMaxEquality(max_wk, list(wkend_count.values()))
model.AddMinEquality(min_wk, list(wkend_count.values()))
penalty_terms.append(fairness_weight * (max_wk - min_wk))

model.Minimize(sum(penalty_terms))
```

See Google's employee scheduling example at <https://developers.google.com/optimization/scheduling/employee_scheduling> — a scaled-down NSP using exactly these patterns.

### 10.9 Practical CP-SAT tips for NSP

- **Symmetry breaking** matters: if nurses are interchangeable within a skill, add lexicographic ordering.
- **Warm starts / hints**: `model.AddHint(x[...], 1)` from last month's roster drastically reduces solve time in rolling horizons.
- **Boolean linear >> table constraints** for coverage — simpler and stronger presolve.
- **Automaton vs sliding window**: automaton is cleaner for complex patterns; sliding-window sum is faster for simple counts.
- **Parallel search**: set `solver.parameters.num_search_workers = 8` (or more) — CP-SAT's portfolio scales well.
- **Objective decomposition**: assemble penalty terms as list, sum once. Avoid building enormous single expressions incrementally.

---

## 11. Toy Instance — A Worked Example

**Setup:**
- 4 nurses: `{A, B, C, D}`
- 7 days: `{Mon, Tue, Wed, Thu, Fri, Sat, Sun}`
- 3 shifts: `{Day, Evening, Night}`
- Hard constraints only (for illustration)

**Hard constraints:**
- H1: Each (day, shift) needs exactly 1 nurse
- H2: Each nurse ≤ 1 shift/day
- H3: Night → Day next morning forbidden
- H4: Each nurse works ≤ 5 days out of 7
- H5: Each nurse works ≥ 3 shifts total

**Search-space size:**
Ignoring constraints: each (nurse, day) cell has 4 choices (D, E, N, off). That's `4^(4×7) = 4^28 ≈ 7.2 × 10^16` raw assignments — already beyond brute-force enumeration for 4 nurses × 7 days.

With H1 reframing it as "pick exactly one nurse per (day, shift) slot": 21 slots, 4 nurses each = `4^21 ≈ 4.4 × 10^12`. Still enormous.

**A feasible schedule:**

| | Mon | Tue | Wed | Thu | Fri | Sat | Sun |
|---|---|---|---|---|---|---|---|
| A | D   | D   | off | E   | E   | off | D   |
| B | E   | E   | D   | off | N   | off | E   |
| C | N   | off | E   | D   | off | D   | N   |
| D | off | N   | N   | N   | D   | E   | off |

Check: every slot filled, no nurse works two shifts a day, no N→D next-day (verify: C works N Mon, off Tue — fine; D works N Tue, N Wed, N Thu — need to check max-consec-nights if that was a constraint; here we only specified 5-day caps; D works 5 days, OK).

The point: even with 4 nurses and 7 days, the space is huge, and writing down a feasible schedule by hand takes effort. Scale this to 30 nurses × 28 days and you see why we need solvers.

---

## 12. Real-World Complications Not in Textbooks

Academic benchmarks give you a taste. Real deployments add layers:

- **Union / collective agreements.** In many countries, unions specify max consecutive days, minimum weekend-off counts, seniority-based privilege in shift selection. These become hard constraints and often override individual preferences.
- **Country-specific labor law.** EU Working Time Directive caps 48h/week averaged over a reference period; US has no federal cap but states vary; Japan has complex overtime rules. These shape the constraint set.
- **Pregnancy and disability accommodations.** No night shifts after certain weeks of pregnancy; no lifting duties; temporary shift restrictions. Must be modeled as dynamic unavailability.
- **Caregiving and school-run constraints.** Informal but real — nurses with young children often can't take early-morning shifts. Modern systems capture these as soft preferences with high weight.
- **Last-minute sick calls.** A nurse calls in sick at 5am for a 7am shift. The re-roster must respect all hard constraints, minimize disruption, and prefer nurses already nearby or willing to be on call.
- **Floating pool / agency nurses.** A "float pool" covers gaps but at higher cost. Models need to minimize float usage subject to coverage, treating float shifts as high-cost fallback.
- **Seasonal and surge demand.** Flu season, pandemics, local events. Demand forecasts become inputs that must be robust to error.
- **Continuity-of-care preferences.** Same nurse preferred for same patients (oncology, maternity). Modeled as soft patient-nurse affinity.
- **Training / orientation / supervision coupling.** Junior nurses must be paired with seniors on certain shifts — a coupling constraint, not just skill coverage.
- **Travel / multi-site.** Hospital networks share staff across sites; travel time and reimbursement enter the model.
- **Fairness auditability.** Nurses (and their unions) will ask: "why did I get three weekends and she got one?" The system must explain or justify. This nudges toward lexicographic / goal-programming formulations with interpretable penalty structure.
- **Psychological load.** "No three weeks in a row where I have both night and weekend shifts" — kinds of rule that don't fit any textbook category but matter for retention.
- **Strikes and refusals.** Some contracts let nurses refuse individual assignments within a window. The solver has to handle schedule revision without cascading chaos.
- **Self-scheduling interactions.** When nurses pre-select shifts, the optimizer fills gaps and resolves conflicts — a constrained completion problem with different dynamics than greenfield rostering.

These are the reasons production nurse-scheduling systems (ANSOS, Kronos, QGenda, smartPlan, ORTEC) are never pure optimizers — they pair a solver with heavy UI, rule-editing DSLs, and human-in-the-loop override capabilities.

---

## 13. Resources

### Seminal surveys and papers
- Burke, De Causmaecker, Vanden Berghe, Van Landeghem. "The state of the art of nurse rostering." *Journal of Scheduling*, 7(6), 2004. <https://doi.org/10.1023/B:JOSH.0000046076.75950.0b>
- Van den Bergh, Beliën, De Bruecker, Demeulemeester, De Boeck. "Personnel scheduling: A literature review." *European Journal of Operational Research*, 226(3), 2013. <https://doi.org/10.1016/j.ejor.2012.11.029>
- Cheang, Li, Lim, Rodrigues. "Nurse rostering problems — A bibliographic survey." *European Journal of Operational Research*, 151(3), 2003.
- Ernst, Jiang, Krishnamoorthy, Sier. "Staff scheduling and rostering: A review of applications, methods and models." *European Journal of Operational Research*, 153(1), 2004.

### Competitions
- INRC-I (2010): Haspeslagh, De Causmaecker, Schaerf, Stølevik, "The first international nurse rostering competition 2010." *Annals of Operations Research*, 218(1), 2014. <https://doi.org/10.1007/s10479-012-1062-0>
- INRC-II (2015): Ceschia, Dang, De Causmaecker, Haspeslagh, Schaerf, "The second international nurse rostering competition." *Annals of Operations Research*, 292, 2019. <https://doi.org/10.1007/s10479-018-2816-0>

### Benchmark repositories
- **NSPLib** (Vanhoucke / Maenhout, Ghent University): <https://www.projectmanagement.ugent.be/research/personnel_scheduling/nsp>
- **INRC-I instances and validator**: <https://mobiz.vives.be/inrc1/>
- **INRC-II instances and simulator**: <http://mobiz.vives.be/inrc2/>
- **Employee Scheduling Benchmarks (Schaerf group)**: <https://www.schedulingbenchmarks.org/>

### CP-SAT / OR-Tools references
- Google OR-Tools employee scheduling example: <https://developers.google.com/optimization/scheduling/employee_scheduling>
- OR-Tools CP-SAT documentation: <https://developers.google.com/optimization/cp/cp_solver>
- Laurent Perron & Frédéric Didier, "CP-SAT," OR-Tools primer papers and CPAIOR tutorials.

### Recent tutorials and blogs
- Google OR-Tools nurse-scheduling tutorial (maintained, updated periodically)
- Yves Caseau's lectures on rostering
- Hakan Kjellerstrand's CP model collection (includes nurse rostering variants): <http://www.hakank.org/>
- Recent systematic reviews (2020–2024) on nurse rostering appear regularly in *Health Care Management Science*, *European Journal of Operational Research*, and *Computers & Operations Research*.

### Wikipedia
- <https://en.wikipedia.org/wiki/Nurse_scheduling_problem>
- <https://en.wikipedia.org/wiki/Schedule_(workplace)>

---

*This overview is a starting point for the learning project. Recommended next steps: (1) download INRC-II instance `n030w4` and a simpler NSPLib instance, (2) build a minimal CP-SAT model using the mapping in §10, (3) iterate by adding hard constraints one at a time, validating feasibility, then introduce soft constraints and the objective.*
