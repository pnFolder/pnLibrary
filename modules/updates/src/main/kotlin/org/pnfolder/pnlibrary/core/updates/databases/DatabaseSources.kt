package org.pnfolder.pnlibrary.core.updates.databases

import org.pnfolder.pnlibrary.api.config.DatabaseKind
import org.pnfolder.pnlibrary.api.config.DatabaseRole
import org.pnfolder.pnlibrary.config.ManagedConfigFile
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A database a plugin marked with `@BackupDatabase`: whose it is, what it is called and where its connection is written.
 * @property plugin lowercase id of the plugin that owns the database
 * @property kind database engine
 * @property name name given in `@BackupDatabase`, unique within the plugin
 * @property file configuration file that holds the connection
 * @property path dotted path of the connection section inside [file]
 * @property keys field names that replace the usual ones, by role
 * @property tables fixed tables or collections to copy, empty for all
 * @property tableKeys configuration fields that hold a table name
 */
class DatabaseSource(
    val plugin: String,
    val kind: DatabaseKind,
    val name: String,
    val file: Path,
    val path: String,
    val keys: Map<DatabaseRole, String> = emptyMap(),
    val tables: List<String> = emptyList(),
    val tableKeys: List<String> = emptyList(),
)

/** The databases of all plugins, filled while plugins register their configuration files. */
class DatabaseSources {
    private val sources = ConcurrentHashMap<String, CopyOnWriteArrayList<DatabaseSource>>()

    /** Remembers the databases of [file]; closing the result forgets them again. */
    fun register(file: ManagedConfigFile): AutoCloseable? {
        if (file.databases.isEmpty()) return null
        val key = file.pluginId.value.lowercase(Locale.ROOT)
        val path = file.directory.resolve(file.path)
        val added = file.databases.map { DatabaseSource(key, it.kind, it.name, path, it.path, it.keys, it.tables, it.tableKeys) }
        val list = sources.getOrPut(key) { CopyOnWriteArrayList() }
        val names = (added.map { it.name } + list.map { it.name })
        require(names.distinct().size == names.size) {
            "У плагина ${file.pluginId.value} две базы с одним именем: имя в @BackupDatabase должно быть разным"
        }
        list.addAll(added)
        return AutoCloseable { sources[key]?.removeAll(added.toSet()) }
    }

    /** The databases registered for [plugin], whatever its letter case. */
    fun of(plugin: String): List<DatabaseSource> = sources[plugin.lowercase(Locale.ROOT)].orEmpty()
}
