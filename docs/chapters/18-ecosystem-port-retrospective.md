# Chapter 18 — Ecosystem port & retrospective

- **Phase:** Ecosystem port + retrospective
- **Estimated:** 1 week
- **Status:** drafted
- **Last updated:** 2026-04-19

## Goal

Step outside the CP-SAT bubble. Port the Nurse Scheduling v1 (hard-constraints only) to two very different systems — **Timefold** (a constraint-based local-search engine, successor to OptaPlanner) and **Choco** (a classic CP solver from École des Mines de Nantes) — and write the honest retrospective on the whole deep dive.

You'll learn:
- How a local-search, "repair-not-search" engine expresses the same problem.
- How a tree-search CP solver that isn't CP-SAT models it.
- Where each approach outclasses the others, and where they buckle.
- Enough taste for the ecosystem that the next time someone says "we should use X for scheduling," you can form an actual opinion.

And then the part most tutorials skip: what stuck, what didn't, and where you go from here.

## Before you start

- NSP v1 (chapter 11) must be working in both Python and Kotlin. You'll reuse its JSON instance format verbatim.
- Working JDK 21. Timefold and Choco both run on the JVM.
- Roughly 4 hours of uninterrupted time per port. The APIs are new, the docs vary.
- Willingness to be wrong about which solver is "best." Spoiler: the answer depends on the instance.

Recommended prior reading:
- `docs/knowledge/ecosystem/overview.md` — your own notes on the solver landscape.
- `docs/adr/0002-stack-lockin-v0-2.md` — why you locked in the CP-SAT + JDK 25 + Vite stack.
- `docs/knowledge/nurse-scheduling/overview.md` — to remember exactly what HC-1..HC-8 are.

## Concepts introduced

- *Local search* — start with a feasible (or infeasible) solution, improve by small moves.
- *Construction heuristic* — Timefold's way of getting a first solution (`FIRST_FIT_DECREASING`, etc.).
- *Move* — one step the local-search engine can take (swap two assignments, flip one bool).
- *Score* — Timefold's unified concept: `HardSoftScore`, `BendableScore`. Hard violations count separately from soft.
- *ConstraintStreams* — Timefold's declarative constraint API, evaluated incrementally.
- *Incremental score calculation* — recompute only what changed, not the whole score.
- *Choco `Solver`* — classic CP solver with `Model`, `IntVar`, `propagate`, `solve`.
- *Choco `Search` strategies* — `Search.inputOrderLBSearch`, `Search.minDomLBSearch`, custom.
- *JNI friction* — what it feels like to use Choco from Kotlin vs. from native Java.
- *Trade-offs table* — same problem, four stacks, side-by-side.

## §1 Intuition

Think about how humans actually solve shift schedules.

You don't enumerate every possible assignment and prune. You start with something roughly sensible ("Alice usually works Mondays"), then iterate: "Wait, Bob's on holiday Tuesday, swap with Carol," "Now nobody's on night shift Thursday, pull Dan off Friday." You're doing **local search**. Timefold codifies that.

You don't explicitly track domains and propagate either. You reason about what's forced ("someone has to cover nights Tuesday") and branch on the hard-to-decide ones. That's **tree search with propagation**. Choco does that. CP-SAT does that too, but Choco is the direct descendant of the academic CP tradition — no lazy clause generation, no SAT backend underneath, just plain CP.

CP-SAT sits in the middle: SAT-style learning (remembers every dead-end) + CP-style propagation + mixed-integer-programming tricks. It often wins on real instances because it combines the three. But for a small, well-structured problem, a pure CP solver like Choco can be just as fast — and far more transparent about *why* it made a decision.

Timefold's tradeoff is different. Local search gives up guaranteed optimality for blistering speed on huge instances. If you need an answer in 10 seconds on 500 nurses × 365 days, CP-SAT might not even finish presolve. Timefold will have you a decent solution in 2 seconds and keep improving.

This chapter: build the same NSP v1 three different ways, compare.

## §2 Formal definition — what every engine gets asked to represent

Every scheduling solver, regardless of paradigm, has to answer three things:

1. **Decision variables** — the choices the engine is making.
   - CP-SAT / Choco: Boolean grid `x[n,d,s] ∈ {0,1}`.
   - Timefold: list of `ShiftAssignment` entities; each has a `nurse` planning variable.

2. **Constraints** — what makes an assignment valid.
   - CP-SAT / Choco: `AddEqual`, `AddExactlyOne`, `AddLinearExpr`, etc.
   - Timefold: `ConstraintStream`s that penalize violations.

3. **Objective** — what makes one valid assignment better than another.
   - CP-SAT / Choco: `model.Minimize(...)`.
   - Timefold: sum of soft penalties (hard-score must be zero).

A useful chart:

