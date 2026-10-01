package ru.privatenull.pnlibrary.core.updates

import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import ru.privatenull.pnlibrary.api.updates.UpdatePlan
import ru.privatenull.pnlibrary.api.updates.UpdatePlanSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateState
import ru.privatenull.pnlibrary.api.updates.BlockedReason
import ru.privatenull.pnlibrary.api.updates.ProductId
import ru.privatenull.pnlibrary.api.version.ApiVersionRange
import ru.privatenull.pnlibrary.api.version.SemanticVersion
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
    /**
     * Update plans contain java.time.Instant values (release publication time).
     * Gson's reflective adapter cannot access Instant's private fields across Java module
     * boundaries (Java 17+), so persist it as the same ISO-8601 string used by the
     * release catalog instead of relying on reflection.
     */
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Instant::class.java, JsonSerializer<Instant> { value, _, _ ->
            JsonPrimitive(value.toString())
        })
        .registerTypeAdapter(Instant::class.java, JsonDeserializer<Instant> { json, _, _ ->
            Instant.parse(json.asString)
        })
        .create()
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
                decodeBlockers(root),
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
        add("blockers", JsonArray().apply { snapshot.blockers.forEach { add(encodeBlocker(it)) } })
    }.toString().toByteArray(StandardCharsets.UTF_8)

    private fun encodeBlocker(reason: BlockedReason): JsonObject = JsonObject().apply {
        when (reason) {
            is BlockedReason.ApiMismatch -> {
                addProperty("type", "api-mismatch")
                addProperty("plugin", reason.product.value)
                addProperty("minimumApi", reason.supportedApi.minimum)
                addProperty("maximumApi", reason.supportedApi.maximum)
                addProperty("requiredApi", reason.requiredApi)
                reason.repository?.let { addProperty("repository", it) }
            }
            is BlockedReason.MissingDependency -> {
                addProperty("type", "missing-dependency")
                addProperty("plugin", reason.product.value)
                addProperty("dependency", reason.dependency.value)
                addProperty("minimumVersion", reason.minimumVersion.toString())
            }
            is BlockedReason.MissingExternalPluginDependency -> {
                addProperty("type", "missing-external-plugin")
                addProperty("plugin", reason.product.value)
                addProperty("dependency", reason.plugin)
                addProperty("minimumVersion", reason.minimumVersion.toString())
                reason.downloadPage?.let { addProperty("downloadPage", it) }
            }
            is BlockedReason.Frozen -> {
                addProperty("type", "frozen")
                addProperty("plugin", reason.product.value)
            }
            is BlockedReason.NoCompatibleRelease -> {
                addProperty("type", "no-compatible-release")
                addProperty("plugin", reason.product.value)
                addProperty("requiredApi", reason.requiredApi)
            }
        }
    }

    private fun decodeBlockers(root: JsonObject): List<BlockedReason> =
        root.getAsJsonArray("blockers")?.map { element ->
            val item = element.asJsonObject
            val product = ProductId.of(item.get("plugin").asString)
            when (item.get("type").asString) {
                "api-mismatch" -> BlockedReason.ApiMismatch(
                    product,
                    ApiVersionRange(item.get("minimumApi").asInt, item.get("maximumApi").asInt),
                    item.get("requiredApi").asInt,
                    item.get("repository")?.asString,
                )
                "missing-dependency" -> BlockedReason.MissingDependency(
                    product,
                    ProductId.of(item.get("dependency").asString),
                    SemanticVersion.parse(item.get("minimumVersion").asString),
                )
                "missing-external-plugin" -> BlockedReason.MissingExternalPluginDependency(
                    product,
                    item.get("dependency").asString,
                    SemanticVersion.parse(item.get("minimumVersion").asString),
                    item.get("downloadPage")?.asString,
                )
                "frozen" -> BlockedReason.Frozen(product)
                "no-compatible-release" -> BlockedReason.NoCompatibleRelease(product, item.get("requiredApi").asInt)
                else -> error("unknown update blocker type: ${item.get("type").asString}")
            }
        }.orEmpty()

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
