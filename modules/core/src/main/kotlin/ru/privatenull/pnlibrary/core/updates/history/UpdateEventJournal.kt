package ru.privatenull.pnlibrary.core.updates.history

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import ru.privatenull.pnlibrary.api.updates.ProductId
import ru.privatenull.pnlibrary.api.updates.UpdateActor
import ru.privatenull.pnlibrary.api.updates.UpdatePlanSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateState
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.common.io.AtomicFiles
import ru.privatenull.pnlibrary.core.io.TextFiles
import ru.privatenull.pnlibrary.core.updates.install.ComponentLedger
import ru.privatenull.pnlibrary.core.updates.service.UpdateHealthReport
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.streams.toList

/**
 * Durable observations, separate from mutable resolver snapshots and release publication dates.
 *
 * The journal is bounded: events older than [maxAge] (by file modification time) and everything beyond the
 * newest [maxEvents] are deleted. An observation identical to the one before it is not written again, so
 * repeated automatic checks of an unchanged state do not fill the history.
 */
internal class UpdateEventJournal(
    private val directory: Path,
    private val clock: Clock = Clock.systemUTC(),
    private val warning: (String) -> Unit = {},
    private val maxAge: Duration = Duration.ofDays(DEFAULT_MAX_AGE_DAYS),
    private val maxEvents: Int = DEFAULT_MAX_EVENTS,
) {
    data class Change(val product: String, val from: String?, val to: String)

    data class Event(
        val id: UUID,
        val occurredAt: Instant,
        val planId: UUID,
        val revision: Long,
        val state: String,
        val changes: List<Change>,
        val actor: UpdateActor = UpdateActor.UNKNOWN,
        val operation: String = "STATE_OBSERVED",
        val message: String? = null,
    ) {
        /** A scheduled check that found nothing: kept for auditing, but left out of the history people read. */
        val routine: Boolean
            get() = operation == "CHECK" && changes.isEmpty() && message == null && state == UpdateState.UP_TO_DATE.name
    }

    private var latest: Event? = null
    private var latestLoaded = false
    private var writesSincePrune = 0

    init {
        require(maxEvents > 0) { "maxEvents must be positive" }
        Files.createDirectories(directory)
        prune()
    }

    /** Records an observed state, not an assertion about who initiated the operation. */
    @Synchronized
    fun record(snapshot: UpdatePlanSnapshot, actor: UpdateActor = UpdateActor.UNKNOWN, operation: String = "STATE_OBSERVED"): Event {
        require(operation in OPERATIONS) { "Invalid update operation" }
        val event = Event(
            UUID.randomUUID(), clock.instant(), snapshot.id, snapshot.revision, snapshot.state.name,
            snapshot.plan?.changes.orEmpty().map { Change(it.product.value, it.from?.toString(), it.to.toString()) },
            actor, operation, snapshot.message?.take(4096),
        )
        val previous = latestEvent()
        if (previous != null && signature(previous) == signature(event)) return previous
        return write(event)
    }

    /**
     * Records a change of a managed component that happened outside an update plan: it appeared, disappeared,
     * changed version without the updater or had its channel or automatic download changed.
     */
    @Synchronized
    fun recordComponent(
        operation: String,
        product: String,
        from: String?,
        to: String,
        message: String?,
        actor: UpdateActor = UpdateActor.SYSTEM,
    ): Event {
        require(operation in COMPONENT_OPERATIONS) { "Invalid component operation" }
        return write(Event(
            UUID.randomUUID(), clock.instant(), UUID.randomUUID(), 0, UpdateState.UP_TO_DATE.name,
            listOf(Change(product, from, to)), actor, operation, message?.take(4096),
        ))
    }

    /** Writes the actual source transaction's transitions, never a newly resolved release plan. */
    @Synchronized
    fun recordHealth(report: UpdateHealthReport): Event = write(Event(
        UUID.randomUUID(), clock.instant(), report.planId, 0,
        (if (!report.healthy) UpdateState.FAILED else if (report.rollback) UpdateState.ROLLED_BACK else UpdateState.UP_TO_DATE).name,
        report.changes.map { Change(it.product.value, it.from?.toString(), it.to.toString()) },
        UpdateActor.SYSTEM, if (report.rollback) "HEALTH_ROLLBACK" else "HEALTH", report.message.take(4096),
    ))

    private fun signature(event: Event): String =
        "${event.operation}|${event.state}|${event.actor.kind}:${event.actor.id}|${event.message}|" +
            event.changes.joinToString(";") { "${it.product}:${it.from}>${it.to}" }

    private fun latestEvent(): Event? {
        if (!latestLoaded) {
            latest = readPage(1, 1, null).firstOrNull()
            latestLoaded = true
        }
        return latest
    }

    private fun write(event: Event): Event {
        val actor = event.actor
        val json = JsonObject().apply {
            addProperty("schema", 1)
            addProperty("kind", "STATE_OBSERVED")
            addProperty("id", event.id.toString())
            addProperty("occurredAt", event.occurredAt.toString())
            addProperty("planId", event.planId.toString())
            addProperty("revision", event.revision)
            addProperty("state", event.state)
            addProperty("operation", event.operation)
            addProperty("message", event.message)
            add("actor", JsonObject().apply {
                addProperty("kind", actor.kind.name)
                addProperty("id", actor.id)
                addProperty("name", actor.name)
            })
            add("changes", JsonArray().apply {
                event.changes.forEach { change -> add(JsonObject().apply {
                    addProperty("product", change.product)
                    addProperty("from", change.from)
                    addProperty("to", change.to)
                }) }
            })
        }
        val target = directory.resolve("${event.id}.json")
        require(json.toString().toByteArray(UTF_8).size <= 1024 * 1024) { "Event exceeds size limit" }
        val temporary = Files.createTempFile(directory, ".event-", ".tmp")
        try {
            TextFiles.write(temporary, json.toString(), UTF_8)
            AtomicFiles.move(temporary, target, replaceExisting = false)
        } finally {
            Files.deleteIfExists(temporary)
        }
        latest = event
        latestLoaded = true
        if (++writesSincePrune >= PRUNE_EVERY_WRITES) prune()
        return event
    }

    /** Deletes events older than [maxAge] and any beyond the newest [maxEvents]; returns how many were removed. */
    @Synchronized
    fun prune(): Int {
        writesSincePrune = 0
        val cutoff = clock.instant().minus(maxAge).toEpochMilli()
        val files = Files.list(directory).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".json") && Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
                .toList()
        }.map { it to Files.getLastModifiedTime(it).toMillis() }.sortedByDescending { it.second }
        var removed = 0
        files.forEachIndexed { index, (path, modified) ->
            if (index >= maxEvents || modified < cutoff) {
                if (Files.deleteIfExists(path)) removed++
            }
        }
        if (removed > 0) latestLoaded = false
        return removed
    }

    /** Number of readable events, optionally restricted to one managed product; routine checks are not counted. */
    @Synchronized
    fun count(product: String? = null): Int = readAll(product, includeRoutine = false).size

    /**
     * Reads bounded pages ordered by observation time, optionally restricted to one managed product. Routine checks
     * that found nothing are left out, so the pages show what actually happened.
     */
    @Synchronized
    fun read(page: Int = 1, pageSize: Int = 10, product: String? = null): List<Event> {
        return readPage(page, pageSize, product, includeRoutine = false)
    }

    private fun readPage(page: Int, pageSize: Int, product: String?, includeRoutine: Boolean = true): List<Event> {
        require(page > 0) { "Page must be positive" }
        require(pageSize in 1..100) { "Page size must be between 1 and 100" }
        val offset = (page.toLong() - 1) * pageSize
        return readAll(product, includeRoutine).asSequence().drop(offset.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).take(pageSize).toList()
    }

    private fun readAll(product: String?, includeRoutine: Boolean = true): List<Event> = Files.list(directory).use { files ->
        files.filter { it.fileName.toString().endsWith(".json") && Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
            .map { path -> readEvent(path) }
            .filter { it != null }
            .map { requireNotNull(it) }
            .filter { product == null || it.changes.any { change -> change.product.equals(product, true) } }
            .filter { includeRoutine || !it.routine }
            .sorted(compareByDescending<Event> { it.occurredAt }.thenByDescending { it.revision }.thenBy { it.id })
            .toList()
    }

    private fun readEvent(path: Path): Event? = try {
        require(Files.size(path) <= 1024 * 1024) { "Event exceeds size limit" }
        val json = JsonParser.parseString(TextFiles.read(path, UTF_8)).asJsonObject
        require(json.get("schema").asInt == 1 && json.get("kind").asString == "STATE_OBSERVED") { "Unsupported event schema" }
        Event(
            UUID.fromString(json.get("id").asString), Instant.parse(json.get("occurredAt").asString),
            UUID.fromString(json.get("planId").asString), json.get("revision").asLong, json.get("state").asString,
            json.getAsJsonArray("changes").map { element -> element.asJsonObject.let { change ->
                Change(change.get("product").asString, change.get("from")?.takeUnless { it.isJsonNull }?.asString, change.get("to").asString)
            } },
            json.getAsJsonObject("actor")?.let { actor ->
                UpdateActor(UpdateActor.Kind.valueOf(actor.get("kind").asString), actor.get("id").asString, actor.get("name").asString)
            } ?: UpdateActor.UNKNOWN,
            json.get("operation")?.asString ?: "STATE_OBSERVED",
            json.get("message")?.takeUnless { it.isJsonNull }?.asString,
        ).also { event ->
            require(event.revision >= 0) { "Invalid event revision" }
            UpdateState.valueOf(event.state)
            require(event.operation in OPERATIONS) { "Invalid update operation" }
            event.changes.forEach { change ->
                ProductId.of(change.product)
                change.from?.let(SemanticVersion::parse)
                SemanticVersion.parse(change.to)
            }
        }
    } catch (error: Exception) {
        warning("Unreadable update event ${path.fileName}: ${error.javaClass.simpleName}")
        null
    }

    private companion object {
        const val DEFAULT_MAX_AGE_DAYS = 90L
        const val DEFAULT_MAX_EVENTS = 1_000
        const val PRUNE_EVERY_WRITES = 25
        val COMPONENT_OPERATIONS = setOf(
            ComponentLedger.OPERATION_ADDED, ComponentLedger.OPERATION_REMOVED,
            ComponentLedger.OPERATION_VERSION_CHANGED, ComponentLedger.OPERATION_SETTINGS_CHANGED,
        )
        val OPERATIONS = setOf("STATE_OBSERVED", "CHECK", "STAGE", "ROLLBACK", "HEALTH", "HEALTH_ROLLBACK") + COMPONENT_OPERATIONS
    }
}
