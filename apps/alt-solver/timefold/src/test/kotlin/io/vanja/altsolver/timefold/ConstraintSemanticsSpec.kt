package io.vanja.altsolver.timefold

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.nio.file.Paths

/**
 * Pure-data tests that don't run Timefold — they just exercise the loader
 * and factory. Fast feedback for schema / domain mapping bugs.
 */
class ConstraintSemanticsSpec : StringSpec({

    "toy-01 loads with the expected facts" {
        val instance = loadInstance(
            Paths.get("../../../data/nsp/toy-01.json").toAbsolutePath().normalize()
        )
        instance.horizonDays shouldBe 7
        instance.nurses.size shouldBe 3
        instance.shifts.size shouldBe 2
        instance.forbiddenTransitions shouldBe listOf(listOf("N", "D"))
    }

    "NspSolutionFactory expands coverage into the right number of entities" {
        val instance = loadInstance(
            Paths.get("../../../data/nsp/toy-01.json").toAbsolutePath().normalize()
        )
        val solution = NspSolutionFactory.build(instance)

        // toy-01: 7 days × (min=1 D + min=1 N) = 14 entities
        solution.assignments.size shouldBe 14
        solution.nurses.map { it.id } shouldContainExactlyInAnyOrder listOf("N1", "N2", "N3")
        solution.shifts.map { it.id } shouldContainExactlyInAnyOrder listOf("D", "N")
        solution.days.map { it.index } shouldContainExactlyInAnyOrder (0..6).toList()
    }

    "forbidden transitions are mirrored onto the ConstraintProvider companion" {
        val instance = loadInstance(
            Paths.get("../../../data/nsp/toy-02.json").toAbsolutePath().normalize()
        )
        NspSolutionFactory.build(instance)
        NspConstraintProvider.forbiddenTransitions shouldContain ("N" to "M")
        NspConstraintProvider.forbiddenTransitions shouldContain ("N" to "D")
        NspConstraintProvider.forbiddenTransitions shouldContain ("D" to "M")
    }

    "preferences are registered with correct sign on the ConstraintProvider companion" {
        val instance = loadInstance(
            Paths.get("../../../data/nsp/toy-02.json").toAbsolutePath().normalize()
        )
        // Build populates the companion map.
        NspSolutionFactory.build(instance)
        // N3 on day 4 has avoid N (weight 5) -> stored as -5
        val n3d4 = NspConstraintProvider.preferencesByNurseDay["N3" to 4]!!
        n3d4.size shouldBe 1
        n3d4[0].shiftId shouldBe "N"
        n3d4[0].weight shouldBe -5

        // N1 on day 5 prefers day off (weight 2) -> stored as +2 with shiftId null
        val n1d5 = NspConstraintProvider.preferencesByNurseDay["N1" to 5]!!
        n1d5[0].shiftId shouldBe null
        n1d5[0].weight shouldBe 2
    }

    "shift isNight uses the explicit field when the JSON provides it" {
        val instance = loadInstance(
            Paths.get("../../../data/nsp/toy-01.json").toAbsolutePath().normalize()
        )
        val solution = NspSolutionFactory.build(instance)
        val night = solution.shifts.single { it.id == "N" }
        night.isNight shouldBe true
        val day = solution.shifts.single { it.id == "D" }
        day.isNight shouldBe false
    }
})
