package org.pnfolder.pnlibrary.core.updates.catalog

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType
import org.pnfolder.pnlibrary.api.commands.definition.CommandNodeBuilder
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.core.exceptions.rootCause
import java.util.Locale

/**
 * The `backups` commands of the catalog: the list with its filters, and `restore`, `files` and `delete` of one saved set.
 *
 * @param ready returns whether the command may run for this sender; it reports the reason itself
 * @param routesFor the routes adapted to the sender
 * @param requestedPage the page the sender asked for, or `null` after telling them it is wrong
 */
internal class UpdateCatalogBackupCommands(
    private val library: PnLibrary,
    private val ready: (CommandContext) -> Boolean,
    private val routesFor: (CommandContext) -> UpdateCatalogRoutes,
    private val requestedPage: (CommandContext) -> Int?,
    private val deliver: (Runnable) -> Unit,
) {
    /** Adds `backups` through [addLiteral]; [readPermission] opens the list, [managePermission] the commands that change something. */
    fun install(addLiteral: (String, CommandNodeBuilder.() -> Unit) -> Unit, readPermission: String?, managePermission: String?) {
        addLiteral("backups") {
            readPermission?.let(::permission)
            backupFilters(emptySet())
            literal("restore") {
                managePermission?.let(::permission)
                argument("backupId", ArgumentType.string()) {
                    argument("backupPlugin", ArgumentType.string()) {
                        suggests { library.updateProductNames() }
                        literal("confirm") { argument("databaseName", ArgumentType.greedyString()) { executes(::restoreDatabase) } }
                        argument("databaseName", ArgumentType.greedyString()) { executes(::previewRestore) }
                    }
                }
            }
            literal("files") {
                managePermission?.let(::permission)
                argument("backupId", ArgumentType.string()) {
                    argument("backupPlugin", ArgumentType.string()) {
                        suggests { library.updateProductNames() }
                        executes(::extractBackup)
                    }
                }
            }
            literal("delete") {
                managePermission?.let(::permission)
                argument("backupId", ArgumentType.string()) { executes(::deleteBackup) }
            }
        }
    }

    /**
     * The words after `backups`: `plugin <name>`, `since <7d|date>` and `sort <new|old|plugin>`, each at most once and
     * in any order, then an optional page. Every path through them shows the list.
     */
    private fun CommandNodeBuilder.backupFilters(used: Set<String>) {
        executes(::showBackups)
        argument("page", ArgumentType.integer()) { executes(::showBackups) }
        if ("plugin" !in used) {
            literal("plugin") {
                argument("backupPlugin", ArgumentType.string()) {
                    suggests { library.updateProductNames() }
                    backupFilters(used + "plugin")
                }
            }
        }
        if ("since" !in used) {
            literal("since") {
                argument("backupSince", ArgumentType.string()) {
                    suggests { listOf("1d", "7d", "30d") }
                    backupFilters(used + "since")
                }
            }
        }
        if ("sort" !in used) {
            literal("sort") {
                argument("backupSort", ArgumentType.choice(*BackupOrder.values().map { it.word }.toTypedArray())) {
                    backupFilters(used + "sort")
                }
            }
        }
    }

    /** Without `confirm` it only warns and shows the command that does it: a database cannot be put back by accident. */
    private fun previewRestore(context: CommandContext) {
        if (!ready(context)) return
        val sender = context.sender
        val id = backupId(context) ?: return
        val plugin = context.get<String>("backupPlugin")
        val name = context.get<String>("databaseName")
        sender.send(error("База «$name» плагина $plugin будет заменена сохранённой: всё, что записано в неё после копии, пропадёт."))
        sender.send(Component.text("Текущее состояние сохранится отдельной копией."))
        sender.send(Component.text("Если согласны, выполните: /pn updates backups restore $id $plugin confirm $name"))
    }

    private fun restoreDatabase(context: CommandContext) {
        if (!ready(context)) return
        val sender = context.sender
        val id = backupId(context) ?: return
        val plugin = context.get<String>("backupPlugin")
        val name = context.get<String>("databaseName")
        library.updates.backups.restoreDatabase(id, plugin, name, sender.asUpdateActor()).whenComplete { _, failure ->
            deliver(Runnable {
                if (failure == null) sender.send(Component.text("База «$name» возвращена. Перезапустите плагин $plugin."))
                else sender.send(error("Не удалось вернуть базу: ${failure.rootCause().message}"))
            })
        }
    }

    /** The id the sender typed, or `null` after telling them it is not one. */
    private fun backupId(context: CommandContext): java.util.UUID? =
        runCatching { java.util.UUID.fromString(context.get<String>("backupId")) }.getOrNull()
            ?: run { context.sender.send(error("Некорректный ID резервного набора.")); null }

    /** Hands out the saved databases and exports of a plugin as plain files, for what the library cannot put back itself. */
    private fun extractBackup(context: CommandContext) {
        if (!ready(context)) return
        val sender = context.sender
        val id = backupId(context) ?: return
        val plugin = context.get<String>("backupPlugin")
        val actor = sender.asUpdateActor()
        library.updates.backups.extract(id, plugin, actor).whenComplete { folder, failure ->
            deliver(Runnable {
                if (failure == null) sender.send(Component.text("Файлы плагина $plugin лежат в папке: $folder"))
                else sender.send(error("Не удалось выдать файлы: ${failure.rootCause().message}"))
            })
        }
    }

    private fun deleteBackup(context: CommandContext) {
        if (!ready(context)) return
        val sender = context.sender
        val id = backupId(context) ?: return
        val actor = sender.asUpdateActor()
        library.updates.backups.delete(id, actor).whenComplete { _, failure ->
            deliver(Runnable {
                if (failure == null) sender.send(Component.text("Резервный набор $id удалён."))
                else sender.send(error("Не удалось удалить набор: ${failure.rootCause().message}"))
            })
        }
    }

    private fun showBackups(context: CommandContext) {
        if (!ready(context)) return
        val page = requestedPage(context) ?: return
        val query = backupQuery(context) ?: return
        library.updates.backups.list().whenComplete { backups, failure ->
            deliver(Runnable {
                if (failure != null) context.sender.send(error("Не удалось прочитать резервные наборы."))
                else UpdateCatalogView.backups(backups, page, routesFor(context), query = query).forEach(context.sender::send)
            })
        }
    }

    /** What the words after `backups` ask for, or null after telling the sender that a value is wrong. */
    private fun backupQuery(context: CommandContext): BackupQuery? {
        val plugin = if (context.contains("backupPlugin")) context.get<String>("backupPlugin") else null
        val sinceText = if (context.contains("backupSince")) context.get<String>("backupSince") else null
        val since = sinceText?.let {
            BackupQuery.parseSince(it, java.time.Instant.now(), org.pnfolder.pnlibrary.common.time.DisplayZone.zone)
                ?: return context.sender.send(error("Не понял «$it»: после since напишите ${BackupQuery.SINCE_HELP}.")).let { null }
        }
        val order = if (context.contains("backupSort")) BackupQuery.orderOf(context.get<String>("backupSort")) else BackupOrder.NEWEST
        return BackupQuery(plugin?.lowercase(Locale.ROOT), since, sinceText, order ?: BackupOrder.NEWEST)
    }

    private fun error(text: String): Component = catalogError(text)
}
