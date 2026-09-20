package ru.privatenull.pnlibrary.core.updates

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Gson
import ru.privatenull.pnlibrary.api.updates.UpdatePlan
import ru.privatenull.pnlibrary.api.updates.UpdatePlanSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateState
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.UUID

internal class UpdateStateStore(
    private val root: Path,
    private val maximumEntries: Int = 100,
    private val maximumBytes: Long = 10L * 1024L * 1024L,
    private val warning: (String) -> Unit = {},
) {
    private val gson = Gson()
    private val stateFile = root.resolve("state.json")
    private val historyDirectory = root.resolve("history")

    init {
        require(maximumEntries > 0 && maximumBytes > 0)
        Files.createDirectories(historyDirectory)
    }

    @Synchronized
    fun save(snapshot: UpdatePlanSnapshot) {
        val bytes = encode(snapshot)
        writeAtomic(stateFile, bytes)
        writeAtomic(historyDirectory.resolve("%020d-%s.json".format(snapshot.revision, snapshot.id)), bytes)
        prune()
    }

    fun current(): UpdatePlanSnapshot? = read(stateFile, quarantine = true)

    fun history(): List<UpdatePlanSnapshot> = historyFiles().mapNotNull { read(it, quarantine = false) }

    private fun read(path: Path, quarantine: Boolean): UpdatePlanSnapshot? {
        if (!Files.isRegularFile(path)) return null
        return try {
            val root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).asJsonObject
            UpdatePlanSnapshot(
                UUID.fromString(root.get("id").asString),
                root.get("revision").asLong,
                UpdateState.valueOf(root.get("state").asString),
                root.get("plan")?.takeUnless { it.isJsonNull }?.let { gson.fromJson(it, UpdatePlan::class.java) },
                emptyList(),
                root.get("message")?.takeUnless { it.isJsonNull }?.asString,
            )
        } catch (error: Exception) {
            if (quarantine) {
                val target = path.resolveSibling("${path.fileName}.corrupt-${Instant.now().toEpochMilli()}")
                runCatching { Files.move(path, target, StandardCopyOption.REPLACE_EXISTING) }
                warning("Corrupt update state was quarantined: ${error.message}")
            }
            null
        }
    }

    private fun encode(snapshot: UpdatePlanSnapshot): ByteArray = JsonObject().apply {
        addProperty("schema", 1)
        addProperty("id", snapshot.id.toString())
        addProperty("revision", snapshot.revision)
        addProperty("state", snapshot.state.name)
        if (snapshot.message == null) add("message", null) else addProperty("message", snapshot.message)
        add("plan", gson.toJsonTree(snapshot.plan))
        addProperty("blockerCount", snapshot.blockers.size)
    }.toString().toByteArray(StandardCharsets.UTF_8)

    private fun prune() {
        val files = historyFiles().toMutableList()
        var bytes = files.sumOf { runCatching { Files.size(it) }.getOrDefault(0) }
        while (files.size > maximumEntries || bytes > maximumBytes) {
            val oldest = files.removeLast()
            bytes -= runCatching { Files.size(oldest) }.getOrDefault(0)
            Files.deleteIfExists(oldest)
        }
    }

    private fun historyFiles(): List<Path> = Files.list(historyDirectory).use { stream ->
        stream.filter(Files::isRegularFile).sorted(Comparator.reverseOrder()).toList()
    }

    private fun writeAtomic(path: Path, bytes: ByteArray) {
        Files.createDirectories(path.toAbsolutePath().parent)
        val temporary = Files.createTempFile(path.toAbsolutePath().parent, path.fileName.toString(), ".tmp")
        try {
            Files.write(temporary, bytes)
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temporary) }
    }
}
