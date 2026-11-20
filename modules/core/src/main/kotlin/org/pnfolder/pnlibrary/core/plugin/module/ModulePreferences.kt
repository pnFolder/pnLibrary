package org.pnfolder.pnlibrary.core.plugin.module

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.pnfolder.pnlibrary.common.io.AtomicFiles
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Which modules an administrator keeps switched off across restarts, saved as `modules/preferences.json`
 * (`{"disabled": ["plugin:module"]}`). Without a [file] the choices live only in memory.
 */
internal class ModulePreferences(private val file: Path? = null) {
    private val disabled = linkedSetOf<String>()

    init {
        file?.takeIf { Files.isRegularFile(it) }?.let { path ->
            runCatching {
                val root = Gson().fromJson(String(Files.readAllBytes(path), StandardCharsets.UTF_8), JsonObject::class.java)
                root?.getAsJsonArray("disabled")?.forEach { disabled += it.asString }
            }
        }
    }

    /** Whether the module starts with the server. */
    @Synchronized
    fun autoStart(key: String): Boolean = key !in disabled

    /** Remembers the choice and writes it to disk. */
    @Synchronized
    fun setAutoStart(key: String, enabled: Boolean) {
        if (!(if (enabled) disabled.remove(key) else disabled.add(key))) return
        val path = file ?: return
        Files.createDirectories(path.parent)
        val root = JsonObject().apply { add("disabled", Gson().toJsonTree(disabled.toList())) }
        AtomicFiles.write(path, Gson().toJson(root).toByteArray(StandardCharsets.UTF_8))
    }
}
