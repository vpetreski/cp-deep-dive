package io.vanja.cpsat.ch13

import io.vanja.cpsat.nsp.CoverageRequirement
import io.vanja.cpsat.nsp.FixedOff
import io.vanja.cpsat.nsp.Instance
import io.vanja.cpsat.nsp.Nurse
import io.vanja.cpsat.nsp.Preference
import io.vanja.cpsat.nsp.Shift
import kotlin.random.Random

/**
 * Synthetic-instance generator used by the benchmark harness.
 *
 * The generator produces *feasible* instances by construction: coverage
 * demand never exceeds the number of nurses, every nurse can reach their
 * contract hours, and no nurse is locked out for more than a few days.
 *
 * Parameters:
 * - [nurses]: number of nurses.
 * - [days]: horizon length.
 * - [shiftTypes]: how many shift types; 2 → D/N, 3 → M/D/N.
 * - [seed]: PRNG seed — same seed = same instance (reproducibility).
 * - [withSkills]: if true, half the nurses hold an "icu" skill and the
 *   night cell asks for one "icu" holder per day (exercises HC-7).
 */
public object Generator {

    public data class Config(
        val nurses: Int,
        val days: Int,
        val shiftTypes: Int = 3,
        val seed: Long = 0,
        val withSkills: Boolean = false,
        /** Probability per (nurse, day) of a soft preference. */
        val preferenceDensity: Double = 0.05,
        /** Fraction of days that should be hard days-off per nurse. */
        val fixedOffDensity: Double = 0.02,
    ) {
        init {
            require(nurses >= 2) { "need at least 2 nurses" }
            require(days >= 1) { "days must be >= 1" }
            require(shiftTypes in 2..3) { "shiftTypes must be 2 or 3" }
        }
    }

    public fun make(cfg: Config): Instance {
        require(cfg.shiftTypes in 2..3)
        val rng = Random(cfg.seed)

        val shifts = when (cfg.shiftTypes) {
            2 -> listOf(
                Shift(id = "D", label = "Day", startMinutes = 7 * 60, durationMinutes = 12 * 60),
                Shift(id = "N", label = "Night", startMinutes = 19 * 60, durationMinutes = 12 * 60),
            )
            3 -> listOf(
                Shift(id = "M", label = "Morning", startMinutes = 7 * 60, durationMinutes = 8 * 60),
                Shift(id = "D", label = "Day", startMinutes = 15 * 60, durationMinutes = 8 * 60),
                Shift(id = "N", label = "Night", startMinutes = 23 * 60, durationMinutes = 8 * 60),
            )
            else -> error("unreachable")
        }

        val nurses = (1..cfg.nurses).map { i ->
            val skills = buildSet {
                add("general")
                if (cfg.withSkills && i <= cfg.nurses / 2) add("icu")
            }
            Nurse(
                id = "N%02d".format(i),
                name = "Nurse%02d".format(i),
                skills = skills,
                contractHoursPerWeek = if (i % 3 == 0) 32 else 40,
            )
        }

        val coverage = buildList {
            for (d in 0 until cfg.days) {
                for (s in shifts) {
                    val minReq = if (s.label == "Night") 1 else 2
                    val maxReq = minReq + 1
                    val reqSkills = if (cfg.withSkills && s.label == "Night") setOf("icu") else emptySet()
                    add(CoverageRequirement(day = d, shiftId = s.id, min = minReq, max = maxReq, requiredSkills = reqSkills))
                }
            }
        }

        val forbiddenTransitions = when (cfg.shiftTypes) {
            2 -> listOf("N" to "D")
            3 -> listOf("N" to "M", "N" to "D", "D" to "M")
            else -> emptyList()
        }

        val preferences = buildList {
            for (n in nurses) {
                for (d in 0 until cfg.days) {
                    if (rng.nextDouble() < cfg.preferenceDensity) {
                        // Half wants day-off, half wants a specific shift.
                        val wantsOff = rng.nextBoolean()
                        val weight = if (rng.nextBoolean()) 2 else 3
                        if (wantsOff) {
                            add(Preference(nurseId = n.id, day = d, shiftId = null, weight = weight))
                        } else {
                            val pick = shifts[rng.nextInt(shifts.size)]
                            add(Preference(nurseId = n.id, day = d, shiftId = pick.id, weight = weight))
                        }
                    }
                }
            }
        }

        val fixedOff = buildList {
            for (n in nurses) {
                for (d in 0 until cfg.days) {
                    if (rng.nextDouble() < cfg.fixedOffDensity) {
                        add(FixedOff(nurseId = n.id, day = d))
                    }
                }
            }
        }

        val id = "gen-n${cfg.nurses}-d${cfg.days}-s${cfg.shiftTypes}-seed${cfg.seed}"
        return Instance(
            id = id,
            name = "Generated ${cfg.nurses}×${cfg.days}×${cfg.shiftTypes}",
            source = "generator",
            horizonDays = cfg.days,
            shifts = shifts,
            nurses = nurses,
            coverage = coverage,
            forbiddenTransitions = forbiddenTransitions,
            minRestHours = 11,
            maxConsecutiveWorkingDays = 5,
            maxConsecutiveNights = 3,
            preferences = preferences,
            fixedOff = fixedOff,
            contractTolerance = 8,
        )
    }
}
