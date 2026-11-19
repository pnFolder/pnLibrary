package org.pnfolder.pnlibrary.updates.engine

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.pnfolder.pnlibrary.common.io.AtomicFiles
import org.pnfolder.pnlibrary.common.io.TextFiles
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path

/**
 * The administrator's update choices in one file: release channels, Java builds, automatic download and component pauses.
 *
 * Several owners edit their own sections of the same file, so there is one in-memory model and one lock; every change
 * is read, modified and written back under that lock, and a save never discards what another owner stored.
 */
internal class UpdatePreferencesStore(
    private val file: Path,
    private val warning: (String) -> Unit = {},
) {
    /** What is stored: channel and automatic-download choices by product, the all-plugins choice and pause expiries in epoch millis. */
    data class Preferences(
        val channels: Map<String, String> = emptyMap(),
        val automatic: Map<String, Boolean> = emptyMap(),
        val allPlugins: Boolean? = null,
        val freezes: Map<String, Long> = emptyMap(),
        val javaBuilds: Map<String, Int> = emptyMap(),
    )

    private val gson = GsonBuilder().setPrettyPrinting().create()

    @Volatile
    private var preferences = load()

    /** The file name, for messages. */
    val fileName: String get() = file.fileName.toString()

    /** What is stored now. */
    fun current(): Preferences = preferences

    /** Applies [change] to what is stored and saves the result; a failed save leaves the stored model as it was. */
    @Synchronized
    fun update(change: (Preferences) -> Preferences) {
        val changed = change(preferences)
        if (changed == preferences) return
        write(changed)
        preferences = changed
    }

    private fun load(): Preferences {
        if (!Files.isRegularFile(file)) return Preferences()
        return try {
            val root = JsonParser.parseString(TextFiles.read(file, UTF_8)).asJsonObject
            Preferences(
                channels = root.getAsJsonObject("channels")?.entrySet().orEmpty().associate { (product, value) -> product to value.asString },
                automatic = root.getAsJsonObject("automatic")?.entrySet().orEmpty().associate { (product, value) -> product to value.asBoolean },
                allPlugins = root.get("allPlugins")?.takeIf { !it.isJsonNull }?.asBoolean,
                freezes = root.getAsJsonObject("freezes")?.entrySet().orEmpty().associate { (product, value) -> product to value.asLong },
                javaBuilds = root.getAsJsonObject("javaBuilds")?.entrySet().orEmpty().associate { (product, value) -> product to value.asInt },
            )
        } catch (error: Exception) {
            warning("Unreadable update settings $fileName: ${error.javaClass.simpleName}")
            Preferences()
        }
    }

    private fun write(value: Preferences) {
        val json = JsonObject().apply {
            value.allPlugins?.let { addProperty("allPlugins", it) }
            add("channels", JsonObject().also { node -> value.channels.toSortedMap().forEach { (p, c) -> node.addProperty(p, c) } })
            add("automatic", JsonObject().also { node -> value.automatic.toSortedMap().forEach { (p, a) -> node.addProperty(p, a) } })
            add("freezes", JsonObject().also { node -> value.freezes.toSortedMap().forEach { (p, e) -> node.addProperty(p, e) } })
            add("javaBuilds", JsonObject().also { node -> value.javaBuilds.toSortedMap().forEach { (p, j) -> node.addProperty(p, j) } })
        }
        AtomicFiles.write(file, gson.toJson(json).toByteArray(UTF_8))
    }
}
