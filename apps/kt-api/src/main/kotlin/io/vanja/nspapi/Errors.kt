package io.vanja.nspapi

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * RFC 7807-shaped error payload matching the `Error` schema in
 * `apps/shared/openapi.yaml`.
 *
 * Every non-2xx JSON response in this service uses this envelope so the Python
 * and Kotlin backends produce identical error bodies.
 */
@Serializable
public data class ApiError(
    val code: String,
    val message: String,
    val details: JsonElement? = null,
)

/** Canonical error codes — must stay in sync with `specs/nsp-app/06-api-contract.md`. */
public object ErrorCodes {
    public const val BAD_REQUEST: String = "bad_request"
    public const val NOT_FOUND: String = "not_found"
    public const val VALIDATION_FAILED: String = "validation_failed"
    public const val CONFLICT: String = "conflict"
    public const val PAYLOAD_TOO_LARGE: String = "payload_too_large"
    public const val SOLVER_POOL_FULL: String = "solver_pool_full"
    public const val INTERNAL_ERROR: String = "internal_error"
    public const val TERMINAL_STATE: String = "terminal_state"
}

/** Thrown by semantic validation on an instance. Mapped to 422. */
public class InstanceValidationException(
    public val errors: List<String>,
) : RuntimeException("instance failed validation: ${errors.joinToString("; ")}")

/** Thrown when a client supplies an Idempotency-Key that was seen with a different body. */
public class IdempotencyConflictException(public val key: String) :
    RuntimeException("idempotency key '$key' already used with a different body")

/** Thrown when all solver slots are occupied. */
public class SolverPoolFullException(public val capacity: Int) :
    RuntimeException("solver pool exhausted (capacity=$capacity)")

/** Thrown when a caller asks to cancel a job that already finished. */
public class JobAlreadyTerminalException(public val jobId: String) :
    RuntimeException("job $jobId is already in a terminal state")

/** Write a [ApiError] payload with the given status. */
public suspend fun ApplicationCall.respondError(
    status: HttpStatusCode,
    code: String,
    message: String,
    details: JsonElement? = null,
) {
    respond(status, ApiError(code = code, message = message, details = details))
}
