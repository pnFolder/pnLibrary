package org.pnfolder.pnlibrary.core.commands.modules

import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * What one plugin takes on the disk of this server, split by kind; plain values, so it also crosses to another server.
 *
 * @property name plugin name as its folder or jar is called
 * @property jar the plugin's own jar
 * @property configs settings files (`yml`, `json`, `toml`, `properties`, ...) in its folder
 * @property libraries jars it downloaded into a `lib`, `libs` or `libraries` folder
 * @property logs log files in its folder
 * @property data everything else in its folder: databases, caches, saved players
 */
data class DiskRow(val name: String, val jar: Long, val configs: Long, val libraries: Long, val logs: Long, val data: Long) {
    val total: Long get() = jar + configs + libraries + logs + data
}

/**
 * The disk of the `plugins` folder.
 *
 * @property registeredLibraries size of the jars of libraries that plugins and modules registered through pnLibrary
 * @property free free space on the disk, or -1 when unknown
 * @property capacity size of the disk, or -1 when unknown
 */
data class DiskReport(val rows: List<DiskRow>, val registeredLibraries: Long, val free: Long, val capacity: Long) {
    val total: Long get() = rows.sumOf { it.total }

    companion object {
        val EMPTY = DiskReport(emptyList(), 0, -1, -1)
    }
}

/** Counts the bytes of the `plugins` folder, plugin by plugin; never fails, an unreadable file counts as zero. */
object DiskUsage {
    private val CONFIGS = setOf("yml", "yaml", "json", "toml", "properties", "conf", "cfg", "hocon", "ini", "xml")
    private val LIBRARY_FOLDERS = setOf("lib", "libs", "libraries")

    /** The `plugins` folder of this server; the runtime sets it at start, `null` until then. */
    @Volatile
    var pluginsFolder: Path? = null

    /** Scans [plugins]; [registered] are the jars of registered libraries, counted once each. */
    fun scan(plugins: Path?, registered: Collection<String>): DiskReport {
        val known = registered.map(::File).filter { it.isFile }.distinctBy { it.absolutePath }.sumOf { it.length() }
        if (plugins == null || !Files.isDirectory(plugins)) return DiskReport(emptyList(), known, -1, -1)
        val rows = linkedMapOf<String, DiskRow>()
        val children = runCatching { Files.list(plugins).use { stream -> stream.collect(java.util.stream.Collectors.toList<Path>()) } }.getOrDefault(emptyList())
        val folders = children.filter { Files.isDirectory(it) }
        folders.forEach { rows[it.fileName.toString().lowercase()] = folderRow(it) }
        children.filter { it.fileName.toString().endsWith(".jar", true) }.forEach { jar ->
            val base = jar.fileName.toString().dropLast(4).replace(Regex("[-_ ]v?\\d.*$"), "")
            val key = rows.keys.firstOrNull { it == base.lowercase() } ?: base.lowercase()
            val size = runCatching { Files.size(jar) }.getOrDefault(0L)
            val row = rows[key] ?: DiskRow(base, 0, 0, 0, 0, 0)
            rows[key] = row.copy(jar = row.jar + size)
        }
        val store = runCatching { Files.getFileStore(plugins) }.getOrNull()
        return DiskReport(
            rows.values.sortedByDescending { it.total }, known,
            store?.let { runCatching { it.usableSpace }.getOrDefault(-1L) } ?: -1,
            store?.let { runCatching { it.totalSpace }.getOrDefault(-1L) } ?: -1,
        )
    }

    private fun folderRow(folder: Path): DiskRow {
        var configs = 0L
        var libraries = 0L
        var logs = 0L
        var data = 0L
        runCatching {
            Files.walk(folder).use { stream ->
                stream.filter { Files.isRegularFile(it) }.forEach { file ->
                    val size = runCatching { Files.size(file) }.getOrDefault(0L)
                    val relative = folder.relativize(file)
                    val first = relative.getName(0).toString().lowercase()
                    val extension = file.fileName.toString().substringAfterLast('.', "").lowercase()
                    when {
                        first in LIBRARY_FOLDERS && relative.nameCount > 1 -> libraries += size
                        extension in CONFIGS -> configs += size
                        extension == "log" || first == "logs" || (extension == "gz" && first.startsWith("log")) -> logs += size
                        else -> data += size
                    }
                }
            }
        }
        return DiskRow(folder.fileName.toString(), 0, configs, libraries, logs, data)
    }

    /** `1.5 МБ`, `320 КБ`, `12 Б`; the same text in the command and the menu. */
    fun format(bytes: Long): String = when {
        bytes < 0 -> "неизвестно"
        bytes < 1024 -> "$bytes Б"
        bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
        bytes < 1024L * 1024 * 1024 -> "%.1f МБ".format(java.util.Locale.ROOT, bytes / 1048576.0)
        else -> "%.2f ГБ".format(java.util.Locale.ROOT, bytes / 1073741824.0)
    }

    /** Registered libraries of this server and the size of the jar of each, for [LibraryRow]. */
    fun registeredFiles(library: PnLibrary): List<String> = LibraryRow.all(library).mapNotNull { it.file }.distinct()
}
