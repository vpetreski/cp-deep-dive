package io.vanja.nspapi

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire-level DTOs that mirror the OpenAPI schemas in
 * `apps/shared/openapi.yaml`. The domain model lives in `nsp-core`; here we
 * keep only the shapes that appear on the wire but are not already serializable
 * types from nsp-core.
 */

@Serializable
public data class HealthResponse(
    val status: String,
    val service: String,
    val checks: Map<String, JsonElement> = emptyMap(),
)

@Serializable
public data class VersionResponse(
    val version: String,
    val ortools: String,
    val runtime: String,
    val service: String,
)

/** POST /instances, GET /instances/{id} response body. */
@Serializable
public data class InstanceSummary(
    val id: String,
    val name: String? = null,
    val source: String? = null,
    val horizonDays: Int,
    val nurseCount: Int,
    val shiftCount: Int,
    val coverageSlotCount: Int,
    val createdAt: String,
)

@Serializable
public data class InstanceListResponse(
    val items: List<InstanceSummary>,
    val nextCursor: String? = null,
)

/** POST /solve request body. Contains an inline instance or references an existing one. */
@Serializable
public data class SolveRequest(
    val instance: JsonElement? = null,
    val instanceId: String? = null,
    val params: SolverParamsDto? = null,
)

@Serializable
public data class SolverParamsDto(
    val maxTimeSeconds: Double? = null,
    val timeLimitSeconds: Double? = null,
    val numSearchWorkers: Int? = null,
    val numWorkers: Int? = null,
    val randomSeed: Int? = null,
    val linearizationLevel: Int? = null,
    val relativeGapLimit: Double? = null,
    val logSearchProgress: Boolean? = null,
    val enableHints: Boolean? = null,
    val objectiveWeights: ObjectiveWeightsDto? = null,
)

/**
 * Flexible weights DTO — accepts either the named-soft-constraint style
 * (preference/fairness/...) or SCn style (SC1..SC5) from the OpenAPI.
 */
@Serializable
public data class ObjectiveWeightsDto(
    val preference: Int? = null,
    val fairness: Int? = null,
    val workloadBalance: Int? = null,
    val weekendDistribution: Int? = null,
    val consecutiveDaysOff: Int? = null,
    val SC1: Int? = null,
    val SC2: Int? = null,
    val SC3: Int? = null,
    val SC4: Int? = null,
    val SC5: Int? = null,
)

@Serializable
public data class SolveAccepted(
    val jobId: String,
    val status: String,
    val instanceId: String? = null,
    val createdAt: String,
)

@Serializable
public data class AssignmentDto(
    val nurseId: String,
    val day: Int,
    val shiftId: String? = null,
)

@Serializable
public data class ScheduleDto(
    val instanceId: String,
    val jobId: String? = null,
    val generatedAt: String? = null,
    val assignments: List<AssignmentDto>,
    val violations: List<ViolationDto> = emptyList(),
)

@Serializable
public data class ViolationDto(
    val code: String,
    val message: String,
    val severity: String? = null,
    val nurseId: String? = null,
    val day: Int? = null,
    val penalty: Double? = null,
)

/** The canonical SolveResponse used by both GET /solution/{id} and the SSE stream. */
@Serializable
public data class SolveResponse(
    val jobId: String,
    val status: String,
    val instanceId: String? = null,
    val schedule: ScheduleDto? = null,
    val violations: List<ViolationDto>? = null,
    val objective: Double? = null,
    val bestBound: Double? = null,
    val gap: Double? = null,
    val solveTimeSeconds: Double? = null,
    val createdAt: String? = null,
    val startedAt: String? = null,
    val endedAt: String? = null,
    val error: String? = null,
)

/** Status strings used on the wire (SolveResponse.status enum). */
public object JobStatus {
    public const val PENDING: String = "pending"
    public const val QUEUED: String = "queued"
    public const val RUNNING: String = "running"
    public const val FEASIBLE: String = "feasible"
    public const val OPTIMAL: String = "optimal"
    public const val INFEASIBLE: String = "infeasible"
    public const val UNKNOWN: String = "unknown"
    public const val TIMEOUT: String = "timeout"
    public const val CANCELLED: String = "cancelled"
    public const val MODEL_INVALID: String = "modelInvalid"
    public const val ERROR: String = "error"

    public val TERMINAL: Set<String> = setOf(
        FEASIBLE, OPTIMAL, INFEASIBLE, UNKNOWN, TIMEOUT, CANCELLED, MODEL_INVALID, ERROR,
    )
}
