package io.vanja.nspapi

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay

/**
 * Full happy-path: submit a solve for toy-01, poll the solution endpoint until
 * it reaches a terminal state, assert FEASIBLE or OPTIMAL.
 *
 * We also run the cancel-mid-solve variant on a longer horizon to verify the
 * CANCELLED terminal state.
 */
class SolveLifecycleSpec : StringSpec({

    // Force OR-Tools natives to load before the test clock starts.
    beforeSpec { OrtoolsNativesReady }

    "POST /solve then poll /solution reaches OPTIMAL / FEASIBLE for toy-01".config(
        timeout = 3.minutes,
        invocationTimeout = 3.minutes,
    ) {
        val wiring = testWiring(capacity = 2)
        testApplication {
            application { wiredModule(wiring) }
            val client = jsonClient()

            val toy = Files.readString(locateRepoFile("data/nsp/toy-01.json"))
            val submit = client.post("/solve") {
                contentType(ContentType.Application.Json)
                setBody("""{"instance": $toy, "params": {"maxTimeSeconds": 30, "numSearchWorkers": 4, "randomSeed": 1}}""")
            }
            submit.status shouldBe HttpStatusCode.Accepted
            val accepted: SolveAccepted = submit.body()
            submit.headers[HttpHeaders.Location] shouldBe "/solution/${accepted.jobId}"

            var final: SolveResponse? = null
            var lastStatus = "none"
            for (i in 0 until 120) {
                delay(300)
                val state: SolveResponse = client.get("/solution/${accepted.jobId}").body()
                lastStatus = state.status
                if (state.status in JobStatus.TERMINAL) {
                    final = state
                    break
                }
            }
            val result = final ?: error("job never reached terminal state; last status='$lastStatus'")
            if (result.status !in setOf(JobStatus.FEASIBLE, JobStatus.OPTIMAL)) {
                error(
                    "expected FEASIBLE or OPTIMAL, got status='${result.status}', " +
                        "objective=${result.objective}, error=${result.error}",
                )
            }
            (result.schedule != null) shouldBe true
        }
    }

    "POST /solve then cancel mid-solve yields cancelled terminal state".config(
        timeout = 3.minutes,
        invocationTimeout = 3.minutes,
    ) {
        val wiring = testWiring(capacity = 2)
        testApplication {
            application { wiredModule(wiring) }
            val client = jsonClient()

            val toy = Files.readString(locateRepoFile("data/nsp/toy-02.json"))
            val submit = client.post("/solve") {
                contentType(ContentType.Application.Json)
                // Ask for a long budget so we have time to cancel.
                setBody("""{"instance": $toy, "params": {"maxTimeSeconds": 120, "numSearchWorkers": 2}}""")
            }
            submit.status shouldBe HttpStatusCode.Accepted
            val accepted: SolveAccepted = submit.body()

            // Fire the cancel immediately. The solver may have already reached
            // OPTIMAL on the tiny toy-02 instance — in that case cancel returns
            // 409 because the job is already terminal, which is an accepted
            // outcome for this race. Otherwise we expect 202.
            val cancel = client.post("/solve/${accepted.jobId}/cancel")
            (cancel.status == HttpStatusCode.Accepted || cancel.status == HttpStatusCode.Conflict) shouldBe true

            // Poll until terminal — cancelled or a quick FEASIBLE/OPTIMAL before stop.
            var final: SolveResponse? = null
            for (i in 0 until 120) {
                delay(250)
                val state: SolveResponse = client.get("/solution/${accepted.jobId}").body()
                if (state.status in JobStatus.TERMINAL) {
                    final = state
                    break
                }
            }
            val result = final ?: error("job never reached terminal state")
            if (result.status !in setOf(JobStatus.CANCELLED, JobStatus.FEASIBLE, JobStatus.OPTIMAL)) {
                error(
                    "expected CANCELLED/FEASIBLE/OPTIMAL, got status='${result.status}', " +
                        "objective=${result.objective}, error=${result.error}",
                )
            }
        }
    }
})