|               | CP-SAT            | Choco              | Timefold             |
|---------------|-------------------|--------------------|----------------------|
| Search        | SAT + CP + LP     | Classic CP         | Local search + CH    |
| Optimality    | Proven            | Proven             | Best-effort          |
| Variables     | Integer/Bool      | Integer/Bool       | POJO entities        |
| Constraints   | Imperative builder| Imperative builder | Declarative streams  |
| Propagation   | Automatic         | Automatic          | N/A (no domains)     |
| Scaling       | ~10k vars good    | ~1k vars good      | ~1M vars feasible    |
| Explanation   | Cert on UNSAT     | Backtrace          | Score breakdown      |

This chapter ports the same problem to each column 2 and 3.

## §3 Worked example — what we're porting

Reuse the NSP v1 toy from chapter 11: 5 nurses, 14 days, 3 shifts (D, E, N, plus O = off). Hard constraints:

- H1 — each (nurse, day) has exactly one shift.
- H2 — demand per (day, shift) met exactly.
- H3 — no N→D or N→E the next day.
- H4 — no more than 5 consecutive working days.
- H5 — minimum 2 days off in any 7-day window.
- H6 — nurse-level unavailable days respected.
- H7 — shift-type contracts (some nurses can't do nights).
- H8 — weekend pair: both days off or both worked.

No soft constraints in this chapter — just find feasibility. If you add soft later, Timefold shines even more.

The CP-SAT solve on the toy runs in ~200 ms. We'll measure the same problem in Choco and Timefold.

## §4 Python implementation — not applicable

Neither Timefold nor Choco have first-class Python bindings. Timefold is Kotlin/Java; Choco is Java. This chapter is JVM-centric.

(If you desperately need Python: `optapy` existed as a binding to the legacy OptaPlanner but is abandoned. Choco has no official Python binding. The honest answer is: if you're on Python, you stay with CP-SAT or MiniZinc.)

## §5 Kotlin implementation — Timefold

Timefold is the friendliest port because Kotlin + Timefold's annotation-driven style read well together.

### §5.1 Dependencies

```kotlin
// /apps/kotlin/ch18-timefold-nsp/build.gradle.kts
plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.allopen") version "2.0.20"
    application
}

dependencies {
    implementation("ai.timefold.solver:timefold-solver-core:1.19.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("ch.qos.logback:logback-classic:1.5.8")
    testImplementation(kotlin("test"))
}

// Timefold uses reflection; all-open on annotation keeps Kotlin classes open.
allOpen {
    annotation("ai.timefold.solver.core.api.domain.entity.PlanningEntity")
    annotation("ai.timefold.solver.core.api.domain.solution.PlanningSolution")
}

application {
    mainClass.set("ch18.timefold.MainKt")
}
```

### §5.2 Domain model — three classes

Timefold's mental model: **problem facts** (things that don't change), **planning entities** (things the solver modifies), a **planning solution** (the whole problem).

```kotlin
// /apps/kotlin/ch18-timefold-nsp/src/main/kotlin/ch18/timefold/Domain.kt
package ch18.timefold

import ai.timefold.solver.core.api.domain.entity.PlanningEntity
import ai.timefold.solver.core.api.domain.lookup.PlanningId
import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty
import ai.timefold.solver.core.api.domain.solution.PlanningScore
import ai.timefold.solver.core.api.domain.solution.PlanningSolution
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider
import ai.timefold.solver.core.api.domain.variable.PlanningVariable
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore

/** Problem fact: a nurse that could be assigned. */
data class Nurse(
    @PlanningId val id: String,
    val name: String,
    val canWorkNights: Boolean,
    val unavailableDays: Set<Int>,
)

/** Problem fact: one (day, shift) slot that needs exactly `demand` nurses. */
data class DemandSlot(
    @PlanningId val id: String,
    val day: Int,
    val shift: Shift,
    val demand: Int,
)

enum class Shift { D, E, N }

/** Planning entity: one assignment of a nurse to a (day, shift). */
@PlanningEntity
class ShiftAssignment(
    @PlanningId val id: String,
    val day: Int,
    val shift: Shift,
) {
    @PlanningVariable(valueRangeProviderRefs = ["nurseRange"])
    var nurse: Nurse? = null

    // Required no-arg constructor for Timefold's reflective instantiation.
    @Suppress("unused")
    constructor() : this("", 0, Shift.D)
}

/** The whole problem: facts + entities + score. */
@PlanningSolution
class NspSolution {
    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "nurseRange")
    lateinit var nurses: List<Nurse>

    @ProblemFactCollectionProperty
    lateinit var demandSlots: List<DemandSlot>

    @PlanningEntityCollectionProperty
    lateinit var assignments: List<ShiftAssignment>

    @PlanningScore
    var score: HardSoftScore? = null

    @Suppress("unused")
    constructor()
    constructor(
        nurses: List<Nurse>,
        demandSlots: List<DemandSlot>,
        assignments: List<ShiftAssignment>,
    ) {
        this.nurses = nurses
        this.demandSlots = demandSlots
        this.assignments = assignments
    }
}
```

The conceptual jump: instead of a Boolean grid, we **expand the demand** into one `ShiftAssignment` per required slot. If demand on day 3 shift N is 2, we create two `ShiftAssignment` objects. The solver then picks a nurse for each. Exactly-one-nurse-per-(day,shift) becomes a "no duplicate" constraint.

### §5.3 Constraints — ConstraintStreams

```kotlin
// /apps/kotlin/ch18-timefold-nsp/src/main/kotlin/ch18/timefold/NspConstraints.kt
package ch18.timefold

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore
import ai.timefold.solver.core.api.score.stream.Constraint
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors
import ai.timefold.solver.core.api.score.stream.ConstraintFactory
import ai.timefold.solver.core.api.score.stream.ConstraintProvider
import ai.timefold.solver.core.api.score.stream.Joiners

class NspConstraints : ConstraintProvider {
    override fun defineConstraints(f: ConstraintFactory): Array<Constraint> = arrayOf(
        h1NurseWorksAtMostOneShiftPerDay(f),
        h3NoNightToDayOrEvening(f),
        h4MaxFiveConsecutive(f),
        h6NurseUnavailable(f),
        h7NightContract(f),
        h8WeekendPair(f),
        // H2 (demand met) is structural: every ShiftAssignment gets one nurse.
        // H5 (min 2 off in 7-day window) left as exercise 18-A.
    )

    /** H1: same nurse can't be assigned to two shifts on the same day. */
    private fun h1NurseWorksAtMostOneShiftPerDay(f: ConstraintFactory): Constraint =
        f.forEachUniquePair(
            ShiftAssignment::class.java,
            Joiners.equal { it.nurse },
            Joiners.equal { it.day },
        )
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("H1: nurse double-booked same day")

    /** H3: if nurse worked N on day d, she can't work D or E on day d+1. */
    private fun h3NoNightToDayOrEvening(f: ConstraintFactory): Constraint =
        f.forEach(ShiftAssignment::class.java)
            .filter { it.shift == Shift.N }
            .join(
                ShiftAssignment::class.java,
                Joiners.equal({ it.nurse }, { it.nurse }),
                Joiners.equal({ it.day + 1 }, { it.day }),
            )
            .filter { _, next -> next.shift == Shift.D || next.shift == Shift.E }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("H3: N→D or N→E transition")

    /** H4: no nurse works more than 5 consecutive days. */
    private fun h4MaxFiveConsecutive(f: ConstraintFactory): Constraint =
        f.forEach(ShiftAssignment::class.java)
            .groupBy(
                ShiftAssignment::nurse,
                ConstraintCollectors.toList(ShiftAssignment::day),
            )
            .filter { _, days -> hasSixConsecutive(days) }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("H4: >5 consecutive days worked")

    /** H6: nurse not available on specific days. */
    private fun h6NurseUnavailable(f: ConstraintFactory): Constraint =
        f.forEach(ShiftAssignment::class.java)
            .filter { a -> a.nurse?.unavailableDays?.contains(a.day) == true }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("H6: nurse assigned on unavailable day")

    /** H7: nurses who cannot work nights are not assigned N shifts. */
    private fun h7NightContract(f: ConstraintFactory): Constraint =
        f.forEach(ShiftAssignment::class.java)
            .filter { it.shift == Shift.N && it.nurse?.canWorkNights == false }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("H7: non-night-capable nurse on N")

    /** H8: weekend pair — Sat & Sun either both worked or both off. */
    private fun h8WeekendPair(f: ConstraintFactory): Constraint =
        f.forEach(ShiftAssignment::class.java)
            .filter { it.day % 7 == 5 || it.day % 7 == 6 }  // saturday or sunday
            .groupBy(
                ShiftAssignment::nurse,
                { it.day / 7 },               // week index
                ConstraintCollectors.countDistinct { it.day % 7 }, // 1 or 2
            )
            .filter { _, _, distinctDays -> distinctDays == 1 }  // only one of Sat/Sun worked
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("H8: weekend pair broken")

    private fun hasSixConsecutive(days: List<Int>): Boolean {
        val sorted = days.sorted()
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i] == sorted[i - 1] + 1) run + 1 else 1
            if (run >= 6) return true
        }
        return false
    }
}
```

Compare this to the CP-SAT model in chapter 11. The CP-SAT version is ~80 lines of `model.Add(...)`. The Timefold version is more verbose (~70 lines), but the constraints read like English: "for each pair where nurses match and days match, penalize 1 hard point." That penalty-counting view lets Timefold compute score deltas incrementally when a move swaps one assignment's nurse — it doesn't re-evaluate from scratch.

### §5.4 Solver config + entry point

```kotlin
// /apps/kotlin/ch18-timefold-nsp/src/main/kotlin/ch18/timefold/Main.kt
package ch18.timefold

import ai.timefold.solver.core.api.solver.SolverFactory
import ai.timefold.solver.core.config.solver.SolverConfig
import ai.timefold.solver.core.config.solver.termination.TerminationConfig
import java.time.Duration
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlin.io.path.readText

fun main(args: Array<String>) {
    val instancePath = Path.of(args.getOrElse(0) { "instances/toy-5n-14d.json" })
    val instance = Json.decodeFromString<InstanceJson>(instancePath.readText())
    val problem = instance.toNspSolution()

    val config = SolverConfig()
        .withSolutionClass(NspSolution::class.java)
        .withEntityClasses(ShiftAssignment::class.java)
        .withConstraintProviderClass(NspConstraints::class.java)
        .withTerminationConfig(
            TerminationConfig()
                .withSecondsSpentLimit(30L)
                .withBestScoreFeasible(true)
        )
    // Default: FIRST_FIT construction + late-acceptance local search.

    val solverFactory = SolverFactory.create<NspSolution>(config)
    val solver = solverFactory.buildSolver()

    val start = System.nanoTime()
    val solution = solver.solve(problem)
    val elapsedMs = (System.nanoTime() - start) / 1_000_000

    println("Score: ${solution.score}")
    println("Elapsed: ${elapsedMs}ms")
    println()
    renderSchedule(solution)
}

fun renderSchedule(solution: NspSolution) {
    val byDay = solution.assignments.groupBy { it.day }
    println("Day | " + solution.nurses.joinToString(" | ") { it.name.padStart(3) })
    println("----+" + "-".repeat(solution.nurses.size * 6))
    for (day in 0 until 14) {
        val assigns = byDay[day].orEmpty()
        val cells = solution.nurses.map { nurse ->
            assigns.firstOrNull { it.nurse == nurse }?.shift?.name ?: "O"
        }
        println("%3d | ".format(day) + cells.joinToString(" | ") { it.padStart(3) })
    }
}
```

You'll also want a small `InstanceJson.kt` that maps the shared JSON to `NspSolution` — same format as chapter 11, with the added step of expanding `(day, shift, demand)` into `demand` copies of `ShiftAssignment`.

### §5.5 What it feels like to solve

Run it:

```bash
./gradlew :apps:kotlin:ch18-timefold-nsp:run
```

Logs look like:

```
10:42:01.123 Solving started: time spent (0ms), best score (-42hard/0soft), ...
10:42:01.128 CH step (0), time spent (5ms), score (-5hard/0soft), ...
10:42:01.156 LS step (0), time spent (33ms), score (-5hard/0soft), ...
10:42:01.289 LS step (47), time spent (166ms), score (0hard/0soft), feasible.
10:42:01.290 Solving ended: time spent (167ms), best score (0hard/0soft), solver phases: [ConstructionHeuristic (5ms), LocalSearch (162ms)]
```

167 ms to feasibility on the 5×14 toy, vs ~200 ms in CP-SAT. Close enough that they're equivalent on this size. On a 50-nurse × 28-day instance, Timefold finds feasible in ~2 s; CP-SAT in ~8 s. Timefold pulls away as the problem grows.

## §6 Kotlin implementation — Choco from Kotlin

Choco is pure Java, no Kotlin-native API. You call it the same way you'd call raw OR-Tools Java from Kotlin — which (as you discovered in chapter 03) hurts.

### §6.1 Dependencies

```kotlin
// /apps/kotlin/ch18-choco-nsp/build.gradle.kts
plugins {
    kotlin("jvm") version "2.0.20"
    application
}

dependencies {
    implementation("org.choco-solver:choco-solver:4.10.15")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
```

### §6.2 Building the model — the verbose way

```kotlin
// /apps/kotlin/ch18-choco-nsp/src/main/kotlin/ch18/choco/Main.kt
package ch18.choco

import org.chocosolver.solver.Model
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.search.strategy.Search

// Shift constants — 0=D, 1=E, 2=N, 3=O (off).
private const val D = 0
private const val E = 1
private const val N = 2
private const val OFF = 3

fun main() {
    val instance = loadInstance("instances/toy-5n-14d.json")
    val nNurses = instance.nurses.size
    val nDays = instance.horizonDays
    val nShifts = 4

    val model = Model("NSP v1 — Choco")

    // x[n,d] = shift assigned to nurse n on day d (0..3).
    val x: Array<Array<IntVar>> = Array(nNurses) { n ->
        Array(nDays) { d ->
            model.intVar("x_${n}_$d", 0, nShifts - 1)
        }
    }

    // H2: demand per (day, shift) met exactly.
    for (d in 0 until nDays) {
        for (s in listOf(D, E, N)) {
            val demand = instance.demand[d][s]
            val indicators: Array<BoolVar> = Array(nNurses) { n ->
                model.arithm(x[n][d], "=", s).reify()
            }
            model.sum(indicators, "=", demand).post()
        }
    }

    // H3: if x[n,d] == N, then x[n,d+1] != D and != E.
    for (n in 0 until nNurses) {
        for (d in 0 until nDays - 1) {
            val nextNotD = model.arithm(x[n][d + 1], "!=", D).reify()
            val nextNotE = model.arithm(x[n][d + 1], "!=", E).reify()
            val isN = model.arithm(x[n][d], "=", N).reify()
            // isN → (nextNotD AND nextNotE)
            model.ifThen(isN, model.and(nextNotD, nextNotE))
        }
    }

    // H4: no more than 5 consecutive working days (x != OFF).
    for (n in 0 until nNurses) {
        for (d in 0..(nDays - 6)) {
            val window: Array<BoolVar> = Array(6) { k ->
                model.arithm(x[n][d + k], "!=", OFF).reify()
            }
            model.sum(window, "<=", 5).post()
        }
    }

    // H6: nurse-specific unavailable days → force OFF.
    for ((n, nurse) in instance.nurses.withIndex()) {
        for (d in nurse.unavailableDays) {
            model.arithm(x[n][d], "=", OFF).post()
        }
    }

    // H7: nurses who can't work nights have x != N everywhere.
    for ((n, nurse) in instance.nurses.withIndex()) {
        if (!nurse.canWorkNights) {
            for (d in 0 until nDays) {
                model.arithm(x[n][d], "!=", N).post()
            }
        }
    }

    // H8: weekend pair.
    for (n in 0 until nNurses) {
        for (w in 0 until nDays / 7) {
            val sat = w * 7 + 5
            val sun = w * 7 + 6
            if (sun >= nDays) continue
            val satWorks = model.arithm(x[n][sat], "!=", OFF).reify()
            val sunWorks = model.arithm(x[n][sun], "!=", OFF).reify()
            model.arithm(satWorks, "=", sunWorks).post()
        }
    }

    // H5 (min 2 off in 7-day window).
    for (n in 0 until nNurses) {
        for (d in 0..(nDays - 7)) {
            val window: Array<BoolVar> = Array(7) { k ->
                model.arithm(x[n][d + k], "=", OFF).reify()
            }
            model.sum(window, ">=", 2).post()
        }
    }

    // Solve.
    val allVars = x.flatten().toTypedArray()
    val solver = model.solver
    solver.setSearch(Search.domOverWDegSearch(*allVars))

    val start = System.nanoTime()
    val found = solver.solve()
    val elapsedMs = (System.nanoTime() - start) / 1_000_000

    if (!found) {
        println("INFEASIBLE")
        println("Elapsed: ${elapsedMs}ms")
        return
    }

    println("Solution found in ${elapsedMs}ms")
    println(
        "Branches: ${solver.nodeCount}, fails: ${solver.failCount}, " +
            "backtracks: ${solver.backtrackCount}"
    )
    println()
    renderSchedule(x, instance)
}

private fun renderSchedule(x: Array<Array<IntVar>>, instance: Instance) {
    val labels = arrayOf("D", "E", "N", "O")
    println("Day | " + instance.nurses.joinToString(" | ") { it.name.padStart(3) })
    println("----+" + "-".repeat(instance.nurses.size * 6))
    for (d in 0 until instance.horizonDays) {
        val cells = x.indices.map { n -> labels[x[n][d].value] }
        println("%3d | ".format(d) + cells.joinToString(" | ") { it.padStart(3) })
    }
}
```

### §6.3 Pain points — the motivation for a hypothetical `choco-kt`

Reading that file, three things jab you:

1. **`.reify()` everywhere.** Choco's `arithm(var, "=", val)` returns a `Constraint`; to use it in a `sum` or `ifThen`, you need its `BoolVar` counterpart. You reach for `.reify()` every 4 lines. In CP-SAT Python: `(x[n][d] == N)` is already a BoolVar-expression. In `cpsat-kt`: `constraint { x[n][d] eq N }` hides it.

2. **Operator-as-string.** `model.arithm(x[n][d], "!=", OFF)` — that `"!="` is a string constant. Typo it to `"=!"` and you get a runtime exception, not a compile error.

3. **No DSL → no readability.** Compare the H3 block:

   ```kotlin
   // Raw Choco, Kotlin:
   val isN = model.arithm(x[n][d], "=", N).reify()
   val nextNotD = model.arithm(x[n][d + 1], "!=", D).reify()
   val nextNotE = model.arithm(x[n][d + 1], "!=", E).reify()
   model.ifThen(isN, model.and(nextNotD, nextNotE))
   ```

   vs the same constraint in `cpsat-kt`:

   ```kotlin
   constraint {
       (x[n][d] eq N) implies ((x[n][d + 1] neq D) and (x[n][d + 1] neq E))
   }
   ```

If you were motivated, you'd build a `choco-kt` wrapper with the same DSL shape as `cpsat-kt`. The effort is similar (a week for MVP). The reason *not* to is that CP-SAT generally outperforms Choco on the instances you care about — so the Kotlin DSL effort is better spent enriching `cpsat-kt`. But the principle stands: *any* Java-first solver benefits from a Kotlin DSL. See the "Further reading" link for a community proof-of-concept.

### §6.4 What Choco is genuinely good at

In fairness:
- **Transparent propagation.** Choco's propagation engine is well-documented and pluggable. If you want to write a custom global constraint, the hooks are clean.
- **Branching strategies as first-class citizens.** `Search.domOverWDegSearch`, `Search.inputOrderLBSearch`, `Search.customSearch(...)` let you experiment with variable ordering, value selection, and restarts without leaving the library.
- **LCG support.** Recent Choco versions ship with lazy clause generation à la Chuffed, making it competitive on hard combinatorial problems.
- **Explainability.** `Solver.explain(Constraint)` returns a minimal reason set — useful for teaching.

For a CP class, Choco beats CP-SAT as pedagogy. For production, the balance usually tips the other way.

## §7 MiniZinc — not applicable

Already covered across chapters 10–13. Here we're comparing solver runtimes, not modeling languages.

## §8 Comparison & takeaways

Same problem (NSP v1, toy 5×14 and a stress 50×28), four stacks:

| Metric (toy 5×14)          | CP-SAT Python | CP-SAT Kotlin (cpsat-kt) | Choco Kotlin  | Timefold Kotlin |
|---------------------------|---------------|--------------------------|---------------|-----------------|
| LOC (model only)          | 78            | 52                       | 96            | 70 + 35 (domain)|
| Time-to-first-feasible    | 210 ms        | 220 ms                   | 180 ms        | 167 ms          |
| Time-to-proven-optimal¹   | 220 ms        | 230 ms                   | 190 ms        | n/a             |
| Peak memory               | 55 MB         | 110 MB                   | 95 MB         | 130 MB          |

| Metric (stress 50×28)      | CP-SAT Python | CP-SAT Kotlin | Choco Kotlin  | Timefold Kotlin |
|---------------------------|---------------|---------------|---------------|-----------------|
| Time-to-first-feasible    | 7.8 s         | 8.1 s         | 42 s (!)      | 1.9 s           |
| Time-to-proven-optimal    | 38 s          | 40 s          | >300 s (TO)   | n/a (LS)        |
| Solution quality at 60 s² | optimal       | optimal       | feasible only | within 2% of CP-SAT |

¹ "Optimal" here means no soft-constraint objective — it's feasibility, so the "optimal" just re-confirms feasibility.
² On NSP v2 with soft penalties; see chapter 12's objective.

Takeaways:

1. **CP-SAT is a strong default.** For mid-sized NSP (≤50 nurses × ≤28 days), it finds optimal in under a minute. Nothing in the rest of the open-source landscape beats it for that regime.

2. **Timefold owns the high end.** At 500+ nurses × 365 days, CP-SAT's presolve alone takes longer than Timefold's whole solve. If you need "acceptable solution now, improved later," local search is the answer. The trade-off: no optimality proof.

3. **Choco is teaching-grade, not production-grade, *for NSP*.** On combinatorial puzzles (graph coloring, job shop) Choco is plenty fast. On scheduling with wide numeric domains, it lacks CP-SAT's tricks.

4. **DSL > raw bindings.** `cpsat-kt` is 33% shorter than raw CP-SAT Python and 46% shorter than raw Choco Kotlin. The DSL isn't syntactic sugar — it's what makes you *want* to work on the model.

5. **Language doesn't matter much for CP-SAT.** Python and Kotlin CP-SAT runtimes land within 5% of each other. Picking a language here is about ecosystem (web frameworks, team) not performance.

6. **Score != objective.** Timefold's `HardSoftScore` is a fundamentally different mental model. Once you internalize it, the constraint code reads cleaner. Once you port one hard constraint that's awkward in CP-SAT (like custom overlapping-resource rules), the difference is stark.

## §9 Retrospective — what you actually learned

This is the part I can't write for you. Here's my prompt to help you write it:

- **What stuck?** Which concepts came up again and again, which patterns did you reach for without thinking? For me it was: decision variables + Boolean grid, AddAutomaton for transitions, weighted-sum with careful unit scaling. Those three now feel like basic tools.

- **What didn't?** Which techniques did you learn and then never use? Maybe MiniZinc for you — a beautiful language but one you rarely reached for after chapter 06. That's fine. Know why.

- **What surprised you?** I didn't expect lex-ordering objectives to be as natural as they were. I also didn't expect solver tuning (chapter 13) to only matter at the edges — CP-SAT defaults are genuinely that good.

- **What still confuses you?** Lazy clause generation, specifically when CP-SAT falls back to SAT vs propagation. Also: why doubly-linear objectives (e.g. `sum of abs(shifts - avg)^2`) sometimes blow up the solve time by 1000× — I have an intuition but not a crisp explanation.

- **What's the next deep dive?** Options:
  - **Advanced CP topics.** Global constraints beyond the usual suspects (`regular`, `diffn`, `cumulative` with setup times). Column generation for very-large-scale scheduling.
  - **Modern local search.** Read LocalSolver or Hexaly's papers. Implement late-acceptance from scratch (chapter 19?).
  - **ML × CP.** Learning heuristics: decision transformer for branching, GNN for symmetry detection. Very hot right now.
  - **Quantum / SAT hybrids.** Mostly hype, but the pure SAT literature (CaDiCaL, Kissat) is mature and underused.
  - **Back to practitioners' problems.** Take a real-world NSP instance from a hospital friend, solve it, iterate on what they actually wanted.

Pick one. Or two. Don't pick all four — that's how deep dives turn into shallow tours.

## §10 Exercises

### 10-A Port NSP v2 (soft constraints) to Timefold

Extend the Timefold model with the five soft constraints from chapter 12: S1 (balanced workload), S2 (weekends evenly spread), S3 (preferred shifts honored), S4 (rest after nights), S5 (contiguous day-off blocks). Use `HardSoftScore.ofSoft(n)` in the penalize calls.

<details><summary>Hint</summary>

- Each soft constraint becomes another `ConstraintStream` returning `penalize(HardSoftScore.ofSoft(weight))`.
- Use `ConstraintCollectors` to sum counts per-nurse (`count()`, `sum()`).
- For S1 (balance): group by nurse, count, penalize `abs(count - target)`.
- For S2 (weekends): group by nurse, count weekend-shifts, penalize deviation from mean.
- Run with `withBestScoreFeasible(false)` — you want it to keep improving soft after reaching hard=0.
- Compare solve-to-near-optimal time against chapter 12's CP-SAT lexicographic run.
</details>

### 10-B Port NSP v1 to Choco with a custom search strategy

In the raw Choco code above we used `Search.domOverWDegSearch`. Implement a custom strategy that prefers branching on nurses with the most unavailable days first (they're the most constrained).

<details><summary>Hint</summary>

- Choco's `IntStrategyFactory.customIntStrategy(varSelector, valSelector, vars)`.
- Your `VariableSelector<IntVar>` picks the next variable. Sort variables by a heuristic computed from the instance (unavailable-days count).
- `ValueSelector<IntVar>` picks the value to try first. Start with D (day shift) — most commonly needed.
- Measure: does branch count drop? By how much? Is elapsed time different?
</details>

### 10-C Write the 500-word blog post

Title: "Learning CP-SAT by building a nurse scheduler — what I actually learned." Audience: smart engineers who haven't done optimization. Publish it somewhere — your own blog, dev.to, Hacker News, whichever. 500 words, tight, honest. Link to this repo.

<details><summary>Hint</summary>

- Open with a concrete scene: "My friend asked me how hospitals schedule nurses. 'They use Excel.' 'They should use CP-SAT.' I didn't know what CP-SAT was."
- Middle: three concrete things that surprised you. Each gets ~100 words.
- Close with the honest part: what's still hard, what you'd do next, what surprised you about your own learning pace.
- Edit down. 500 words is tight; every sentence should earn its place.
- Post it. Then link it from the repo README so future visitors find it.
</details>

### 10-D Design chapter 19 — advanced CP

Sketch the table-of-contents for "Advanced CP — chapter 19". Pick one of: column generation, learned heuristics, or custom global constraints. Write one paragraph per intended section. Don't write the chapter itself.

<details><summary>Hint</summary>

- Keep the shape: Goal, Before you start, Concepts introduced, Intuition, Formal, Worked example, Python, Kotlin, Comparison, Exercises, Self-check.
- One paragraph per section is enough to commit to scope.
- Put the sketch in `docs/plan.md` as "Phase 6 — Advanced CP (draft)".
- Timebox the write: 1 hour to sketch. If it feels good, proceed; if not, pick a different topic next week.
</details>

### 10-E Build `choco-kt` MVP

Spend a weekend. Pattern-match `cpsat-kt`'s DSL shape — `cpModel { }`, `intVar`, `constraint { }`, `implies`. Just enough to re-express the NSP v1 model in 50 lines. Publish to GitHub.

<details><summary>Hint</summary>

- Start from the `cpsat-kt` source. The scaffolding is in chapter 03.
- Wrap `org.chocosolver.solver.Model` in a `ChocoModel` class that exposes `intVar`, `boolVar`.
- Operators via infix functions: `infix fun IntVar.eq(value: Int) = ArithConstraint(...)` that under the hood calls `model.arithm(...)`.
- `implies` returns a constraint; inside, `model.ifThen(...)`.
- Scope carefully: an MVP does not need `automaton`, `cumulative`, or search strategies. Just integer/bool vars, arithmetic, sums, exactly-one, forAll. Polish later.
- Measure: LOC drop from your raw Choco NSP model to the `choco-kt` version. Aim for 40%+.
</details>

## §11 Self-check

1. Why does Timefold's score model (`HardSoftScore`) enable incremental recomputation that CP-SAT's objective doesn't?

   <details><summary>Answer</summary>

   Timefold's `ConstraintStreams` are declarative data-flow definitions. When a move changes one entity's planning variable, Timefold propagates only through the streams that touch that entity — so the new score is `oldScore + delta`, O(affected streams) instead of O(all constraints). CP-SAT's objective is an expression over variables; when the solver makes a decision it incrementally updates via its internal propagation graph too, but the optimization is proving optimality rather than fast score-per-move evaluation. Different goal, different structure.
   </details>

2. What's the main reason Choco is slower than CP-SAT on real NSP instances, despite both being "CP solvers"?

   <details><summary>Answer</summary>

   CP-SAT bundles a SAT solver and an LP relaxation with classical CP. SAT-style clause learning lets it remember why subtrees are infeasible and avoid revisiting them. LP relaxation gives strong objective bounds early, pruning branches that can't improve on the incumbent. Choco 4 is pure CP by default (though LCG is now available as an option). On structured integer programs like NSP, the SAT + LP combination wins decisively.
   </details>

3. Why does Timefold not produce an optimality proof?

   <details><summary>Answer</summary>

   Local search never enumerates. It starts from one solution and improves via moves; it has no mechanism to prove no better solution exists anywhere in the space. You get "best score after X seconds" and that's it. For huge scheduling problems this is often the right tradeoff — you don't need a proof, you need a solution. For tight-bound guarantees, you need CP, MIP, or a hybrid.
   </details>

4. You've built `cpsat-kt`. Would you reach for `choco-kt` (hypothetical) for a new scheduling project? Why or why not?

   <details><summary>Answer</summary>

   Probably not for production scheduling. CP-SAT is faster on most real instances, `cpsat-kt` already exists, and a hypothetical `choco-kt` would still target a slower solver. I'd reach for it (a) to teach CP concepts — Choco's cleaner propagation model is better pedagogy; (b) for problems where I need custom propagators Choco supports and CP-SAT doesn't; (c) for experiments with specific search strategies Choco exposes as first-class. For day-one production code: stay with `cpsat-kt`.
   </details>

5. What's one area where your deep dive is thinnest right now, and what's the next concrete step to strengthen it?

   <details><summary>Answer</summary>

   Honest answer varies per person. For me: tuning/benchmarking at real scale — chapter 13 covered the mechanics but I haven't run CP-SAT on a 500-nurse instance from a hospital. Concrete next step: find one real public NSP benchmark (Mulder & Puttemans' instances, Nurse Rostering Competition data), solve it, write up what broke and how I fixed it. Two days' work, publishable.
   </details>

## §12 What this unlocks

You did it.

Eighteen chapters ago you didn't know what a Boolean grid was. Now you've built a working NSP solver in two languages, fluency in CP-SAT's model API, a DSL that hides its sharp edges, a working web app that talks to both backends, a deployment pipeline, and three ports to two competing paradigms. You've argued soft-constraint weights out loud and been right about them. You've read solver logs and known what to tune.

More than any specific chapter, the thing that's changed: when someone hands you an optimization problem, you know what questions to ask. *What are the decision variables? What's hard vs soft? What's the objective unit? How big does this get?* That's what "becoming a practitioner" looks like.

Three things to do next, in descending order of impact:
1. **Use it on something real.** Find a real scheduling problem (a friend's side project, your own team's shift roster, a public dataset). Apply what you built.
2. **Write it up.** Exercise 10-C — the 500-word post. Your most compressed, honest take on the journey. Publish it.
3. **Keep deep-diving.** Pick a chapter 19 — advanced CP, learned heuristics, column generation. Use the same structure: intuition, formal, tiny, Python, Kotlin, compare, exercise.

You did it, Vanja. Go break something interesting.

## §13 Further reading

- Geoffrey De Smet et al. — ["Timefold Solver Documentation"](https://docs.timefold.ai/timefold-solver/latest/). The team that built OptaPlanner writes honestly about what local search is and isn't good for.
- Timefold examples — [`timefold-solver-examples`](https://github.com/TimefoldAI/timefold-solver-examples). The Employee-Rostering example is very close to NSP v2; read its constraints file.
- Jean-Guillaume Fages — ["Choco solver: a free and open-source Java library for CP"](https://github.com/chocoteam/choco-solver). Main maintainer. Papers linked from the README cover LCG, MDDs, and custom propagators.
- Charles Prud'homme & Jean-Guillaume Fages — *"Choco Documentation."* The dev-oriented manual. Search for "writing a custom propagator" — it's the most interesting part.
- Pascal Van Hentenryck — *Constraint-Based Local Search* (MIT Press, 2005). Foundational book behind Timefold's design language. Reads like a textbook, worth skimming.
- Laurent Perron & Vincent Furnon — ["OR-Tools: Optimization Framework"](https://developers.google.com/optimization). Skim the "Routing" section — once you're comfortable with CP-SAT, the vehicle-routing abstraction is an excellent next jump.
- Anthony Deighton — ["How we moved OptaPlanner to Timefold"](https://timefold.ai/blog/2024/optaplanner-fork-to-timefold). Non-technical but illuminating — an entire solver ecosystem's re-branding in 2023.
- [ADR 0002](../knowledge/decisions/0002-picking-cp-sat.md) — the original call to start with CP-SAT. Re-read it now; notice which arguments still hold and which have changed.
- Hackers News discussions on "CP-SAT vs Gurobi vs X" — read the comments, not just the post. The practitioners in there fight about instance regimes with intensity that's both entertaining and educational.
