package io.vanja.nspapi

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Error envelopes:
 *
 *  - Syntactically bad JSON on /instances or /solve → 400 with RFC 7807-ish
 *    body `{code, message, ...}`.
 *  - Semantically invalid instance (e.g. coverage points at a non-existent
 *    shift) → 422 with `code = "validation_failed"` and a details object
 *    listing the errors.
 */
class ErrorsSpec : StringSpec({

    "POST /solve with malformed JSON → 400" {
        val wiring = testWiring()
        testApplication {
            application { wiredModule(wiring) }
            val resp = client.post("/solve") {
                contentType(ContentType.Application.Json)
                setBody("{not valid json")
            }
            resp.status shouldBe HttpStatusCode.BadRequest
            val body = resp.bodyAsText()
            body shouldContain "\"code\""
        }
    }

    "POST /instances with semantically invalid instance → 422" {
        val wiring = testWiring()
        testApplication {
            application { wiredModule(wiring) }
            val toy = Files.readString(locateRepoFile("data/nsp/toy-01.json"))
            // Swap out shift id "D" for "XXX" in coverage — the parse succeeds,
            // but Validation should reject the reference to a missing shift.
            val broken = toy.replace("\"shiftId\": \"D\"", "\"shiftId\": \"XXX\"")
            val resp = client.post("/instances") {
                contentType(ContentType.Application.Json)
                setBody(broken)
            }
            resp.status shouldBe HttpStatusCode.UnprocessableEntity
            val body = resp.bodyAsText()
            val obj = Json.parseToJsonElement(body).jsonObject
            obj["code"]?.toString()?.trim('"') shouldBe "validation_failed"
        }
    }
})
