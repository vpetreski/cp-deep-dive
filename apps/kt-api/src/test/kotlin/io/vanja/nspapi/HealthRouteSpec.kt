package io.vanja.nspapi

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

/** GET /health must return the canonical shape with `status`, `service`, and a `checks` object. */
class HealthRouteSpec : StringSpec({

    "GET /health returns ok + service identifier" {
        val wiring = testWiring()
        testApplication {
            application { wiredModule(wiring) }
            val resp = client.get("/health")
            resp.status shouldBe HttpStatusCode.OK
            val body: HealthResponse = jsonClient().get("/health").body()
            body.status shouldBe "ok"
            body.service shouldBe "kt-api"
            body.checks.keys.shouldContainAnyOf("database", "solver")
        }
    }
})

private fun Set<String>.shouldContainAnyOf(vararg keys: String) {
    check(keys.any { it in this }) { "expected any of ${keys.toList()} in $this" }
}
