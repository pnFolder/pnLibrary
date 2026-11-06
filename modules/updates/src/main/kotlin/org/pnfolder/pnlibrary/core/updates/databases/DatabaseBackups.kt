package org.pnfolder.pnlibrary.core.updates.databases

import org.pnfolder.pnlibrary.api.config.DatabaseKind
import org.pnfolder.pnlibrary.api.updates.ProductId
import org.pnfolder.pnlibrary.api.updates.backup.BackupKind
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackupItem
import org.pnfolder.pnlibrary.core.updates.history.BackupStorage
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.stream.Collectors

/**
 * Copies the databases plugins marked with `@BackupDatabase` before an update and puts them back on request.
 * Copies live in `<folder>/<plan>/<plugin>/<name>.db.gz`; before a restore the current state is copied too, so a
 * restore can itself be undone.
 */
internal class DatabaseBackups(
    private val folder: Path,
    private val sources: DatabaseSources,
    private val engines: Map<DatabaseKind, DatabaseEngine> = mapOf(
        DatabaseKind.MYSQL to MysqlEngine(),
        DatabaseKind.POSTGRESQL to PostgresEngine(),
        DatabaseKind.MONGODB to MongoEngine(),
    ),
) {
    /** Copies every marked database of [plugins]. A failure stops the update: it would run without its safety copy. */
    fun copyBefore(plan: UUID, plugins: Collection<String>) {
        plugins.forEach { plugin ->
            sources.of(plugin).forEach { source ->
                val target = file(plan, plugin, source.name)
                try {
                    Files.createDirectories(target.parent)
                    dump(source, target)
                } catch (error: Exception) {
                    BackupStorage.delete(folder.resolve(plan.toString()))
                    throw IllegalStateException("Не удалось сохранить базу «${source.name}» плагина $plugin: ${error.message}", error)
                }
            }
        }
    }

    /** The databases saved for [plan], for the lists of backups. */
    fun items(plan: UUID): List<UpdateBackupItem> {
        val root = folder.resolve(plan.toString())
        if (!Files.isDirectory(root)) return emptyList()
        return Files.walk(root, 2).use { paths ->
            paths.filter { it.fileName.toString().endsWith(SUFFIX) && it.parent != root }.map { path ->
                UpdateBackupItem(ProductId.of(path.parent.fileName.toString()), BackupKind.DATABASE, nameOf(path), Files.size(path))
            }.collect(Collectors.toList())
        }
    }

    /** Replaces the tables of the database with the saved ones. The state it replaces is kept first. */
    fun restore(plan: UUID, plugin: String, name: String) {
        val saved = file(plan, plugin, name)
        require(Files.isRegularFile(saved)) { "Для набора $plan нет сохранённой базы «$name» плагина $plugin" }
        val source = sources.of(plugin).firstOrNull { it.name == name }
            ?: error("Плагин $plugin сейчас не описывает базу «$name»: она не отмечена @BackupDatabase или плагин не загружен")
        val before = folder.resolve(plan.toString()).resolve(BEFORE_RESTORE).resolve(Instant.now().toEpochMilli().toString())
            .resolve(plugin).resolve(saved.fileName)
        Files.createDirectories(before.parent)
        dump(source, before)
        val connection = DatabaseConnection.read(source)
        engineOf(source).restore(connection, saved)
    }

    private fun dump(source: DatabaseSource, target: Path) {
        val connection = DatabaseConnection.read(source)
        engineOf(source).dump(connection, target)
    }

    private fun engineOf(source: DatabaseSource): DatabaseEngine =
        engines[source.kind] ?: error("Базы ${source.kind} пока не поддерживаются")

    private fun file(plan: UUID, plugin: String, name: String): Path =
        folder.resolve(plan.toString()).resolve(plugin).resolve(URLEncoder.encode(name, "UTF-8") + SUFFIX)

    private fun nameOf(path: Path): String = URLDecoder.decode(path.fileName.toString().removeSuffix(SUFFIX), "UTF-8")

    private companion object {
        const val SUFFIX = ".db.gz"
        const val BEFORE_RESTORE = "before-restore"
    }
}
