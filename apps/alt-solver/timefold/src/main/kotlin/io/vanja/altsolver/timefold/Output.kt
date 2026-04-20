package io.vanja.altsolver.timefold

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore

/**
 * Flat `(nurse, day, shift)` triple emitted by every solver in this repo.
 *
 * Keeping a minimal record here (rather than sharing a module) deliberately —
 * see CLAUDE.md's guidance on cross-module coupling in the alt-solver tree.
 * The benchmark harness and the CP-SAT Kotlin port produce records with the
 * same three fields in the same order, so cross-solver diffs stay easy.
 */
data class ScheduleAssignment(val nurseId: String, val day: Int, val shiftId: String)

/**
 * Result of a single solver run. `objective` is the raw soft score magnitude
 * (Timefold's soft is negative-when-bad; we flip the sign so "lower = better"
 * matches CP-SAT's minimization convention across the repo).
 */
data class SolveResult(
    val assignments: List<ScheduleAssignment>,
    val score: HardSoftScore?,
    val hardScore: Int,
    val softScore: Int,
    val feasible: Boolean,
    val wallTimeMs: Long,
)

/** Render a nurse x day grid. Unassigned cells render as `.`. */
fun renderSchedule(solution: NspSolution): String {
    val nurseOrder = solution.nurses.sortedBy { it.id }
    val days = solution.days.sortedBy { it.index }
    val shiftsByNurseDay = solution.assignments
        .asSequence()
        .filter { it.nurse != null }
        .groupBy { it.nurse!!.id to it.day.index }
        .mapValues { (_, rows) -> rows.joinToString("+") { it.shift.id } }

    val header = buildString {
        append("nurse  ")
        for (d in days) append("d${"%02d".format(d.index)} ")
    }
    val lines = mutableListOf(header)
    for (n in nurseOrder) {
        val row = buildString {
            append("${n.id.padEnd(6)} ")
            for (d in days) {
                val cell = shiftsByNurseDay[n.id to d.index] ?: "."
                append(cell.padEnd(3)).append(" ")
            }
        }
        lines += row
    }
    return lines.joinToString("\n")
}

/** Extract the flat assignment list from a solved [NspSolution]. */
fun NspSolution.toScheduleAssignments(): List<ScheduleAssignment> =
    assignments
        .asSequence()
        .filter { it.nurse != null }
        .map { ScheduleAssignment(it.nurse!!.id, it.day.index, it.shift.id) }
        .sortedWith(compareBy({ it.day }, { it.shiftId }, { it.nurseId }))
        .toList()
