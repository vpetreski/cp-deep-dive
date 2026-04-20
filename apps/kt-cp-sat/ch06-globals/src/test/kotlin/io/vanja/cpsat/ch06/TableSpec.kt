package io.vanja.cpsat.ch06

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TableSpec : StringSpec({
    "monday night -> only Bob in the allowed tuples" {
        val sol = solveTableDemo(pinDay = Day.MON, pinShift = Shift.NIGHT)
        sol shouldNotBe null
        val s = checkNotNull(sol)
        s.chosen.day shouldBe Day.MON
        s.chosen.shift shouldBe Shift.NIGHT
        s.chosen.nurse shouldBe Nurse.BOB
    }

    "tuesday morning -> only Bob in the allowed tuples" {
        val sol = solveTableDemo(pinDay = Day.TUE, pinShift = Shift.MORNING)
        sol shouldNotBe null
        val s = checkNotNull(sol)
        s.chosen.nurse shouldBe Nurse.BOB
    }

    "wednesday night -> only Carol" {
        val sol = solveTableDemo(pinDay = Day.WED, pinShift = Shift.NIGHT)
        sol shouldNotBe null
        val s = checkNotNull(sol)
        s.chosen.nurse shouldBe Nurse.CAROL
    }
})
