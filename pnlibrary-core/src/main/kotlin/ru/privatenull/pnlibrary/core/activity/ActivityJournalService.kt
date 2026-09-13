package ru.privatenull.pnlibrary.core.activity

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import ru.privatenull.pnlibrary.api.activity.ActivityEvent
import ru.privatenull.pnlibrary.api.activity.ActivityQuery
import ru.privatenull.pnlibrary.api.activity.ActivityService
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.ArrayDeque

class ActivityJournalService(
    dataFolder: Path,
    private val maxEvents: Int = 10_000,
) : ActivityService {
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    private val lock = Any()
    private val events = ArrayDeque<ActivityEvent>()
    private val directory = dataFolder.resolve("activity")
    private val file = directory.resolve("activity.jsonl")
    @Volatile private var closed = false

    init {
        Files.createDirectories(directory)
        loadExisting()
    }

    override fun record(event: ActivityEvent): ActivityEvent {
        check(!closed) { "Activity service is closed" }
        val normalized = normalize(event)
        synchronized(lock) {
            events.addLast(normalized)
            while (events.size > maxEvents) events.removeFirst()
            Files.write(
                file,
                (gson.toJson(normalized) + "\n").toByteArray(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND,
            )
            compactIfNeeded()
        }
        return normalized
    }

    override fun recent(query: ActivityQuery): List<ActivityEvent> {
        val limit = query.limit.coerceIn(1, 500)
        val snapshot = synchronized(lock) { events.toList().asReversed() }
        return snapshot.asSequence()
            .filter { query.actorId == null || it.actorId == query.actorId }
            .filter { query.sessionId == null || it.sessionId == query.sessionId }
            .filter { query.correlationId == null || it.correlationId == query.correlationId }
            .filter { query.pluginId == null || it.pluginId == query.pluginId }
            .filter { query.category == null || it.category == query.category }
            .filter { query.minimumSeverity == null || it.severity.ordinal >= query.minimumSeverity.ordinal }
            .filter { query.types.isEmpty() || it.type in query.types }
            .filter { query.since == null || it.timestamp >= query.since }
            .filter { query.until == null || it.timestamp <= query.until }
            .take(limit)
            .toList()
    }

    override fun clear() {
        synchronized(lock) {
            events.clear()
            Files.deleteIfExists(file)
        }
    }

    override fun close() {
        closed = true
    }

    private fun normalize(event: ActivityEvent): ActivityEvent {
        val type = event.type.trim().uppercase().replace(Regex("[^A-Z0-9_.-]"), "_").take(96)
        require(type.isNotBlank()) { "Activity event type must not be blank" }
        val metadata = LinkedHashMap<String, String>()
        event.metadata.entries.take(MAX_METADATA_ENTRIES).forEach { (rawKey, rawValue) ->
            val key = rawKey.trim().replace(Regex("[^A-Za-z0-9_.-]"), "_").take(MAX_KEY_LENGTH)
            if (key.isNotBlank()) metadata[key] = rawValue.take(MAX_VALUE_LENGTH)
        }
        return event.copy(
            type = type,
            actorId = event.actorId?.take(MAX_ID_LENGTH),
            sessionId = event.sessionId?.take(MAX_ID_LENGTH),
            correlationId = event.correlationId?.take(MAX_ID_LENGTH),
            source = event.source?.take(MAX_FIELD_LENGTH),
            action = event.action?.take(MAX_FIELD_LENGTH),
            target = event.target?.take(MAX_FIELD_LENGTH),
            pluginId = event.pluginId?.take(MAX_FIELD_LENGTH),
            metadata = metadata,
        )
    }

    private fun loadExisting() {
        if (!Files.isRegularFile(file)) return
        synchronized(lock) {
            val loaded = Files.readAllLines(file, StandardCharsets.UTF_8)
                .asSequence()
                .mapNotNull { line -> runCatching { gson.fromJson(line, ActivityEvent::class.java) }.getOrNull() }
                .toList()
                .takeLast(maxEvents)
            loaded.forEach(events::addLast)
        }
    }

    private fun compactIfNeeded() {
        if (!Files.isRegularFile(file) || Files.size(file) <= COMPACT_AFTER_BYTES) return
        val temp = directory.resolve("activity.jsonl.tmp")
        Files.newBufferedWriter(temp, StandardCharsets.UTF_8).use { writer ->
            events.forEach {
                writer.write(gson.toJson(it))
                writer.newLine()
            }
        }
        Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        private const val MAX_METADATA_ENTRIES = 32
        private const val MAX_KEY_LENGTH = 64
        private const val MAX_VALUE_LENGTH = 512
        private const val MAX_ID_LENGTH = 128
        private const val MAX_FIELD_LENGTH = 128
        private const val COMPACT_AFTER_BYTES = 8L * 1024L * 1024L
    }
}
