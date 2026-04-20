package io.vanja.cpsat.ch10

/**
 * Chapter 10 entry point. Solves the default 5-nurse × 7-day × 2-shift roster
 * and prints the calendar. This is the warm-up for the full NSP in chapters
 * 11-13: every constraint shape used here (coverage, one-per-day, workload,
 * calendar-aware transitions) reappears there.
 */
fun main() {
    val result = solveShifts(DEMO_SHIFTS)
    println(renderCalendar(DEMO_SHIFTS, result))
}
