package ru.privatenull.pnlibrary.core.updates

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import ru.privatenull.pnlibrary.core.io.TextFiles
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Remembers which managed components were present at the last start, so the history can say what appeared,
 * disappeared or changed between restarts, including changes made without the updater.
 *
 * The first observation only records a baseline and writes nothing to the history.
 */
internal class ComponentLedger(
    private val file: Path,
    private val journal: UpdateEventJournal,
    private val warning: (String) -> Unit = {},
) {
    /** What the ledger remembers about one component. */
    data class Component(val version: String, val channel: String, val automaticDownload: Boolean)

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val type = object : TypeToken<Map<String, Component>>() {}.type

    /**
     * Compares [current] with the remembered state, records what differs and stores [current].
     *
     * @param current every managed component that is registered now, by product ID
     * @param verifiedByHealth products whose version change was already reported by a post-restart health check
     */
    @Synchronized
    fun observe(current: Map<String, Component>, verifiedByHealth: Set<String> = emptySet()) {
        val previous = load()
        if (previous != null) {
            current.toSortedMap().forEach { (product, now) ->
                val before = previous[product]
                when {
                    before == null -> journal.recordComponent(OPERATION_ADDED, product, null, now.version, null)
                    before.version != now.version && product !in verifiedByHealth ->
                        journal.recordComponent(OPERATION_VERSION_CHANGED, product, before.version, now.version, null)
                }
                if (before != null) describeSettings(before, now)?.let { message ->
                    journal.recordComponent(OPERATION_SETTINGS_CHANGED, product, now.version, now.version, message)
                }
            }
            previous.toSortedMap().filterKeys { it !in current }.forEach { (product, before) ->
                journal.recordComponent(OPERATION_REMOVED, product, before.version, before.version, null)
            }
        }
        save(current)
    }

    private fun describeSettings(before: Component, now: Component): String? {
        val changes = buildList {
            if (before.channel != now.channel) add("канал: ${before.channel} → ${now.channel}")
            if (before.automaticDownload != now.automaticDownload) {
                add("автозагрузка: ${if (before.automaticDownload) "вкл" else "выкл"} → ${if (now.automaticDownload) "вкл" else "выкл"}")
            }
        }
        return changes.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    private fun load(): Map<String, Component>? {
        if (!Files.isRegularFile(file)) return null
        return try {
            gson.fromJson<Map<String, Component>>(TextFiles.read(file, UTF_8), type)
        } catch (error: Exception) {
            warning("Unreadable component ledger ${file.fileName}: ${error.javaClass.simpleName}")
            null
        }
    }

    private fun save(components: Map<String, Component>) {
        Files.createDirectories(file.parent)
        val temporary = Files.createTempFile(file.parent, ".components-", ".tmp")
        try {
            TextFiles.write(temporary, gson.toJson(components.toSortedMap(), type), UTF_8)
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    internal companion object {
        const val OPERATION_ADDED = "COMPONENT_ADDED"
        const val OPERATION_REMOVED = "COMPONENT_REMOVED"
        const val OPERATION_VERSION_CHANGED = "VERSION_CHANGED"
        const val OPERATION_SETTINGS_CHANGED = "SETTINGS_CHANGED"
    }
}
