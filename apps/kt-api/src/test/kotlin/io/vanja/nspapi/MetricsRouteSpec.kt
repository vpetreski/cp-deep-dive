package io.vanja.nspapi

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class MetricsRouteSpec : StringSpec({

    "GET /metrics returns Prometheus text exposition" {
        val wiring = testWiring()
        testApplication {
            application { wiredModule(wiring) }
            val resp = client.get("/metrics")
            resp.status shouldBe HttpStatusCode.OK
            val body = resp.bodyAsText()
            // Common Micrometer JVM / process metrics
            body shouldContain "jvm_"
        }
    }
})
