package io.vanja.nspapi

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.vanja.cpsat.nsp.Instance
import io.vanja.cpsat.nsp.InstanceIo
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json

/**
 * Warm up OR-Tools native loading once for the whole JVM so the first solver
 * test doesn't pay the JNI-load penalty (which can take 10+ seconds on slow
 * machines and easily blow past the 60s test timeout).
 */
internal val OrtoolsNativesReady: Boolean = run {
    io.vanja.cpsat.ensureNativesLoaded()
    true
}

/** Small helper: find a file somewhere above CWD. */
internal fun locateRepoFile(relative: String): Path {
    var dir: Path? = Path.of("").toAbsolutePath()
    while (dir != null) {
        val c = dir.resolve(relative)
        if (Files.exists(c)) return c
        dir = dir.parent
    }
    error("could not locate '$relative' above CWD")
}

/** Load a bundled NSP instance JSON. */
internal fun loadToyInstance(filename: String): Instance {
    val path = locateRepoFile("data/nsp/$filename")
    return InstanceIo.fromJson(Files.readString(path))
}

/**
 * Build a full [AppWiring] for a testApplication block. Uses an in-memory
 * SQLite so tests are isolated from each other and from the dev DB.
 */
/**
 * Build a fresh SQLite file for one test. We use a real temp file (deleted on
 * JVM exit) instead of `:memory:` — xerial's in-memory DB is per-connection,
 * and Exposed opens connections lazily, which makes `:memory:` unusable for a
 * schema that was created by a different connection.
 */
internal fun uniqueTestJdbcUrl(): String {
    val tmp = Files.createTempFile("nsp-api-test-", ".sqlite")
    tmp.toFile().deleteOnExit()
    return "jdbc:sqlite:${tmp.toAbsolutePath()}"
}

internal fun testWiring(
    capacity: Int = 2,
    jdbcUrl: String = uniqueTestJdbcUrl(),
): AppWiring {
    val db = connectDb(jdbcUrl)
    initSchema(db)
    val repo = InstanceRepository(db)
    val registry = SolveJobRegistry(capacity = capacity)
    val idempotency = IdempotencyStore(db)
    val prom = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    JvmMemoryMetrics().bindTo(prom)
    ProcessorMetrics().bindTo(prom)
    val openApi = try {
        Files.readAllBytes(locateRepoFile("apps/shared/openapi.yaml"))
    } catch (_: Throwable) {
        "# stub".toByteArray()
    }
    return AppWiring(
        repository = repo,
        registry = registry,
        idempotency = idempotency,
        prometheus = prom,
        openApiDocument = openApi,
    )
}

/** Build an HttpClient configured for JSON content negotiation. */
internal fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient {
    install(ClientContentNegotiation) {
        json(
            Json {
                encodeDefaults = true
                explicitNulls = false
                ignoreUnknownKeys = true
            },
        )
    }
}

/** Configure the test module from an existing [AppWiring]. */
internal fun Application.wiredModule(wiring: AppWiring) {
    configureModule(wiring)
}
