package ru.privatenull.pnlibrary.core.updates.policy

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import ru.privatenull.pnlibrary.api.updates.UpdateChannel
import ru.privatenull.pnlibrary.common.io.AtomicFiles
import ru.privatenull.pnlibrary.core.io.TextFiles
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path

/**
 * Per-component channel and automatic-download choices made at runtime, for example from `/pn updates settings`.
 *
 * `updates.yml` is hand-edited and commented, so it is never rewritten. Choices made through commands are kept
 * here instead and take precedence over `updates.yml` until they are changed again.
 */
internal class UpdatePolicyOverrides(
    private val file: Path,
    private val warning: (String) -> Unit = {},
) {
    private data class State(val channels: Map<String, UpdateChannel>, val automatic: Map<String, Boolean>)

    private val gson = GsonBuilder().setPrettyPrinting().create()

    @Volatile
    private var state = load()

    /** The channel chosen for [product] at runtime, or null when `updates.yml` decides. */
    fun channel(product: String): UpdateChannel? = state.channels[product]

    /** The automatic-download choice for [product] at runtime, or null when `updates.yml` decides. */
    fun automatic(product: String): Boolean? = state.automatic[product]

    /** Whether any component was switched to automatic download at runtime. */
    fun anyAutomaticEnabled(): Boolean = state.automatic.values.any { it }

    @Synchronized
    fun setChannel(product: String, channel: UpdateChannel) {
        state = state.copy(channels = state.channels + (product to channel))
        save()
    }

    @Synchronized
    fun setAutomatic(product: String, enabled: Boolean) {
        state = state.copy(automatic = state.automatic + (product to enabled))
        save()
    }

    private fun load(): State {
        if (!Files.isRegularFile(file)) return State(emptyMap(), emptyMap())
        return try {
            val root = JsonParser.parseString(TextFiles.read(file, UTF_8)).asJsonObject
            State(
                channels = root.getAsJsonObject("channels")?.entrySet().orEmpty().mapNotNull { (product, value) ->
                    runCatching { UpdateChannel.valueOf(value.asString) }.getOrNull()?.let { product to it }
                }.toMap(),
                automatic = root.getAsJsonObject("automatic")?.entrySet().orEmpty()
                    .associate { (product, value) -> product to value.asBoolean },
            )
        } catch (error: Exception) {
            warning("Unreadable update settings ${file.fileName}: ${error.javaClass.simpleName}")
            State(emptyMap(), emptyMap())
        }
    }

    private fun save() {
        Files.createDirectories(file.parent)
        val json = JsonObject().apply {
            add("channels", JsonObject().also { node -> state.channels.toSortedMap().forEach { (p, c) -> node.addProperty(p, c.name) } })
            add("automatic", JsonObject().also { node -> state.automatic.toSortedMap().forEach { (p, a) -> node.addProperty(p, a) } })
        }
        val temporary = Files.createTempFile(file.parent, ".settings-", ".tmp")
        try {
            TextFiles.write(temporary, gson.toJson(json), UTF_8)
            AtomicFiles.move(temporary, file)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
