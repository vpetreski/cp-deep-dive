package io.vanja.cpsat.ch06

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.math.roundToLong

class CircuitTspSpec : StringSpec({
    "octagon TSP visits each city exactly once" {
        val r = solveTsp(cities = OCTAGON_CITIES)
        r.order.size shouldBe OCTAGON_CITIES.size
        r.order.toSet().size shouldBe OCTAGON_CITIES.size
        r.order[0] shouldBe 0
    }

    "octagon TSP optimal distance matches the perimeter" {
        val r = solveTsp(cities = OCTAGON_CITIES)
        val perimeter = tourDistance(OCTAGON_CITIES, (0 until OCTAGON_CITIES.size).toList())
        val perimeterScaled = (perimeter * 1000.0).roundToLong()
        // Solver should not be worse than the obvious perimeter tour.
        r.totalDistanceScaled.shouldBeLessThanOrEqual(perimeterScaled)
    }

    "straight-line 3-city TSP trivially covers all cities" {
        val cities = listOf(
            City("A", 0.0, 0.0),
            City("B", 1.0, 0.0),
            City("C", 2.0, 0.0),
        )
        val r = solveTsp(cities = cities, timeLimitS = 5.0)
        r.order.toSet() shouldBe setOf(0, 1, 2)
    }
})
