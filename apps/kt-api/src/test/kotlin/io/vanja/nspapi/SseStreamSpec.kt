package io.vanja.nspapi

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.sse.ClientSSESession
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Subscribe to the SSE stream for a running solve and verify that we receive
 * at least one `event: solution` frame. We don't care whether it's a mid-solve
 * incumbent or the terminal event — only that the stream yields.
 */
class SseStreamSpec : StringSpec({

    beforeSpec { OrtoolsNativesReady }

    "GET /solutions/{jobId}/stream emits at least one `solution` event" {
        val wiring = testWiring(capacity = 2)
        testApplication {
            application { wiredModule(wiring) }
            val jsonClient = jsonClient()
            val sseClient = createClient { install(SSE) }

            val toy = Files.readString(locateRepoFile("data/nsp/toy-01.json"))
            val submit = jsonClient.post("/solve") {
                contentType(ContentType.Application.Json)
                setBody("""{"instance": $toy, "params": {"maxTimeSeconds": 8, "numSearchWorkers": 2, "randomSeed": 1}}""")
            }
            submit.status shouldBe HttpStatusCode.Accepted
            val accepted: SolveAccepted = submit.body()

            val eventName = withTimeoutOrNull(20_000) {
                var name: String? = null
                sseClient.sse("/solutions/${accepted.jobId}/stream") {
                    val first = incoming.first()
                    name = first.event
                }
                name
            }
            eventName shouldBe "solution"
        }
    }
})
