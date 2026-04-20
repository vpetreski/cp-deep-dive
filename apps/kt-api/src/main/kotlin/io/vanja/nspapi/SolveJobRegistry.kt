package io.vanja.nspapi

import io.vanja.cpsat.nsp.Instance
import io.vanja.cpsat.nsp.ObjectiveWeights
import io.vanja.cpsat.nsp.Schedule
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * In-memory registry of solve jobs. Handles:
 *
 * - Concurrency cap via a [Semaphore] — `tryAcquire`-style: if no permit is
 *   available, reject with [SolverPoolFullException] rather than queueing.
 *   (Queueing means unbounded memory pressure; 503 is the simpler story.)
 * - Per-job [MutableSharedFlow] fan-out for SSE subscribers, with a replay
 *   buffer of 1 so late subscribers still see the latest incumbent.
 * - Cancellation via [SolverAdapter.CancelHandle] tied to the running solver.
 *
 * Jobs live in memory only; on restart the registry starts empty. The
 * idempotency layer gives us crash-safety for duplicate submissions but not
 * for mid-run state recovery — a deliberate v1 simplification.
 */
public class SolveJobRegistry(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val solver: SolverAdapter = SolverAdapter(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val semaphore = Semaphore(capacity)
    private val jobs: MutableMap<String, JobState> = ConcurrentHashMap()

    /** One job's full lifecycle state, mutated in place. */
    public class JobState(
        public val id: String,
        public val instanceId: String,
        public val createdAt: Instant,
    ) {
        @Volatile public var startedAt: Instant? = null
        @Volatile public var endedAt: Instant? = null
        @Volatile public var status: String = JobStatus.PENDING
        @Volatile public var schedule: Schedule? = null
        @Volatile public var objective: Long? = null
        @Volatile public var bestBound: Long? = null
        @Volatile public var gap: Double? = null
        @Volatile public var solveTimeSeconds: Double = 0.0
        @Volatile public var error: String? = null
        @Volatile public var logBuffer: StringBuilder = StringBuilder()

        internal val cancelHandleRef: AtomicReference<SolverAdapter.CancelHandle?> = AtomicReference(null)
        internal var coroutineJob: Job? = null

        // Replay=1 so an SSE client that connects a beat late gets the last
        // incumbent; extraBufferCapacity keeps fast producers from blocking.
        internal val events: MutableSharedFlow<SolveResponse> = MutableSharedFlow(
            replay = 1,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    }

    public fun get(jobId: String): JobState? = jobs[jobId]

    /**
     * Submit a new job. Returns the [JobState] (status = PENDING → RUNNING).
     *
     * Throws [SolverPoolFullException] if no permit is available *and* the
     * request isn't an idempotency-replay. The caller is responsible for
     * idempotency lookup before calling this.
     */
    public fun submit(
        instance: Instance,
        spec: SolverAdapter.SolverSpec,
        jobIdOverride: String? = null,
    ): JobState {
        if (!semaphore.tryAcquire()) throw SolverPoolFullException(capacity)
        val jobId = jobIdOverride ?: newJobId()
        val state = JobState(
            id = jobId,
            instanceId = instance.id,
            createdAt = Instant.now(),
        )
        jobs[jobId] = state

        // Seed the shared flow with the initial pending snapshot so replay
        // works for clients that connect before the solver thread starts.
        state.events.tryEmit(snapshot(state))

        state.coroutineJob = scope.launch(start = CoroutineStart.DEFAULT) {
            try {
                state.status = JobStatus.RUNNING
                state.startedAt = Instant.now()
                state.events.tryEmit(snapshot(state))

                val outcome = solver.solveSuspending(
                    instance = instance,
                    spec = spec,
                    cancelHandleRef = state.cancelHandleRef,
                    onIncumbent = { snap ->
                        state.schedule = snap.schedule
                        state.objective = snap.objective
                        state.bestBound = snap.bestBound
                        state.gap = snap.gap
                        state.solveTimeSeconds = snap.solveTimeSeconds
                        // Publish incumbents with status=RUNNING so the stream
                        // can distinguish them from the terminal event.
                        state.events.tryEmit(snapshot(state))
                    },
                    onLog = { line ->
                        state.logBuffer.appendLine(line)
                    },
                )

                state.schedule = outcome.schedule ?: state.schedule
                state.objective = outcome.objective ?: state.objective
                state.bestBound = outcome.bestBound ?: state.bestBound
                state.gap = outcome.gap ?: state.gap
                state.solveTimeSeconds = outcome.solveTimeSeconds
                state.error = outcome.error
                state.status = outcome.status
                if (outcome.searchLog.isNotEmpty()) {
                    // Replace piecewise log with solver's authoritative one
                    state.logBuffer = StringBuilder(outcome.searchLog)
                }
            } catch (c: CancellationException) {
                // External cancel (or outer scope cancel) — mirror the cancel
                // call's bookkeeping but don't flip status to ERROR.
                if (state.status !in JobStatus.TERMINAL) {
                    state.status = JobStatus.CANCELLED
                    state.error = c.message
                }
            } catch (t: Throwable) {
                state.status = JobStatus.ERROR
                state.error = t.message
            } finally {
                state.endedAt = Instant.now()
                state.events.tryEmit(snapshot(state))
                semaphore.release()
            }
        }
        return state
    }

    /**
     * Cancel a running job. Returns the updated [JobState].
     *
     * - Already-terminal → throw [JobAlreadyTerminalException].
     * - Missing → returns null.
     */
    public fun cancel(jobId: String): JobState? {
        val state = jobs[jobId] ?: return null
        if (state.status in JobStatus.TERMINAL) throw JobAlreadyTerminalException(jobId)
        state.cancelHandleRef.get()?.cancel()
        state.coroutineJob?.cancel()
        state.status = JobStatus.CANCELLED
        state.endedAt = state.endedAt ?: Instant.now()
        state.events.tryEmit(snapshot(state))
        return state
    }

    /** SSE / flow subscription — emits the current snapshot and each update. */
    public fun subscribe(jobId: String): Flow<SolveResponse>? {
        val state = jobs[jobId] ?: return null
        val terminalSeen = AtomicReference(false)
        return flow {
            emitAll(
                state.events.asSharedFlow().takeWhile { event ->
                    val isTerminal = event.status in JobStatus.TERMINAL
                    if (isTerminal) {
                        emit(event)
                        terminalSeen.set(true)
                        false
                    } else {
                        true
                    }
                },
            )
            // Belt-and-braces: if the takeWhile stopped at a terminal event
            // but the consumer hasn't received it (takeWhile swallows the
            // stop-predicate element), re-emit once.
            if (!terminalSeen.get() && state.status in JobStatus.TERMINAL) {
                emit(snapshot(state))
            }
        }
    }

    /** Current snapshot for the polling endpoint. */
    public fun snapshot(state: JobState): SolveResponse {
        val schedule = state.schedule?.let { sched ->
            ScheduleDto(
                instanceId = sched.instanceId,
                jobId = state.id,
                generatedAt = sched.generatedAt,
                assignments = sched.assignments.map { a ->
                    AssignmentDto(
                        nurseId = a.nurseId,
                        day = a.day,
                        shiftId = a.shiftId,
                    )
                },
                violations = sched.violations.map { v ->
                    ViolationDto(
                        code = v.code,
                        message = v.message,
                        severity = v.severity.name.lowercase(),
                        nurseId = v.nurseId,
                        day = v.day,
                        penalty = v.penalty,
                    )
                },
            )
        }
        return SolveResponse(
            jobId = state.id,
            instanceId = state.instanceId,
            status = state.status,
            schedule = schedule,
            violations = schedule?.violations,
            objective = state.objective?.toDouble(),
            bestBound = state.bestBound?.toDouble(),
            gap = state.gap,
            solveTimeSeconds = state.solveTimeSeconds,
            createdAt = state.createdAt.toString(),
            startedAt = state.startedAt?.toString(),
            endedAt = state.endedAt?.toString(),
            error = state.error,
        )
    }

    /** Cancel all in-flight jobs. Used at shutdown. */
    public fun cancelAll() {
        for (state in jobs.values) {
            if (state.status !in JobStatus.TERMINAL) {
                state.cancelHandleRef.get()?.cancel()
                state.coroutineJob?.cancel()
                state.status = JobStatus.CANCELLED
                state.endedAt = state.endedAt ?: Instant.now()
            }
        }
    }

    private fun newJobId(): String = "job_" + UUID.randomUUID().toString().replace("-", "").take(20)

    public companion object {
        public val DEFAULT_CAPACITY: Int = minOf(4, Runtime.getRuntime().availableProcessors())
    }
}

/** Translate a [SolverParamsDto] into the canonical adapter spec. */
public fun SolverParamsDto?.toSolverSpec(): SolverAdapter.SolverSpec {
    if (this == null) return SolverAdapter.SolverSpec()
    val weights = objectiveWeights?.let { dto ->
        ObjectiveWeights(
            sc1 = dto.SC1 ?: dto.preference ?: ObjectiveWeights.DEFAULT.sc1,
            sc2 = dto.SC2 ?: dto.fairness ?: ObjectiveWeights.DEFAULT.sc2,
            sc3 = dto.SC3 ?: dto.workloadBalance ?: ObjectiveWeights.DEFAULT.sc3,
            sc4 = dto.SC4 ?: dto.weekendDistribution ?: ObjectiveWeights.DEFAULT.sc4,
            sc5 = dto.SC5 ?: dto.consecutiveDaysOff ?: ObjectiveWeights.DEFAULT.sc5,
        )
    } ?: ObjectiveWeights.DEFAULT

    val timeLimit = maxTimeSeconds ?: timeLimitSeconds ?: 30.0
    val workers = numSearchWorkers ?: numWorkers ?: 8
    return SolverAdapter.SolverSpec(
        maxTimeSeconds = timeLimit,
        numSearchWorkers = workers,
        randomSeed = randomSeed ?: 1,
        linearizationLevel = linearizationLevel,
        relativeGapLimit = relativeGapLimit,
        logSearchProgress = logSearchProgress ?: true,
        weights = weights,
    )
}
