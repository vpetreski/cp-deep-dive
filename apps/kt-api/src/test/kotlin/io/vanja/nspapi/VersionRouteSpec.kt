package io.vanja.nspapi

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.server.testing.testApplication

class VersionRouteSpec : StringSpec({

    "GET /version reports service, runtime, and ortools" {
        val wiring = testWiring()
        testApplication {
            application { wiredModule(wiring) }
            val body: VersionResponse = jsonClient().get("/version").body()
            body.service shouldBe "kt-api"
            body.version shouldBe "1.0.0"
            body.runtime shouldStartWith "JVM "
            body.ortools shouldContain "."
        }
    }
})
