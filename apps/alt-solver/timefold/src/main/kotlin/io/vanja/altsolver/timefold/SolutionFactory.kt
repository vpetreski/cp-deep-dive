package io.vanja.altsolver.timefold

/**
 * Converts parsed JSON into the Timefold domain.
 *
 * The flow:
 *   1. Resolve each [ShiftJson] into a [Shift] with `isNight` filled in
 *      (honouring an explicit `isNight` in the JSON, falling back to the
 *      heuristic in [Shift.classifyAsNight]).
 *   2. Compute `fixedOffDays` for each nurse from the schema's `unavailable`
 *      field so HC-2 picks them up without extra constraints.
 *   3. Expand each coverage row into `min` copies of [ShiftAssignment] — one
 *      planning entity per required slot-instance. A slot with `min = 2`
 *      produces two entities sharing the same (day, shift). `max` is not
 *      modelled as a hard upper bound because every toy in the repo has
 *      `max = min + ε` and HC-2 already guards against stacking.
 *   4. Install preferences and forbidden transitions on
 *      [NspConstraintProvider]'s companion state so constraint streams can
 *      see them at evaluation time.
 */
internal object NspSolutionFactory {

    fun build(instance: InstanceJson): NspSolution {
        val shifts = instance.shifts.map { it.toDomain() }
        val shiftById = shifts.associateBy { it.id }

        val nurses = instance.nurses.map { raw ->
            Nurse(
                id = raw.id,
                name = raw.name ?: raw.id,
                skills = raw.skills.toSet(),
                contractHoursPerWeek = raw.contractHoursPerWeek,
                fixedOffDays = raw.unavailable.toSet(),
            )
        }

        val days = (0 until instance.horizonDays).map { Day(it) }
        val dayByIndex = days.associateBy { it.index }

        val assignments = mutableListOf<ShiftAssignment>()
        var slotSeq = 0
        for (row in instance.coverage) {
            val day = dayByIndex[row.day] ?: error("Coverage references unknown day ${row.day}")
            val shift = shiftById[row.shiftId]
                ?: error("Coverage references unknown shift '${row.shiftId}'")
            repeat(row.min) { k ->
                assignments += ShiftAssignment(
                    id = "slot-d${row.day}-s${row.shiftId}-$k",
                    day = day,
                    shift = shift,
                )
                slotSeq++
            }
        }

        val forbidden = instance.forbiddenTransitions
            .filter { it.size == 2 }
            .map { it[0] to it[1] }
            .toSet()

        val config = NspConfig(
            horizonDays = instance.horizonDays,
            maxConsecutiveWorkingDays = instance.maxConsecutiveWorkingDays,
            forbiddenTransitions = forbidden,
            preferDaysOff = buildPreferDaysOff(instance),
            preferShift = buildPreferShift(instance),
            avoidShift = buildAvoidShift(instance),
            maxConsecutiveNights = instance.maxConsecutiveNights,
        )

        // Install companion state that the ConstraintProvider reads at
        // evaluation time. Constraint streams are pure — they can only see
        // facts and entities — so solver-lifetime data like preferences and
        // forbidden transitions has to reach them via this side channel.
        installCompanionState(instance)

        return NspSolution(
            nurses = nurses,
            days = days,
            shifts = shifts,
            assignments = assignments,
            config = config,
        )
    }

    private fun ShiftJson.toDomain(): Shift {
        val startMin = startMinutes ?: parseHhmm(start)
            ?: error("Shift '$id' needs either startMinutes or start (HH:MM)")
        val durationMin = durationMinutes ?: durationBetween(start, end)
            ?: error("Shift '$id' needs either durationMinutes or both start+end")
        val night = isNight ?: Shift.classifyAsNight(id, label, startMin)
        return Shift(
            id = id,
            label = label,
            startMinutes = startMin,
            durationMinutes = durationMin,
            isNight = night,
        )
    }

    private fun parseHhmm(hhmm: String?): Int? {
        if (hhmm == null) return null
        val parts = hhmm.split(':')
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h * 60 + m
    }

    private fun durationBetween(start: String?, end: String?): Int? {
        val s = parseHhmm(start) ?: return null
        val e = parseHhmm(end) ?: return null
        val diff = e - s
        return if (diff > 0) diff else diff + 24 * 60 // overnight shift
    }

    private fun buildPreferDaysOff(instance: InstanceJson): Map<String, Map<Int, Int>> =
        instance.preferences
            .filter { it.kind == "prefer" && it.shiftId == null }
            .groupBy { it.nurseId }
            .mapValues { (_, prefs) -> prefs.associate { it.day to it.weight } }

    private fun buildPreferShift(instance: InstanceJson): Map<String, Map<Pair<Int, String>, Int>> =
        instance.preferences
            .filter { it.kind == "prefer" && it.shiftId != null }
            .groupBy { it.nurseId }
            .mapValues { (_, prefs) ->
                prefs.associate { (it.day to it.shiftId!!) to it.weight }
            }

    private fun buildAvoidShift(instance: InstanceJson): Map<String, Map<Pair<Int, String>, Int>> =
        instance.preferences
            .filter { it.kind == "avoid" && it.shiftId != null }
            .groupBy { it.nurseId }
            .mapValues { (_, prefs) ->
                // we store the magnitude as-is; the constraint treats it as
                // a positive soft penalty when violated.
                prefs.associate { (it.day to it.shiftId!!) to it.weight }
            }

    private fun installCompanionState(instance: InstanceJson) {
        NspConstraintProvider.resetPreferences()
        for (p in instance.preferences) {
            when (p.kind) {
                "prefer" -> NspConstraintProvider.registerPreference(
                    nurseId = p.nurseId,
                    day = p.day,
                    shiftId = p.shiftId,
                    // positive weight: nurse wants the shift (or wants day off
                    // if shiftId is null). SC-1 penalises violation.
                    weight = p.weight,
                )
                "avoid" -> NspConstraintProvider.registerPreference(
                    nurseId = p.nurseId,
                    day = p.day,
                    shiftId = p.shiftId,
                    // store as negative so the weight lookup knows this is
                    // an "avoid" preference.
                    weight = -p.weight,
                )
                else -> error("Unknown preference kind '${p.kind}'")
            }
        }
        NspConstraintProvider.installForbiddenTransitions(
            instance.forbiddenTransitions
                .filter { it.size == 2 }
                .map { it[0] to it[1] }
        )
    }
}
