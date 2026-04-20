package io.vanja.nspapi

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.vanja.cpsat.nsp.Instance
import java.security.MessageDigest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * HTTP routes. Each `Route.xxxRoutes(...)` extension mounts one tag's
 * endpoints; `Application.configureRouting` composes them.
 */
public fun Route.metaRoutes(
    service: String,
    version: String,
    ortoolsVersion: String,
    prometheus: PrometheusMeterRegistry,
    openApiDocument: ByteArray,
) {
    get("/health") {
        call.respond(
            HealthResponse(
                status = "ok",
                service = service,
                checks = mapOf(
                    "database" to kotlinx.serialization.json.JsonPrimitive("ok"),
                    "solver" to kotlinx.serialization.json.JsonPrimitive("ok"),
                ),
            ),
        )
    }
    get("/version") {
        call.respond(
            VersionResponse(
                version = version,
                ortools = ortoolsVersion,
                runtime = "JVM ${System.getProperty("java.version")}",
                service = service,
            ),
        )
    }
    get("/openapi.yaml") {
        call.respondBytes(openApiDocument, ContentType.parse("application/yaml"))
    }
    get("/metrics") {
        call.respondText(prometheus.scrape(), ContentType.parse("text/plain; version=0.0.4; charset=utf-8"))
    }
}

