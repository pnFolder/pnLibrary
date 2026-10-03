package ru.privatenull.pnlibrary.core.activity

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import ru.privatenull.pnlibrary.api.activity.ActivityEvent
import ru.privatenull.pnlibrary.api.activity.ActivityAttachment
import ru.privatenull.pnlibrary.api.activity.ActivityQuery
import ru.privatenull.pnlibrary.api.activity.ActivityService
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.UUID

/** Internal black-box journal. Retention is deliberately code-owned, not user-configurable. */
class ActivityJournalService(dataFolder: Path, private val clock: () -> Long = System::currentTimeMillis) : ActivityService {
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    private val lock = Any()
    private val events = ArrayDeque<ActivityEvent>()
    private val directory = dataFolder.resolve("observability")
    private val file = directory.resolve("activity.jsonl")
    private val attachments = directory.resolve("attachments")
    @Volatile private var closed = false

    init {
        Files.createDirectories(directory)
        Files.createDirectories(attachments)
        loadExisting()
        purgeExpired()
    }

    override fun record(event: ActivityEvent): ActivityEvent {
        check(!closed) { "Activity journal is closed" }
        val normalized = normalize(event.copy(timestamp = event.timestamp.takeIf { it > 0 } ?: clock()))
        synchronized(lock) {
            events.addLast(normalized)
            while (events.size > MAX_EVENTS) events.removeFirst()
            Files.writeString(file, gson.toJson(normalized) + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)
        }
        return normalized
    }

    override fun recent(query: ActivityQuery): List<ActivityEvent> {
        val pluginId = query.pluginId
        val category = query.category
        val minimumSeverity = query.minimumSeverity
        val since = query.since
        val until = query.until
        return synchronized(lock) {
        events.toList().asReversed()
    }.asSequence()
        .filter { pluginId == null || it.pluginId == pluginId }
        .filter { category == null || it.category == category }
        .filter { minimumSeverity == null || it.severity.ordinal >= minimumSeverity.ordinal }
        .filter { since == null || it.timestamp >= since }
        .filter { until == null || it.timestamp <= until }
        .take(query.limit.coerceIn(1, 500)).toList()
    }

    override fun attach(eventId: String, name: String, contentType: String, bytes: ByteArray): ActivityAttachment {
        check(!closed) { "Activity journal is closed" }
        require(eventId.isNotBlank()) { "eventId must not be blank" }
        require(bytes.size <= MAX_ATTACHMENT_BYTES) { "attachment exceeds internal size limit" }
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(128).ifBlank { "attachment.bin" }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val id = UUID.randomUUID().toString()
        Files.write(attachments.resolve("$id.bin"), bytes)
        return ActivityAttachment(id, eventId, safeName, contentType.take(128), bytes.size.toLong(), digest)
    }

    override fun attachFile(eventId: String, path: Path, contentType: String): ActivityAttachment {
        require(Files.isRegularFile(path)) { "attachment path is not a regular file" }
        return attach(eventId, path.fileName.toString(), contentType, Files.readAllBytes(path))
    }

    override fun exportJournal(): ByteArray = synchronized(lock) {
        events.joinToString("\n", postfix = if (events.isEmpty()) "" else "\n") { gson.toJson(it) }.toByteArray(StandardCharsets.UTF_8)
    }

    override fun exportAttachments(): Map<String, ByteArray> = synchronized(lock) {
        if (!Files.isDirectory(attachments)) emptyMap()
        else Files.list(attachments).use { stream ->
            stream.filter(Files::isRegularFile).toList().associate { path -> path.fileName.toString() to Files.readAllBytes(path) }
        }
    }

    override fun clear() = synchronized(lock) {
        events.removeIf { it.severity != ru.privatenull.pnlibrary.api.activity.ActivitySeverity.CRITICAL }
        rewrite()
    }

    override fun close() { closed = true }

    private fun purgeExpired() = synchronized(lock) {
        val now = clock()
        events.removeIf { event ->
            event.severity != ru.privatenull.pnlibrary.api.activity.ActivitySeverity.CRITICAL &&
                now - event.timestamp > retentionMillis(event.severity)
        }
        rewrite()
    }

    private fun loadExisting() = synchronized(lock) {
        if (!Files.isRegularFile(file)) return
        Files.readAllLines(file, StandardCharsets.UTF_8).asSequence()
            .mapNotNull { runCatching { gson.fromJson(it, ActivityEvent::class.java) }.getOrNull() }
            .toList().takeLast(MAX_EVENTS).forEach(events::addLast)
    }

    private fun rewrite() {
        Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE).use { writer ->
            events.forEach { writer.appendLine(gson.toJson(it)) }
        }
    }

    private fun normalize(event: ActivityEvent): ActivityEvent = event.copy(
        type = event.type.trim().uppercase().replace(Regex("[^A-Z0-9_.-]"), "_").take(96).ifBlank { "UNKNOWN" },
        source = event.source?.take(128), pluginId = event.pluginId?.take(128),
        sessionId = event.sessionId?.take(128), correlationId = event.correlationId?.take(128),
        metadata = event.metadata.entries.take(MAX_METADATA).associate { it.key.take(64) to it.value.take(512) },
    )

    private fun retentionMillis(severity: ru.privatenull.pnlibrary.api.activity.ActivitySeverity) = when (severity) {
        ru.privatenull.pnlibrary.api.activity.ActivitySeverity.TRACE -> Duration.ofHours(6).toMillis()
        ru.privatenull.pnlibrary.api.activity.ActivitySeverity.INFO -> Duration.ofDays(7).toMillis()
        ru.privatenull.pnlibrary.api.activity.ActivitySeverity.NOTICE,
        ru.privatenull.pnlibrary.api.activity.ActivitySeverity.WARNING -> Duration.ofDays(30).toMillis()
        ru.privatenull.pnlibrary.api.activity.ActivitySeverity.ERROR -> Duration.ofDays(90).toMillis()
        ru.privatenull.pnlibrary.api.activity.ActivitySeverity.CRITICAL -> Long.MAX_VALUE
    }

    private companion object {
        const val MAX_EVENTS = 10_000
        const val MAX_METADATA = 32
        const val MAX_ATTACHMENT_BYTES = 16 * 1024 * 1024
    }
}
