package io.vanja.nspapi

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Files

/**
 * The /openapi.yaml endpoint must return the exact bytes of
 * apps/shared/openapi.yaml — the Python and Kotlin backends both serve this
 * same document verbatim.
 */
class OpenApiRouteSpec : StringSpec({

    "GET /openapi.yaml matches apps/shared/openapi.yaml byte-for-byte" {
        val wiring = testWiring()
        testApplication {
            application { wiredModule(wiring) }
            val resp = client.get("/openapi.yaml")
            val bytes = resp.bodyAsBytes()
            resp.contentType()?.withoutParameters() shouldBe ContentType.parse("application/yaml")

            val expected = Files.readAllBytes(locateRepoFile("apps/shared/openapi.yaml"))
            bytes.contentEquals(expected) shouldBe true
        }
    }
})