public fun Route.instanceRoutes(repo: InstanceRepository) {
    route("/instances") {
        post {
            val payload = receiveJsonOrBadRequest(call) ?: return@post
            val instance = try {
                parseAndValidateInstance(payload)
            } catch (e: IllegalArgumentException) {
                call.respondError(HttpStatusCode.BadRequest, ErrorCodes.BAD_REQUEST, e.message ?: "bad request")
                return@post
            } catch (e: InstanceValidationException) {
                call.respondError(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorCodes.VALIDATION_FAILED,
                    "instance failed semantic validation",
                    details = buildJsonObject {
                        put("errors", JsonArray(e.errors.map { JsonPrimitive(it) }))
                    },
                )
                return@post
            }
            val summary = repo.save(instance)
            call.response.header(HttpHeaders.Location, "/instances/${instance.id}")
            call.respond(HttpStatusCode.Created, summary)
        }
        get {
            val limit = call.parameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            val cursor = call.parameters["cursor"]
            val page = repo.list(limit, cursor)
            call.respond(page)
        }
        route("/{id}") {
            get {
                val id = call.parameters["id"]!!
                val stored = repo.get(id)
                if (stored == null) {
                    call.respondError(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "no instance with id='$id'")
                    return@get
                }
                // Respond with the full wire JSON as an object (not wrapped).
                call.respondText(stored.json, ContentType.Application.Json, HttpStatusCode.OK)
            }
            delete {
                val id = call.parameters["id"]!!
                if (!repo.exists(id)) {
                    call.respondError(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "no instance with id='$id'")
                    return@delete
                }
                repo.delete(id)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

public fun Route.solveRoutes(
    repo: InstanceRepository,
    registry: SolveJobRegistry,
    idempotency: IdempotencyStore,
) {
    post("/solve") {
        val raw = try {
            call.receiveText()
        } catch (e: Throwable) {
            call.respondError(HttpStatusCode.BadRequest, ErrorCodes.BAD_REQUEST, "could not read body: ${e.message}")
            return@post
        }
        if (raw.isBlank()) {
            call.respondError(HttpStatusCode.BadRequest, ErrorCodes.BAD_REQUEST, "empty body")
            return@post
        }
        val request = try {
            Json { ignoreUnknownKeys = true }.decodeFromString<SolveRequest>(raw)
        } catch (e: SerializationException) {
            call.respondError(HttpStatusCode.BadRequest, ErrorCodes.BAD_REQUEST, "malformed SolveRequest: ${e.message}")
            return@post
        } catch (e: IllegalArgumentException) {
            call.respondError(HttpStatusCode.BadRequest, ErrorCodes.BAD_REQUEST, "malformed SolveRequest: ${e.message}")
            return@post
        }

        val instance: Instance = when {
            request.instance != null -> try {
                parseAndValidateInstance(request.instance)
            } catch (e: IllegalArgumentException) {
                call.respondError(HttpStatusCode.BadRequest, ErrorCodes.BAD_REQUEST, e.message ?: "bad instance")
                return@post
            } catch (e: InstanceValidationException) {
                call.respondError(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorCodes.VALIDATION_FAILED,
                    "instance failed semantic validation",
                    details = buildJsonObject {
                        put("errors", JsonArray(e.errors.map { JsonPrimitive(it) }))
                    },
                )
                return@post
            }
            request.instanceId != null -> {
                val stored = repo.get(request.instanceId)
                if (stored == null) {
                    call.respondError(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "no instance with id='${request.instanceId}'")
                    return@post
                }
                stored.instance
            }
            else -> {
                call.respondError(HttpStatusCode.BadRequest, ErrorCodes.BAD_REQUEST, "SolveRequest must include either 'instance' or 'instanceId'")
                return@post
            }
        }

        val spec = request.params.toSolverSpec()

        val idempotencyKey = call.request.headers["Idempotency-Key"]
        if (idempotencyKey != null) {
            val hash = sha256(raw)
            val newJobId = "job_" + java.util.UUID.randomUUID().toString().replace("-", "").take(20)
            val existing = try {
                idempotency.lookupOrReserve(idempotencyKey, hash, newJobId)
            } catch (e: IdempotencyConflictException) {
                call.respondError(
                    HttpStatusCode.Conflict,
                    ErrorCodes.CONFLICT,
                    "Idempotency-Key '${e.key}' already used with a different body",
                )
                return@post
            }
            if (existing != null) {
                // Return 202 with the existing jobId.
                val state = registry.get(existing)
                call.response.header(HttpHeaders.Location, "/solution/$existing")
                call.respond(
                    HttpStatusCode.Accepted,
                    SolveAccepted(
                        jobId = existing,
                        status = state?.status ?: JobStatus.PENDING,
                        instanceId = instance.id,
                        createdAt = (state?.createdAt ?: java.time.Instant.now()).toString(),
                    ),
                )
                return@post
            }
            // Reserved a new jobId — submit with that override.
            val state = try {
                registry.submit(instance, spec, jobIdOverride = newJobId)
            } catch (e: SolverPoolFullException) {
                call.respondError(HttpStatusCode.ServiceUnavailable, ErrorCodes.SOLVER_POOL_FULL, e.message ?: "pool full")
                return@post
            }
            call.response.header(HttpHeaders.Location, "/solution/${state.id}")
            call.respond(
                HttpStatusCode.Accepted,
                SolveAccepted(
                    jobId = state.id,
                    status = state.status,
                    instanceId = state.instanceId,
                    createdAt = state.createdAt.toString(),
                ),
            )
            return@post
        }

        val state = try {
            registry.submit(instance, spec)
        } catch (e: SolverPoolFullException) {
            call.respondError(HttpStatusCode.ServiceUnavailable, ErrorCodes.SOLVER_POOL_FULL, e.message ?: "pool full")
            return@post
        }
        call.response.header(HttpHeaders.Location, "/solution/${state.id}")
        call.respond(
            HttpStatusCode.Accepted,
            SolveAccepted(
                jobId = state.id,
                status = state.status,
                instanceId = state.instanceId,
                createdAt = state.createdAt.toString(),
            ),
        )
    }

    get("/solution/{jobId}") {
        val jobId = call.parameters["jobId"]!!
        val state = registry.get(jobId)
        if (state == null) {
            call.respondError(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "no job with id='$jobId'")
            return@get
        }
        call.respond(registry.snapshot(state))
    }

    post("/solve/{jobId}/cancel") {
        val jobId = call.parameters["jobId"]!!
        val state = try {
            registry.cancel(jobId)
        } catch (e: JobAlreadyTerminalException) {
            call.respondError(HttpStatusCode.Conflict, ErrorCodes.TERMINAL_STATE, e.message ?: "terminal")
            return@post
        }
        if (state == null) {
            call.respondError(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "no job with id='$jobId'")
            return@post
        }
        call.respond(HttpStatusCode.Accepted, registry.snapshot(state))
    }

    get("/solve/{jobId}/log") {
        val jobId = call.parameters["jobId"]!!
        val state = registry.get(jobId)
        if (state == null) {
            call.respondError(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "no job with id='$jobId'")
            return@get
        }
        call.respondText(state.logBuffer.toString(), ContentType.Text.Plain, HttpStatusCode.OK)
    }

    sse("/solutions/{jobId}/stream") {
        val jobId = call.parameters["jobId"]!!
        val flow = registry.subscribe(jobId)
        if (flow == null) {
            call.respondError(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "no job with id='$jobId'")
            return@sse
        }
        val encoder = Json { encodeDefaults = true }
        var emitted = 0
        flow.collect { event ->
            send(
                ServerSentEvent(
                    data = encoder.encodeToString(SolveResponse.serializer(), event),
                    event = "solution",
                ),
            )
            emitted++
            if (emitted >= MAX_SSE_EVENTS) return@collect
        }
    }
}

/** Max events a single SSE subscription will emit before closing (see spec). */
private const val MAX_SSE_EVENTS = 10_000

private suspend fun receiveJsonOrBadRequest(call: ApplicationCall): JsonElement? {
    return try {
        val text = call.receiveText()
        if (text.isBlank()) {
            call.respondError(HttpStatusCode.BadRequest, ErrorCodes.BAD_REQUEST, "empty body")
            return null
        }
        Json { ignoreUnknownKeys = true }.parseToJsonElement(text)
    } catch (e: SerializationException) {
        call.respondError(HttpStatusCode.BadRequest, ErrorCodes.BAD_REQUEST, "malformed JSON: ${e.message}")
        null
    } catch (e: BadRequestException) {
        call.respondError(HttpStatusCode.BadRequest, ErrorCodes.BAD_REQUEST, e.message ?: "bad request")
        null
    }
}

private fun sha256(s: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(s.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

