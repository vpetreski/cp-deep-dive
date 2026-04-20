package io.vanja.altsolver.timefold

import ai.timefold.solver.core.api.solver.SolverFactory
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig
import ai.timefold.solver.core.config.solver.SolverConfig
import ai.timefold.solver.core.config.solver.termination.TerminationConfig
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.measureTimeMillis

/**
 * CLI entry point: `./gradlew run --args="<instance.json> <secondsLimit>"`.
 *
 * Example:
 *   ./gradlew run --args="../../../data/nsp/toy-01.json 30"
 */
fun main(args: Array<String>) {
    if (args.size !in 1..2) {
        System.err.println(
            "Usage: run <instance.json> [secondsLimit=30]"
        )
        kotlin.system.exitProcess(2)
    }
    val instancePath = Paths.get(args[0])
    val secondsLimit = args.getOrNull(1)?.toLongOrNull() ?: 30L

    val result = solveNsp(instancePath, secondsLimit, seed = FIXED_SEED)
    val solution = result.solution

    println("Instance: ${solution.nurses.size} nurses, ${solution.days.size} days, " +
            "${solution.shifts.size} shifts, ${solution.assignments.size} slots")
    println("Time limit: ${secondsLimit}s   Random seed: $FIXED_SEED")
    println("Score: ${solution.score}")
    println("Feasible: ${result.feasible}   Wall time: ${result.wallTimeMs}ms")
    println()
    println(renderSchedule(solution))
}

/** Wraps the solve loop so Kotest specs and the benchmark runner can reuse it. */
fun solveNsp(
    instancePath: Path,
    secondsLimit: Long,
    seed: Long = FIXED_SEED,
): TimefoldSolveResult {
    val instance = loadInstance(instancePath)
    val problem = NspSolutionFactory.build(instance)

    val solverConfig = SolverConfig()
        .withSolutionClass(NspSolution::class.java)
        .withEntityClasses(ShiftAssignment::class.java)
        .withScoreDirectorFactory(
            ScoreDirectorFactoryConfig()
                .withConstraintProviderClass(NspConstraintProvider::class.java)
        )
        .withTerminationConfig(
            TerminationConfig()
                .withSecondsSpentLimit(secondsLimit)
                // If we hit a feasible (hard==0) solution early, stop —
                // local search will keep polishing soft score forever otherwise
                // and we want deterministic benchmark behaviour.
                // Kept commented because the benchmark expects each run to
                // consume the full time budget; uncomment for fast tests.
                // .withBestScoreFeasible(true)
        )
        .withRandomSeed(seed)

    val solver = SolverFactory.create<NspSolution>(solverConfig).buildSolver()

    lateinit var solved: NspSolution
    val wallMs = measureTimeMillis {
        solved = solver.solve(problem)
    }
    val score = solved.score
    val hard = score?.hardScore() ?: Int.MIN_VALUE
    val soft = score?.softScore() ?: Int.MIN_VALUE
    return TimefoldSolveResult(
        solution = solved,
        hardScore = hard,
        softScore = soft,
        feasible = hard == 0,
        wallTimeMs = wallMs,
    )
}

/** The solver result in the shape test code + the benchmark harness want. */
data class TimefoldSolveResult(
    val solution: NspSolution,
    val hardScore: Int,
    val softScore: Int,
    val feasible: Boolean,
    val wallTimeMs: Long,
)

/** Fixed seed so benchmarks and tests are deterministic across runs. */
const val FIXED_SEED: Long = 42L
