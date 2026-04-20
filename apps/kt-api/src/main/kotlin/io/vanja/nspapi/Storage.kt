package io.vanja.nspapi

import io.vanja.cpsat.nsp.Instance
import io.vanja.cpsat.nsp.InstanceIo
import java.time.Instant
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * SQLite-backed storage for NSP instances and idempotency tokens.
 *
 * Schema is intentionally tiny: instances are persisted as the full wire-JSON
 * blob plus denormalized summary columns for listing, and idempotency tokens
 * are a separate table scoped to POST /solve.
 *
 * Jobs themselves live in memory (see [SolveJobRegistry]) — persisting the
 * full solve history across restarts is out of scope for v1.
 */
public object InstancesTable : Table("instances") {
    public val id: Column<String> = varchar("id", 128)
    public val displayName: Column<String?> = varchar("name", 256).nullable()
    public val sourceKind: Column<String?> = varchar("source_kind", 32).nullable()
    public val horizonDays: Column<Int> = integer("horizon_days")
    public val nurseCount: Column<Int> = integer("nurse_count")
    public val shiftCount: Column<Int> = integer("shift_count")
    public val coverageSlotCount: Column<Int> = integer("coverage_slot_count")
    public val json: Column<String> = text("json")
    public val createdAt: Column<Instant> = timestamp("created_at")
    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

public object IdempotencyTable : Table("idempotency") {
    public val key: Column<String> = varchar("key", 128)
    public val bodyHash: Column<String> = varchar("body_hash", 64)
    public val jobId: Column<String> = varchar("job_id", 64)
    public val createdAt: Column<Instant> = timestamp("created_at")
    override val primaryKey: PrimaryKey = PrimaryKey(key)
}

/** Initialize the schema. Safe to call more than once. */
public fun initSchema(db: Database) {
    transaction(db) {
        SchemaUtils.create(InstancesTable, IdempotencyTable)
    }
}

/**
 * Connect to SQLite at [jdbcUrl]. The default is `jdbc:sqlite:./nsp-api.sqlite`
 * unless overridden by the `NSP_API_DB_URL` environment variable.
 */
public fun connectDb(jdbcUrl: String): Database {
    return Database.connect(jdbcUrl, driver = "org.sqlite.JDBC")
}

/** Stored form of an instance. */
public data class StoredInstance(
    val summary: InstanceSummary,
    val instance: Instance,
    val json: String,
)

public class InstanceRepository(private val db: Database) {

    /** Save an instance; returns its summary. */
    public fun save(instance: Instance, createdAt: Instant = Instant.now()): InstanceSummary {
        val jsonText = InstanceIo.toJson(instance)
        val summary = summaryOf(instance, createdAt)
        transaction(db) {
            InstancesTable.deleteWhere { InstancesTable.id eq instance.id }
            InstancesTable.insert {
                it[id] = instance.id
                it[displayName] = instance.name
                it[sourceKind] = instance.source
                it[horizonDays] = instance.horizonDays
                it[nurseCount] = instance.nurses.size
                it[shiftCount] = instance.shifts.size
                it[coverageSlotCount] = instance.coverage.size
                it[json] = jsonText
                it[InstancesTable.createdAt] = createdAt
            }
        }
        return summary
    }

    public fun get(id: String): StoredInstance? = transaction(db) {
        InstancesTable.selectAll()
            .where { InstancesTable.id eq id }
            .limit(1)
            .map { row ->
                val instance = InstanceIo.fromJson(row[InstancesTable.json])
                StoredInstance(
                    summary = InstanceSummary(
                        id = row[InstancesTable.id],
                        name = row[InstancesTable.displayName],
                        source = row[InstancesTable.sourceKind],
                        horizonDays = row[InstancesTable.horizonDays],
                        nurseCount = row[InstancesTable.nurseCount],
                        shiftCount = row[InstancesTable.shiftCount],
                        coverageSlotCount = row[InstancesTable.coverageSlotCount],
                        createdAt = row[InstancesTable.createdAt].toString(),
                    ),
                    instance = instance,
                    json = row[InstancesTable.json],
                )
            }
            .firstOrNull()
    }

    public fun exists(id: String): Boolean = transaction(db) {
        InstancesTable.selectAll()
            .where { InstancesTable.id eq id }
            .limit(1)
            .any()
    }

