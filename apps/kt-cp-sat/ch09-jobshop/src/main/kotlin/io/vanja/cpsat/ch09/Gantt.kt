package io.vanja.cpsat.ch09

/**
 * Minimal SVG Gantt renderer for a job-shop schedule.
 *
 * One row per machine, rectangles per operation. We color rectangles by
 * jobId using an HSL palette so consecutive jobs stand apart.
 *
 * The SVG is intentionally self-contained — no external stylesheets — so
 * callers can drop it into a file and open it in any browser.
 */
public fun renderGanttSvg(
    result: JobShopResult,
    nMachines: Int,
    cellWidth: Int = 30,
    rowHeight: Int = 40,
    margin: Int = 20,
): String {
    val makespan = result.makespan ?: 0
    val width = margin * 2 + makespan * cellWidth + 80
    val height = margin * 2 + nMachines * rowHeight + 40

    val palette = List(16) { i ->
        val hue = (i * 360 / 16)
        "hsl($hue, 70%, 60%)"
    }

    val sb = StringBuilder()
    sb.append(
        """<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" font-family="system-ui, sans-serif" font-size="12">
""",
    )
    // Border
    sb.append("""<rect x="0" y="0" width="$width" height="$height" fill="#fafafa"/>""").append('\n')

    // Machine labels + grid
    for (m in 0 until nMachines) {
        val y = margin + m * rowHeight
        sb.append("""<text x="4" y="${y + rowHeight / 2 + 4}" fill="#333">M$m</text>""").append('\n')
        sb.append("""<line x1="$margin" y1="${y + rowHeight}" x2="${width - margin}" y2="${y + rowHeight}" stroke="#ddd"/>""").append('\n')
    }

    // Time axis ticks every unit
    for (t in 0..makespan) {
        val x = margin + 40 + t * cellWidth
        val yTop = margin
        val yBot = margin + nMachines * rowHeight
        sb.append("""<line x1="$x" y1="$yTop" x2="$x" y2="$yBot" stroke="#eee"/>""").append('\n')
        sb.append("""<text x="$x" y="${yBot + 14}" fill="#666" text-anchor="middle">$t</text>""").append('\n')
    }

    // Operations
    for (sch in result.schedule) {
        val x = margin + 40 + sch.start * cellWidth
        val y = margin + sch.op.machine * rowHeight + 4
        val w = (sch.end - sch.start) * cellWidth
        val h = rowHeight - 8
        val color = palette[sch.op.jobId % palette.size]
        sb.append("""<rect x="$x" y="$y" width="$w" height="$h" fill="$color" stroke="#333" rx="3" ry="3"/>""").append('\n')
        sb.append("""<text x="${x + w / 2}" y="${y + h / 2 + 4}" fill="#111" text-anchor="middle">J${sch.op.jobId}:${sch.op.index}</text>""").append('\n')
    }

    sb.append("</svg>\n")
    return sb.toString()
}
