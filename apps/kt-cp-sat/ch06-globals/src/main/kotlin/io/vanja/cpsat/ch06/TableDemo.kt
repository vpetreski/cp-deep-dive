package io.vanja.cpsat.ch06

import io.vanja.cpsat.*

/**
 * Table (AllowedAssignments) — enumerate the allowed joint values of a
 * handful of variables as explicit rows.
 *
 * Shape:
 *   (day, shift, nurse) must match one of:
 *     (Mon, morning, Alice)
 *     (Mon, night,   Bob)
 *     (Tue, morning, Bob)
 *     (Tue, night,   Alice)
 *     (Wed, morning, Alice)
 *     (Wed, night,   Carol)
 *
 * Constraint: count how many rows have `shift = night`.
 *
 * This is a compact way to encode arbitrary discrete relations the solver
 * would otherwise reach only via many auxiliary boolean variables.
 */
public enum class Day { MON, TUE, WED }
public enum class Shift { MORNING, NIGHT }
public enum class Nurse { ALICE, BOB, CAROL }

public data class TableRow(val day: Day, val shift: Shift, val nurse: Nurse)

private val ALLOWED: List<TableRow> = listOf(
    TableRow(Day.MON, Shift.MORNING, Nurse.ALICE),
    TableRow(Day.MON, Shift.NIGHT,   Nurse.BOB),
    TableRow(Day.TUE, Shift.MORNING, Nurse.BOB),
    TableRow(Day.TUE, Shift.NIGHT,   Nurse.ALICE),
    TableRow(Day.WED, Shift.MORNING, Nurse.ALICE),
    TableRow(Day.WED, Shift.NIGHT,   Nurse.CAROL),
)

public data class TableSolution(
    val chosen: TableRow,
    val alternativesCount: Int,
)

/**
 * Pick one allowed tuple, pinning [pinDay] + [pinShift] and leaving the nurse
 * as a free variable. Returns the sole matching row (there's exactly one for
 * each (day, shift) pair in [ALLOWED]).
 */
public fun solveTableDemo(
    pinDay: Day,
    pinShift: Shift,
    seed: Int = 42,
    timeLimitS: Double = 5.0,
): TableSolution? {
    lateinit var d: IntVar
    lateinit var s: IntVar
    lateinit var n: IntVar
    val model = cpModel {
        d = intVar("d", 0..Day.entries.size - 1)
        s = intVar("s", 0..Shift.entries.size - 1)
        n = intVar("n", 0..Nurse.entries.size - 1)
        val tuples = ALLOWED.map { row ->
            longArrayOf(row.day.ordinal.toLong(), row.shift.ordinal.toLong(), row.nurse.ordinal.toLong())
        }
        table(listOf(d, s, n), tuples)
        constraint { +(d eq pinDay.ordinal.toLong()) }
        constraint { +(s eq pinShift.ordinal.toLong()) }
    }
    val res = model.solveBlocking {
        randomSeed = seed
        maxTimeInSeconds = timeLimitS
    }
    val assignment = when (res) {
        is SolveResult.Optimal -> res.values
        is SolveResult.Feasible -> res.values
        else -> return null
    }
    val row = TableRow(
        day = Day.entries[assignment[d].toInt()],
        shift = Shift.entries[assignment[s].toInt()],
        nurse = Nurse.entries[assignment[n].toInt()],
    )
    val alt = ALLOWED.count { it.day == pinDay && it.shift == pinShift }
    return TableSolution(row, alt)
}
