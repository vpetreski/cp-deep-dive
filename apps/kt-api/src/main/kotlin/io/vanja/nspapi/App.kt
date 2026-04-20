package io.vanja.nspapi

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.util.AttributeKey
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.vanja.cpsat.nsp.Instance
import io.vanja.cpsat.nsp.InstanceIo
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Ktor 3 module for the NSP backend. Responsible for:
 *
 * 1. Installing plugins (ContentNegotiation, CORS, SSE, StatusPages,
 *    CallLogging, CallId for X-Request-Id, Micrometer metrics).
 * 2. Connecting to SQLite via Exposed and creating the schema.
 * 3. Preloading the toy instances so `/instances` returns something useful
 *    on a fresh DB.
 * 4. Wiring routes from [Routes.kt].
 * 5. Registering a shutdown hook that cancels running jobs.
 *
 * External configuration:
 *
 * - `PORT` (env, via HOCON) — bind port, default 8080.
 * - `NSP_API_DB_URL` (env) — SQLite JDBC URL, default `jdbc:sqlite:./nsp-api.sqlite`.
 * - `NSP_API_SOLVER_CAPACITY` (env) — concurrent solves, default `min(4, CPUs)`.
 */
private val log = LoggerFactory.getLogger("io.vanja.nspapi.App")

public const val API_VERSION: String = "1.0.0"
public const val ORTOOLS_VERSION: String = "9.15.6755"
public const val SERVICE_NAME: String = "kt-api"

private val RegistryKey: AttributeKey<SolveJobRegistry> = AttributeKey("kt-api.SolveJobRegistry")

public data class AppWiring(
    val repository: InstanceRepository,
    val registry: SolveJobRegistry,
    val idempotency: IdempotencyStore,
    val prometheus: PrometheusMeterRegistry,
    val openApiDocument: ByteArray,
)

public fun Application.module() {
    val jdbcUrl = System.getenv("NSP_API_DB_URL") ?: "jdbc:sqlite:./nsp-api.sqlite"
    val capacity = System.getenv("NSP_API_SOLVER_CAPACITY")?.toIntOrNull()
        ?: SolveJobRegistry.DEFAULT_CAPACITY

    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    prometheus.config().commonTags("service", SERVICE_NAME)
    JvmMemoryMetrics().bindTo(prometheus)
    ProcessorMetrics().bindTo(prometheus)

    val db = connectDb(jdbcUrl)
    initSchema(db)

    val repository = InstanceRepository(db)
    val idempotency = IdempotencyStore(db)
    val registry = SolveJobRegistry(capacity = capacity)
    attributes.put(RegistryKey, registry)

    preloadToyInstances(repository)

    val openApi = loadOpenApiDocument()

    val wiring = AppWiring(
        repository = repository,
        registry = registry,
        idempotency = idempotency,
        prometheus = prometheus,
        openApiDocument = openApi,
    )
    configureModule(wiring)

    @Suppress("DEPRECATION")
    environment.monitor.subscribe(ApplicationStopped) {
        log.info("shutdown: cancelling {} jobs", SERVICE_NAME)
        registry.cancelAll()
    }
}

/**
 * Core module configuration, separated from [module] so tests can pass their
 * own [AppWiring] without spinning up a real SQLite DB / meter registry.
 */
