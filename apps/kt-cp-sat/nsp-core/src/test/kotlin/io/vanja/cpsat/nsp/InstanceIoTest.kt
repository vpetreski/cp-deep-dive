package io.vanja.cpsat.nsp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * JSON I/O contract: toy instances must round-trip through the canonical
 * wire schema and come back with identical semantic content.
 */
class InstanceIoTest : StringSpec({

    "loads toy-01 into a well-formed Instance" {
        val path = locateInstance("toy-01.json")
        val inst = InstanceIo.load(path)
        inst.id shouldBe "toy-01"
        inst.horizonDays shouldBe 7
        inst.shifts shouldHaveSize 2
        inst.nurses shouldHaveSize 3
        inst.coverage shouldHaveSize 14
        inst.forbiddenTransitions shouldContain ("N" to "D")
        // N3 has a hard day-off at day 3 from the toy data.
        val n3 = inst.nurseById["N3"]!!
        n3.unavailableDays shouldContain 3
    }

    "loads toy-02 with three shifts and weekend metadata" {
        val path = locateInstance("toy-02.json")
        val inst = InstanceIo.load(path)
        inst.shifts.map { it.id }.toSet() shouldBe setOf("M", "D", "N")
        inst.horizonDays shouldBe 14
        // Night shift detection works on label or overnight.
        inst.isNightShift("N") shouldBe true
        inst.isNightShift("M") shouldBe false
    }

    "round-trips an Instance through JSON" {
        val path = locateInstance("toy-01.json")
        val original = InstanceIo.load(path)
        val jsonText = InstanceIo.toJson(original)
        val reloaded = InstanceIo.fromJson(jsonText)
        reloaded.horizonDays shouldBe original.horizonDays
        reloaded.shifts.size shouldBe original.shifts.size
        reloaded.nurses.size shouldBe original.nurses.size
        reloaded.coverage.size shouldBe original.coverage.size
        reloaded.forbiddenTransitions shouldBe original.forbiddenTransitions
        jsonText shouldContain "\"id\""
    }
})

/** Walk up from CWD to find `data/nsp/<file>` — works from any sub-module. */
internal fun locateInstance(filename: String): Path {
    var dir: Path? = Path.of("").toAbsolutePath()
    while (dir != null) {
        val candidate = dir.resolve("data/nsp/$filename")
        if (candidate.exists()) return candidate
        dir = dir.parent
    }
    error("Could not locate data/nsp/$filename above CWD")
}
