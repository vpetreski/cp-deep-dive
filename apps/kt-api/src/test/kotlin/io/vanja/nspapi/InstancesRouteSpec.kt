package io.vanja.nspapi

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Files

class InstancesRouteSpec : StringSpec({

    "POST /instances → 201 with Location, then GET /instances/{id} returns it" {
        val wiring = testWiring()
        testApplication {
            application { wiredModule(wiring) }
            val client = jsonClient()

            val toy = Files.readString(locateRepoFile("data/nsp/toy-01.json"))
            val postResp = client.post("/instances") {
                contentType(ContentType.Application.Json)
                setBody(toy)
            }
            postResp.status shouldBe HttpStatusCode.Created
            postResp.headers[HttpHeaders.Location] shouldBe "/instances/toy-01"

            val summary: InstanceSummary = postResp.body()
            summary.id shouldBe "toy-01"
            summary.horizonDays shouldBe 7

            val getResp = client.get("/instances/toy-01")
            getResp.status shouldBe HttpStatusCode.OK
            val body = getResp.bodyAsText()
            (body.contains("\"toy-01\"")) shouldBe true
        }
    }

    "GET /instances paginates" {
        val wiring = testWiring()
        testApplication {
            application { wiredModule(wiring) }
            val client = jsonClient()

            val toy01 = Files.readString(locateRepoFile("data/nsp/toy-01.json"))
            val toy02 = Files.readString(locateRepoFile("data/nsp/toy-02.json"))
            client.post("/instances") {
                contentType(ContentType.Application.Json); setBody(toy01)
            }
            client.post("/instances") {
                contentType(ContentType.Application.Json); setBody(toy02)
            }

            val page1: InstanceListResponse = client.get("/instances?limit=1").body()
            page1.items.size shouldBe 1
            page1.nextCursor shouldNotBe null

            val page2: InstanceListResponse = client.get("/instances?limit=1&cursor=${page1.nextCursor}").body()
            page2.items.size shouldBe 1
            page2.items.first().id shouldNotBe page1.items.first().id
        }
    }

    "GET /instances/{id} returns 404 for missing" {
        val wiring = testWiring()
        testApplication {
            application { wiredModule(wiring) }
            val resp = client.get("/instances/does-not-exist")
            resp.status shouldBe HttpStatusCode.NotFound
            val body = resp.bodyAsText()
            (body.contains("\"code\"")) shouldBe true
            (body.contains("not_found")) shouldBe true
        }
    }

    "DELETE /instances/{id} removes and returns 204" {
        val wiring = testWiring()
        testApplication {
            application { wiredModule(wiring) }
            val client = jsonClient()
            val toy = Files.readString(locateRepoFile("data/nsp/toy-01.json"))
            client.post("/instances") {
                contentType(ContentType.Application.Json); setBody(toy)
            }
            val del = client.delete("/instances/toy-01")
            del.status shouldBe HttpStatusCode.NoContent
            client.get("/instances/toy-01").status shouldBe HttpStatusCode.NotFound
        }
    }

    "POST /instances with malformed JSON → 400" {
        val wiring = testWiring()
        testApplication {
            application { wiredModule(wiring) }
            val resp = client.post("/instances") {
                contentType(ContentType.Application.Json)
                setBody("not json at all }}")
            }
            resp.status shouldBe HttpStatusCode.BadRequest
        }
    }
})
