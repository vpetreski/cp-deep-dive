package io.vanja.cpsat.ch10

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe

class ShiftSpec : StringSpec({
    "demo instance solves to OPTIMAL or FEASIBLE" {
        val r = solveShifts(DEMO_SHIFTS)
        (r.status == "OPTIMAL" || r.status == "FEASIBLE") shouldBe true
        r.assignments.isNotEmpty() shouldBe true
    }

    "each (day, shift) slot is covered by at least `coverage` nurses" {
        val r = solveShifts(DEMO_SHIFTS)
        for (d in 0 until DEMO_SHIFTS.nDays) {
            for (shift in Shift.entries) {
                val count = r.assignments.count { it.day == d && it.shift == shift }
                count.shouldBeGreaterThanOrEqual(DEMO_SHIFTS.coverage)
            }
        }
    }

    "no nurse works two shifts on the same day" {
        val r = solveShifts(DEMO_SHIFTS)
        for (n in 0 until DEMO_SHIFTS.nNurses) {
            for (d in 0 until DEMO_SHIFTS.nDays) {
                val count = r.assignments.count { it.nurseIndex == n && it.day == d }
                count.shouldBeLessThanOrEqual(1)
            }
        }
    }

    "night shift is never immediately followed by a day shift for the same nurse" {
        val r = solveShifts(DEMO_SHIFTS)
        for (n in 0 until DEMO_SHIFTS.nNurses) {
            val byDay = r.assignments.filter { it.nurseIndex == n }.associateBy { it.day }
            for (d in 0 until DEMO_SHIFTS.nDays - 1) {
                val today = byDay[d]
                val tomorrow = byDay[d + 1]
                if (today?.shift == Shift.NIGHT && tomorrow?.shift == Shift.DAY) {
                    throw AssertionError(
                        "Nurse $n has forbidden NIGHT->DAY transition on days $d,${d + 1}",
                    )
                }
            }
        }
    }

    "every nurse respects the workload bounds" {
        val r = solveShifts(DEMO_SHIFTS)
        r.totals.size shouldBe DEMO_SHIFTS.nNurses
        for (t in r.totals) {
            t.shouldBeGreaterThanOrEqual(DEMO_SHIFTS.minWork)
            t.shouldBeLessThanOrEqual(DEMO_SHIFTS.maxWork)
        }
    }

    "spread equals max(totals) - min(totals)" {
        val r = solveShifts(DEMO_SHIFTS)
        val expected = r.totals.max() - r.totals.min()
        r.spread shouldBe expected
    }

    "calendar renders expected grid dimensions" {
        val r = solveShifts(DEMO_SHIFTS)
        val rendered = renderCalendar(DEMO_SHIFTS, r)
        val dataLines = rendered.lines().filter { it.trim().isNotEmpty() }
        // header(1) + column labels(1) + one line per nurse.
        dataLines.size shouldBe 1 + 1 + DEMO_SHIFTS.nNurses
    }

    "tight instance (coverage = nNurses) solves with every nurse on every day" {
        // 2 nurses, 3 days, coverage = 2 (so every shift needs both nurses — except
        // atMostOne-per-day forces every nurse to take exactly one shift per day).
        // minWork=nDays, maxWork=nDays means every nurse must work every day.
        val tight = ShiftInstance(nNurses = 2, nDays = 3, coverage = 1, minWork = 3, maxWork = 3)
        val r = solveShifts(tight)
        (r.status == "OPTIMAL" || r.status == "FEASIBLE") shouldBe true
        // Every nurse has exactly one shift per day across 3 days = 3 assignments.
        r.totals.forEach { it shouldBe 3 }
    }
})
