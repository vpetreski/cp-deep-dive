package io.vanja.nspapi

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

/**
 * Ktor 3 backend skeleton for the NSP explorer. For now it just exposes
 * two housekeeping endpoints:
 *
 *   GET /health  → `{"status":"ok"}`
 *   GET /version → `{"name":"kt-api","version":"0.1.0","ortools":"9.15.6755"}`
 *
 * Future work: mount NSP-specific endpoints that accept an instance JSON,
 * solve it with `cpsat-kt`, and stream solutions back.
 */

@Serializable
public data class HealthResponse(val status: String)

@Serializable
public data class VersionResponse(
    val name: String,
    val version: String,
    val ortools: String,
)

public fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    install(CallLogging)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.message ?: "unknown error")),
            )
        }
    }
    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok"))
        }
        get("/version") {
            call.respond(
                VersionResponse(
                    name = "kt-api",
                    version = "0.1.0",
                    ortools = "9.15.6755",
                ),
            )
        }
    }
}

public fun main() {
    // Load config from application.conf (see src/main/resources/). Falls back
    // to port 8080 / all-interfaces if the file is missing.
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}
