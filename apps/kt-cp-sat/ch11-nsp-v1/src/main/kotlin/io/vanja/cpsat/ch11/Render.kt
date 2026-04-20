package io.vanja.cpsat.ch11

import io.vanja.cpsat.nsp.Instance
import io.vanja.cpsat.nsp.Schedule

/**
 * Render a [Schedule] as an ASCII grid:
 *
 * ```
 *    d0 d1 d2 ... d6
 * N1  M  D  -  N  ...
 * N2  D  N  M  -  ...
 * ```
 *
 * Each cell shows the shift id, or `-` for a day off. The header row uses
 * `d{index}` with an optional `*` marker for weekends.
 */
public fun render(instance: Instance, schedule: Schedule): String {
    val days = (0 until instance.horizonDays).toList()
    val sb = StringBuilder()
    val nameWidth = instance.nurses.maxOfOrNull { it.id.length }?.coerceAtLeast(4) ?: 4
    val colWidth = 3

    // Header
    sb.append(" ".repeat(nameWidth + 1))
    for (d in days) {
        val label = if (instance.isWeekend(d)) "*$d" else "d$d"
        sb.append(label.padStart(colWidth))
        sb.append(' ')
    }
    sb.append('\n')

    // Index by (nurseId, day) for O(1) lookup.
    val byCell = schedule.assignments.associateBy { it.nurseId to it.day }
    for (n in instance.nurses) {
        sb.append(n.id.padEnd(nameWidth))
        sb.append(' ')
        for (d in days) {
            val a = byCell[n.id to d]
            val cell = a?.shiftId ?: "-"
            sb.append(cell.padStart(colWidth))
            sb.append(' ')
        }
        sb.append('\n')
    }
    return sb.toString()
}
