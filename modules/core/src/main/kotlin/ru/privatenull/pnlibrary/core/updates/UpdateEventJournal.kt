package ru.privatenull.pnlibrary.core.updates

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import ru.privatenull.pnlibrary.api.updates.UpdatePlanSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateActor
import ru.privatenull.pnlibrary.api.updates.UpdateState
import ru.privatenull.pnlibrary.api.updates.ProductId
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.core.io.TextFiles
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.streams.toList

/** Durable observations, separate from mutable resolver snapshots and release publication dates. */
internal class UpdateEventJournal(
    private val directory: Path,
    private val clock: Clock = Clock.systemUTC(),
    private val warning: (String) -> Unit = {},
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
    )

    init {
        Files.createDirectories(directory)
    }

    /** Records an observed state, not an assertion about who initiated the operation. */
    @Synchronized
    fun record(snapshot: UpdatePlanSnapshot, actor: UpdateActor = UpdateActor.UNKNOWN, operation: String = "STATE_OBSERVED"): Event {
        require(operation in OPERATIONS) { "invalid update operation" }
        val event = Event(
            UUID.randomUUID(), clock.instant(), snapshot.id, snapshot.revision, snapshot.state.name,
            snapshot.plan?.changes.orEmpty().map { Change(it.product.value, it.from?.toString(), it.to.toString()) },
            actor, operation, snapshot.message?.take(4096),
        )
        return write(event)
    }

    /** Writes the actual source transaction's transitions, never a newly resolved release plan. */
    @Synchronized
    fun recordHealth(report: UpdateHealthReport): Event = write(Event(
        UUID.randomUUID(), clock.instant(), report.planId, 0,
        (if (!report.healthy) UpdateState.FAILED else if (report.rollback) UpdateState.ROLLED_BACK else UpdateState.UP_TO_DATE).name,
        report.changes.map { Change(it.product.value, it.from?.toString(), it.to.toString()) },
        UpdateActor.SYSTEM, if (report.rollback) "HEALTH_ROLLBACK" else "HEALTH", report.message.take(4096),
    ))

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
        require(json.toString().toByteArray(UTF_8).size <= 1024 * 1024) { "event exceeds size limit" }
        val temporary = Files.createTempFile(directory, ".event-", ".tmp")
        try {
            TextFiles.write(temporary, json.toString(), UTF_8)
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        return event
    }

    /** Reads bounded pages ordered by observation time, optionally restricted to one managed product. */
    @Synchronized
    fun read(page: Int = 1, pageSize: Int = 10, product: String? = null): List<Event> {
        require(page > 0) { "page must be positive" }
        require(pageSize in 1..100) { "page size must be between 1 and 100" }
        val offset = (page.toLong() - 1) * pageSize
        return Files.list(directory).use { files ->
            files.filter { it.fileName.toString().endsWith(".json") && Files.isRegularFile(it, java.nio.file.LinkOption.NOFOLLOW_LINKS) }
                .map { path -> readEvent(path) }
                .filter { it != null }
                .map { requireNotNull(it) }
                .filter { product == null || it.changes.any { change -> change.product.equals(product, true) } }
                .sorted(compareByDescending<Event> { it.occurredAt }.thenByDescending { it.revision }.thenBy { it.id })
                .skip(offset).limit(pageSize.toLong()).toList()
        }
    }

    private fun readEvent(path: Path): Event? = try {
        require(Files.size(path) <= 1024 * 1024) { "event exceeds size limit" }
        val json = JsonParser.parseString(TextFiles.read(path, UTF_8)).asJsonObject
        require(json.get("schema").asInt == 1 && json.get("kind").asString == "STATE_OBSERVED") { "unsupported event schema" }
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
            require(event.revision >= 0) { "invalid event revision" }
            UpdateState.valueOf(event.state)
            require(event.operation in OPERATIONS) { "invalid update operation" }
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
        val OPERATIONS = setOf("STATE_OBSERVED", "CHECK", "STAGE", "ROLLBACK", "HEALTH", "HEALTH_ROLLBACK")
    }
}