public fun Application.configureModule(wiring: AppWiring) {
    install(ContentNegotiation) {
        json(
            Json {
                encodeDefaults = true
                explicitNulls = false
                ignoreUnknownKeys = true
            },
        )
    }
    install(SSE)
    install(CORS) {
        allowHost("localhost:5173", schemes = listOf("http"))
        allowHost("localhost:4173", schemes = listOf("http"))
        allowHost("127.0.0.1:5173", schemes = listOf("http"))
        allowHost("127.0.0.1:4173", schemes = listOf("http"))
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("Idempotency-Key")
        allowHeader("X-Request-Id")
        exposeHeader(HttpHeaders.Location)
        exposeHeader("X-Request-Id")
        allowMethod(io.ktor.http.HttpMethod.Get)
        allowMethod(io.ktor.http.HttpMethod.Post)
        allowMethod(io.ktor.http.HttpMethod.Put)
        allowMethod(io.ktor.http.HttpMethod.Delete)
        allowMethod(io.ktor.http.HttpMethod.Options)
        allowCredentials = true
    }
    install(CallId) {
        header("X-Request-Id")
        generate { UUID.randomUUID().toString() }
        verify { it.isNotBlank() && it.length <= 128 }
        replyToHeader(HttpHeaders.XRequestId)
    }
    install(CallLogging) {
        callIdMdc("requestId")
    }
    install(StatusPages) {
        exception<InstanceValidationException> { call, cause ->
            call.respondError(
                HttpStatusCode.UnprocessableEntity,
                ErrorCodes.VALIDATION_FAILED,
                "instance failed semantic validation",
                details = kotlinx.serialization.json.buildJsonObject {
                    put(
                        "errors",
                        kotlinx.serialization.json.JsonArray(
                            cause.errors.map { kotlinx.serialization.json.JsonPrimitive(it) },
                        ),
                    )
                },
            )
        }
        exception<IdempotencyConflictException> { call, cause ->
            call.respondError(
                HttpStatusCode.Conflict,
                ErrorCodes.CONFLICT,
                cause.message ?: "idempotency conflict",
            )
        }
        exception<SolverPoolFullException> { call, cause ->
            call.respondError(
                HttpStatusCode.ServiceUnavailable,
                ErrorCodes.SOLVER_POOL_FULL,
                cause.message ?: "pool full",
            )
        }
        exception<JobAlreadyTerminalException> { call, cause ->
            call.respondError(
                HttpStatusCode.Conflict,
                ErrorCodes.TERMINAL_STATE,
                cause.message ?: "job already terminal",
            )
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respondError(
                HttpStatusCode.BadRequest,
                ErrorCodes.BAD_REQUEST,
                cause.message ?: "bad request",
            )
        }
        exception<Throwable> { call, cause ->
            log.error("unhandled error", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(code = ErrorCodes.INTERNAL_ERROR, message = cause.message ?: "internal error"),
            )
        }
    }

    routing {
        metaRoutes(
            service = SERVICE_NAME,
            version = API_VERSION,
            ortoolsVersion = ORTOOLS_VERSION,
            prometheus = wiring.prometheus,
            openApiDocument = wiring.openApiDocument,
        )
        instanceRoutes(wiring.repository)
        solveRoutes(wiring.repository, wiring.registry, wiring.idempotency)
    }
}

internal fun loadOpenApiDocument(): ByteArray {
    val classpath = {}::class.java.classLoader.getResourceAsStream("openapi.yaml")
    if (classpath != null) return classpath.use { it.readBytes() }
    // Fallback for `./gradlew run` from non-standard layouts: look relative
    // to the CWD (apps/kt-api/) upwards.
    var dir: Path? = Path.of("").toAbsolutePath()
    while (dir != null) {
        val candidate = dir.resolve("apps/shared/openapi.yaml")
        if (Files.exists(candidate)) return Files.readAllBytes(candidate)
        val sibling = dir.resolve("../shared/openapi.yaml").normalize()
        if (Files.exists(sibling)) return Files.readAllBytes(sibling)
        dir = dir.parent
    }
    log.warn("openapi.yaml not found on classpath or filesystem; /openapi.yaml will serve a stub")
    return "# openapi.yaml missing from classpath\n".toByteArray(Charsets.UTF_8)
}

internal fun preloadToyInstances(repo: InstanceRepository) {
    val candidates = listOf("toy-01.json", "toy-02.json")
    for (filename in candidates) {
        try {
            val path = locateDataFile(filename) ?: continue
            val instance = InstanceIo.fromJson(Files.readString(path))
            if (!repo.exists(instance.id)) {
                repo.save(instance)
                log.info("preloaded instance '{}'", instance.id)
            }
        } catch (t: Throwable) {
            log.warn("failed to preload $filename: ${t.message}")
        }
    }
}

private fun locateDataFile(filename: String): Path? {
    // Classpath resources first
    val resourceUrl = {}::class.java.classLoader.getResource("data/nsp/$filename")
    if (resourceUrl != null) {
        val p = Path.of(resourceUrl.toURI())
        if (Files.exists(p)) return p
    }
    var dir: Path? = Path.of("").toAbsolutePath()
    while (dir != null) {
        val candidate = dir.resolve("data/nsp/$filename")
        if (Files.exists(candidate)) return candidate
        dir = dir.parent
    }
    return null
}

public fun main() {
    embeddedServer(Netty, port = (System.getenv("PORT")?.toIntOrNull() ?: 8080), host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

/** Convenience: fetch the registry from test code without wiring it manually. */
internal fun Application.jobRegistry(): SolveJobRegistry? = attributes.getOrNull(RegistryKey)

/** Make the [Instance] type visible to callers who need to re-parse. */
internal typealias InstanceAlias = Instance
