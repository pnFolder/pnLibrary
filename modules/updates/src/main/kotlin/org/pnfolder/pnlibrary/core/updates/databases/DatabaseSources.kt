package org.pnfolder.pnlibrary.core.updates.databases

import org.pnfolder.pnlibrary.api.config.DatabaseKind
import org.pnfolder.pnlibrary.api.config.DatabaseRole
import org.pnfolder.pnlibrary.config.ManagedConfigFile
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** A database a plugin marked with `@BackupDatabase`: whose it is, what it is called and where its connection is written. */
class DatabaseSource(
    val plugin: String,
    val kind: DatabaseKind,
    val name: String,
    val file: Path,
    val path: String,
    val keys: Map<DatabaseRole, String> = emptyMap(),
)

/** The databases of all plugins, filled while plugins register their configuration files. */
class DatabaseSources {
    private val sources = ConcurrentHashMap<String, CopyOnWriteArrayList<DatabaseSource>>()

    /** Remembers the databases of [file]; closing the result forgets them again. */
    fun register(file: ManagedConfigFile): AutoCloseable? {
        if (file.databases.isEmpty()) return null
        val key = file.pluginId.value.lowercase(Locale.ROOT)
        val added = file.databases.map { DatabaseSource(key, it.kind, it.name, file.directory.resolve(file.path), it.path, it.keys) }
        val list = sources.getOrPut(key) { CopyOnWriteArrayList() }
        val names = (added.map { it.name } + list.map { it.name })
        require(names.distinct().size == names.size) { "У плагина ${file.pluginId.value} две базы с одним именем: имя в @BackupDatabase должно быть разным" }
        list.addAll(added)
        return AutoCloseable { sources[key]?.removeAll(added.toSet()) }
    }

    fun of(plugin: String): List<DatabaseSource> = sources[plugin.lowercase(Locale.ROOT)].orEmpty()
}