    public fun delete(id: String): Boolean = transaction(db) {
        InstancesTable.deleteWhere { InstancesTable.id eq id } > 0
    }

    /**
     * Cursor-based page. `cursor` is the `id` of the last item on the previous
     * page; pages are ordered by `(createdAt ASC, id ASC)` so the cursor is
     * deterministic.
     */
    public fun list(limit: Int, cursor: String?): InstanceListResponse = transaction(db) {
        val cursorCreated: Instant? = cursor?.let { c ->
            InstancesTable.selectAll().where { InstancesTable.id eq c }.firstOrNull()?.get(InstancesTable.createdAt)
        }
        val rows = InstancesTable
            .selectAll()
            .orderBy(InstancesTable.createdAt to SortOrder.ASC, InstancesTable.id to SortOrder.ASC)
            .apply {
                if (cursorCreated != null && cursor != null) {
                    andWhere {
                        (InstancesTable.createdAt greater cursorCreated) or
                            ((InstancesTable.createdAt eq cursorCreated) and (InstancesTable.id greater cursor))
                    }
                }
            }
            .limit(limit + 1)
            .toList()
        val items = rows.take(limit).map { row ->
            InstanceSummary(
                id = row[InstancesTable.id],
                name = row[InstancesTable.displayName],
                source = row[InstancesTable.sourceKind],
                horizonDays = row[InstancesTable.horizonDays],
                nurseCount = row[InstancesTable.nurseCount],
                shiftCount = row[InstancesTable.shiftCount],
                coverageSlotCount = row[InstancesTable.coverageSlotCount],
                createdAt = row[InstancesTable.createdAt].toString(),
            )
        }
        val nextCursor = if (rows.size > limit) items.last().id else null
        InstanceListResponse(items = items, nextCursor = nextCursor)
    }

    public companion object {
        public fun summaryOf(instance: Instance, createdAt: Instant): InstanceSummary = InstanceSummary(
            id = instance.id,
            name = instance.name,
            source = instance.source,
            horizonDays = instance.horizonDays,
            nurseCount = instance.nurses.size,
            shiftCount = instance.shifts.size,
            coverageSlotCount = instance.coverage.size,
            createdAt = createdAt.toString(),
        )
    }
}

/** Extension helper: append a WHERE clause in fluent style. */
private fun <T : org.jetbrains.exposed.sql.Query> T.andWhere(clause: () -> Op<Boolean>): T {
    this.andWhere(clause())
    return this
}

private fun org.jetbrains.exposed.sql.Query.andWhere(op: Op<Boolean>) {
    @Suppress("UNCHECKED_CAST")
    val current = this.where as Op<Boolean>?
    this.adjustWhere {
        if (current == null) op else current and op
    }
}

/**
 * Idempotency-key store: within a 24h window, the same `(key, bodyHash)` must
 * return the same jobId. Mismatched body ⇒ [IdempotencyConflictException].
 */
public class IdempotencyStore(
    private val db: Database,
    private val retentionSeconds: Long = 24L * 60L * 60L,
) {

    /**
     * Return the existing jobId for [key] if the stored [bodyHash] matches.
     * Returns null if the key is unused (caller should create a new job).
     * Throws [IdempotencyConflictException] if the key exists with a different body.
     */
    public fun lookupOrReserve(
        key: String,
        bodyHash: String,
        jobIdIfNew: String,
    ): String? = transaction(db) {
        gc()
        val existing = IdempotencyTable.selectAll()
            .where { IdempotencyTable.key eq key }
            .limit(1)
            .firstOrNull()
        if (existing != null) {
            val storedHash = existing[IdempotencyTable.bodyHash]
            if (storedHash != bodyHash) throw IdempotencyConflictException(key)
            return@transaction existing[IdempotencyTable.jobId]
        }
        IdempotencyTable.insert {
            it[IdempotencyTable.key] = key
            it[IdempotencyTable.bodyHash] = bodyHash
            it[IdempotencyTable.jobId] = jobIdIfNew
            it[IdempotencyTable.createdAt] = Instant.now()
        }
        null
    }

    /** Remove tokens older than the retention window. */
    private fun gc() {
        val cutoff = Instant.now().minusSeconds(retentionSeconds)
        IdempotencyTable.deleteWhere { createdAt less cutoff }
    }
}
