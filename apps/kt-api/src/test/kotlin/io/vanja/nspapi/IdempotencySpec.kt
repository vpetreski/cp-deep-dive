package io.vanja.nspapi

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Files

/**
 * Idempotency-Key behavior on POST /solve:
 *
 *  - same key + same body → same jobId (second POST returns the first's id, 202)
 *  - same key + different body → 409 Conflict
 */
class IdempotencySpec : StringSpec({

    "same Idempotency-Key and body returns the same jobId" {
        val wiring = testWiring(capacity = 2)
        testApplication {
            application { wiredModule(wiring) }
            val client = jsonClient()

            val toy = Files.readString(locateRepoFile("data/nsp/toy-01.json"))
            val body =
                """{"instance": $toy, "params": {"maxTimeSeconds": 120, "numSearchWorkers": 2, "randomSeed": 1}}"""

            val key = "idem-same-${System.nanoTime()}"

            val r1 = client.post("/solve") {
                contentType(ContentType.Application.Json)
                header("Idempotency-Key", key)
                setBody(body)
            }
            r1.status shouldBe HttpStatusCode.Accepted
            val a1: SolveAccepted = r1.body()

            val r2 = client.post("/solve") {
                contentType(ContentType.Application.Json)
                header("Idempotency-Key", key)
                setBody(body)
            }
            r2.status shouldBe HttpStatusCode.Accepted
            val a2: SolveAccepted = r2.body()

            a2.jobId shouldBe a1.jobId
        }
    }

    "same Idempotency-Key with a different body returns 409" {
        val wiring = testWiring(capacity = 2)
        testApplication {
            application { wiredModule(wiring) }
            val client = jsonClient()

            val toy01 = Files.readString(locateRepoFile("data/nsp/toy-01.json"))
            val toy02 = Files.readString(locateRepoFile("data/nsp/toy-02.json"))
            val key = "idem-conflict-${System.nanoTime()}"

            val r1 = client.post("/solve") {
                contentType(ContentType.Application.Json)
                header("Idempotency-Key", key)
                setBody("""{"instance": $toy01, "params": {"maxTimeSeconds": 120}}""")
            }
            r1.status shouldBe HttpStatusCode.Accepted

            val r2 = client.post("/solve") {
                contentType(ContentType.Application.Json)
                header("Idempotency-Key", key)
                setBody("""{"instance": $toy02, "params": {"maxTimeSeconds": 120}}""")
            }
            r2.status shouldBe HttpStatusCode.Conflict
        }
    }
})
