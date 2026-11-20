package org.pnfolder.pnlibrary.updates.paths

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.pnfolder.pnlibrary.common.io.AtomicFiles
import org.pnfolder.pnlibrary.common.io.TextFiles
import org.pnfolder.pnlibrary.updates.engine.UpdatePreferencesStore
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Moves the state of older versions, which lay directly in the plugin folder, into `data`, and merges the two old
 * settings files into one. It never overwrites, never throws and reports what it could not do through [warning].
 */
class LegacyPathsMigration(
    private val paths: LibraryPaths,
    private val warning: (String) -> Unit = {},
) {
    /** Moves every old location that exists and whose new place is still free. */
    fun run() {
        guarded("the library data") {
            val old = paths.pluginFolder
            move(old.resolve("updates"), paths.updates)
            move(old.resolve("journal"), paths.journal)
            move(old.resolve("reports"), paths.reports)
            move(old.resolve("observability"), paths.observability)
            move(old.resolve("diagnostics").resolve("history"), paths.diagnosticsHistory)
            removeIfEmpty(old.resolve("diagnostics"))
            move(old.resolve("task-history.tsv"), paths.taskHistory)
            move(old.resolve("event-history.tsv"), paths.eventHistory)
            move(old.resolve("upload-ledger.json"), paths.uploadLedger)
            move(old.resolve("downloads"), paths.downloads)
            mergeSettings()
        }
    }

    /** Moves [source] to [target] unless [target] exists; two folders are merged entry by entry. */
    private fun move(source: Path, target: Path) {
        if (!Files.exists(source)) return
        guarded(source.fileName.toString()) {
            when {
                !Files.exists(target) -> {
                    Files.createDirectories(target.parent)
                    AtomicFiles.move(source, target, replaceExisting = false)
                }
                Files.isDirectory(source) && Files.isDirectory(target) -> {
                    Files.newDirectoryStream(source).use { it.toList() }.forEach { move(it, target.resolve(it.fileName.toString())) }
                    removeIfEmpty(source)
                }
            }
        }
    }

    private fun removeIfEmpty(folder: Path) {
        if (!Files.isDirectory(folder)) return
        try {
            Files.delete(folder)
        } catch (_: DirectoryNotEmptyException) {
            // Something else lives there; it stays.
        } catch (error: Exception) {
            warning("Could not remove the empty legacy folder ${folder.fileName}: ${error.javaClass.simpleName}")
        }
    }

    /** Folds the old `settings.json` and `freezes.json` into the preferences file, then removes them. */
    private fun mergeSettings() {
        val settings = paths.updates.resolve("settings.json")
        val freezes = paths.updates.resolve("freezes.json")
        if (Files.exists(paths.preferences) || !(Files.isRegularFile(settings) || Files.isRegularFile(freezes))) return
        guarded("the update settings") {
            val store = UpdatePreferencesStore(paths.preferences, warning)
            val merged = legacy(settings) { root ->
                UpdatePreferencesStore.Preferences(
                    channels = root.getAsJsonObject("channels")?.entrySet().orEmpty().associate { (product, value) -> product to value.asString },
                    automatic = root.getAsJsonObject("automatic")?.entrySet().orEmpty().associate { (product, value) -> product to value.asBoolean },
                    allPlugins = root.get("allPlugins")?.takeIf { !it.isJsonNull }?.asBoolean,
                )
            }
            val paused = legacy(freezes) { root -> root.entrySet().associate { (product, value) -> product to value.asLong } }
            store.update {
                it.copy(
                    channels = merged.value.channels,
                    automatic = merged.value.automatic,
                    allPlugins = merged.value.allPlugins,
                    freezes = paused.value,
                )
            }
            if (merged.readable) Files.deleteIfExists(settings)
            if (paused.readable) Files.deleteIfExists(freezes)
        }
    }

    private class Legacy<T>(val value: T, val readable: Boolean)

    /** Reads [file] with [read]; a missing file gives the empty value, an unreadable one is reported and kept. */
    private fun <T> legacy(file: Path, read: (JsonObject) -> T): Legacy<T> {
        val empty = read(JsonObject())
        if (!Files.isRegularFile(file)) return Legacy(empty, true)
        return try {
            Legacy(read(JsonParser.parseString(TextFiles.read(file, UTF_8)).asJsonObject), true)
        } catch (error: Exception) {
            warning("Unreadable legacy file ${file.fileName} was left in place: ${error.javaClass.simpleName}")
            Legacy(empty, false)
        }
    }

    private fun guarded(what: String, action: () -> Unit) {
        try {
            action()
        } catch (error: Exception) {
            warning("Could not migrate $what to the new layout: ${error.javaClass.simpleName}: ${error.message}")
        }
    }
}
