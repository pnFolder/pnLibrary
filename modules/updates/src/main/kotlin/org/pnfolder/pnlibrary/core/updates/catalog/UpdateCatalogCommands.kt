package org.pnfolder.pnlibrary.core.updates.catalog

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandNodeBuilder
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.updates.UpdateActor
import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot
import org.pnfolder.pnlibrary.api.updates.backup.UpdateHistoryEntry
import java.util.Locale
import java.util.concurrent.CompletableFuture

/**
 * The catalog commands `list`, `available`, `info`, `settings`, `set`, `history` and `backups`, shared by the
 * Bukkit `/pn updates` tree and the console-only `/pn update` command of the proxies.
 *
 * Platforms own what differs: the permissions, who may use the command ([guard]) and which thread results are
 * delivered on ([deliver]). The commands that prepare, confirm or restore updates stay with the platform because
 * their confirmation flows differ.
 *
 * Output adapts to the sender: a sender that cannot click gets text without buttons and a list of commands,
 * and the console gets ASCII only.
 *
 * @param viewPermission permission required for list, available, info, settings and history, or null for none
 * @param backupsPermission permission required for backups, which expose rollback identities
 * @param managePermission permission required for `set`, which changes a component's channel, automatic download or pause
 * @param guard returns whether the command may run for this sender; it reports the reason itself
 * @param deliver runs result delivery on the right thread; asynchronous results are never sent directly
 * @param onSaved called on that thread after a setting was saved, so a platform can add its own feedback such as a
 *   title and a sound
 */
class UpdateCatalogCommands(
    private val library: PnLibrary,
    private val routes: UpdateCatalogRoutes,
    private val viewPermission: String? = null,
    private val backupsPermission: String? = viewPermission,
    private val managePermission: String? = viewPermission,
    private val guard: (CommandContext) -> Boolean = { true },
    private val deliver: (Runnable) -> Unit = Runnable::run,
    private val onSaved: (CommandContext, SettingSaved) -> Unit = { _, _ -> },
) {
    private val settings = UpdateCatalogSettingsCommands(library, ::ready, ::routesFor, deliver, onSaved)

    /**
     * A setting that was changed from a player or console command.
     *
     * @property product product whose setting changed
     * @property setting name of the changed setting
     * @property from value before the change
     * @property to value after the change
     */
    data class SettingSaved(val product: String, val setting: String, val from: String, val to: String)

    /**
     * Adds the catalog literals through [addLiteral], which forwards to whichever command or node is being built.
     *
     * ```
     * command("update") { catalog.install { name, configure -> literal(name, configure) } }
     * ```
     */
    fun install(addLiteral: (String, CommandNodeBuilder.() -> Unit) -> Unit) {
        addLiteral("list") {
            viewPermission?.let(::permission)
            executes { showCatalog(it, updatesOnly = false) }
            argument("page", ArgumentType.integer()) { executes { showCatalog(it, updatesOnly = false) } }
        }
        addLiteral("available") {
            viewPermission?.let(::permission)
            executes { showCatalog(it, updatesOnly = true) }
            argument("page", ArgumentType.integer()) { executes { showCatalog(it, updatesOnly = true) } }
        }
        addLiteral("info") {
            viewPermission?.let(::permission)
            argument("plugin", ArgumentType.string()) {
                suggests { productNames() }
                executes(::showInfo)
            }
        }
        addLiteral("settings") {
            viewPermission?.let(::permission)
            argument("plugin", ArgumentType.string()) {
                suggests { productNames() }
                executes { showSettings(it, it.get<String>("plugin")) }
            }
        }
        installSettings(addLiteral)
        installHistory(addLiteral)
    }

    private fun installSettings(addLiteral: (String, CommandNodeBuilder.() -> Unit) -> Unit) {
        addLiteral("set") {
            managePermission?.let(::permission)
            literal("all") {
                literal("auto") {
                    argument("value", ArgumentType.string()) {
                        suggests { UpdateCatalogSettingsCommands.SWITCH_SUGGESTIONS }
                        executes(settings::setAllAutomatic)
                    }
                }
            }
            argument("plugin", ArgumentType.string()) {
                suggests { productNames() }
                literal("channel") {
                    argument("value", ArgumentType.string()) {
                        suggests { settings.available().map { it.name.lowercase(Locale.ROOT) } + UpdateCatalogSettingsCommands.DEFAULT_CHANNEL }
                        executes(settings::setChannel)
                    }
                }
                literal("auto") {
                    argument("value", ArgumentType.string()) {
                        suggests { UpdateCatalogSettingsCommands.SWITCH_SUGGESTIONS }
                        executes(settings::setAutomatic)
                    }
                }
                literal("hold") {
                    argument("value", ArgumentType.string()) {
                        suggests { UpdateCatalogView.HOLD_PERIODS + "off" }
                        executes(settings::setHeld)
                    }
                }
            }
        }
    }

    private fun installHistory(addLiteral: (String, CommandNodeBuilder.() -> Unit) -> Unit) {
        addLiteral("history") {
            viewPermission?.let(::permission)
            executes { showHistory(it, product = null) }
            argument("page", ArgumentType.integer()) { executes { showHistory(it, product = null) } }
            literal("plugin") {
                argument("product", ArgumentType.string()) {
                    suggests { productNames() }
                    executes { showHistory(it, product = it.get<String>("product")) }
                    argument("page", ArgumentType.integer()) {
                        executes { showHistory(it, product = it.get<String>("product")) }
                    }
                }
            }
        }
        addLiteral("backups") {
            (backupsPermission ?: viewPermission)?.let(::permission)
            backupFilters(emptySet())
            literal("restore") {
                managePermission?.let(::permission)
                argument("backupId", ArgumentType.string()) {
                    argument("backupPlugin", ArgumentType.string()) {
                        suggests { productNames() }
                        literal("confirm") { argument("databaseName", ArgumentType.greedyString()) { executes { restoreDatabase(it, true) } } }
                        argument("databaseName", ArgumentType.greedyString()) { executes { restoreDatabase(it, false) } }
                    }
                }
            }
            literal("files") {
                managePermission?.let(::permission)
                argument("backupId", ArgumentType.string()) {
                    argument("backupPlugin", ArgumentType.string()) {
                        suggests { productNames() }
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
                    suggests { productNames() }
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

    /** Shows the first page of every registered component; used as the default action of a command. */
    fun showList(context: CommandContext) = showCatalog(context, updatesOnly = false)

    /** Shows the first page of components with an available or prepared update. */
    fun showAvailable(context: CommandContext) = showCatalog(context, updatesOnly = true)

    private fun productNames(): List<String> = library.updates.all().map { it.snapshot.product }

    /** Routes for this sender: no buttons when it cannot click, ASCII for the console, read-only without permission. */
    private fun routesFor(context: CommandContext): UpdateCatalogRoutes = routes.copy(
        interactive = routes.interactive && context.sender.supportsInteraction,
        ascii = context.sender.isConsole,
        editable = managePermission == null || context.sender.hasPermission(managePermission),
    )

    private fun showCatalog(context: CommandContext, updatesOnly: Boolean) {
        if (!ready(context)) return
        val page = requestedPage(context) ?: return
        UpdateCatalogView.catalog(library.updates.all().map { it.snapshot }, page, updatesOnly, routesFor(context))
            .forEach(context.sender::send)
    }

    private fun showInfo(context: CommandContext) {
        if (!ready(context)) return
        val snapshot = snapshotOf(context, context.get<String>("plugin")) ?: return
        UpdateCatalogView.info(snapshot, routesFor(context), library.updates.heldUntil(snapshot.product)).forEach(context.sender::send)
    }

    private fun showSettings(context: CommandContext, requested: String) {
        if (!ready(context)) return
        val snapshot = snapshotOf(context, requested) ?: return
        UpdateCatalogView.settings(snapshot, routesFor(context), library.updates.heldUntil(snapshot.product)).forEach(context.sender::send)
    }

    private fun snapshotOf(context: CommandContext, requested: String): UpdateSnapshot? {
        val registration = library.updates.get(requested)
        if (registration == null) context.sender.send(error("Плагин $requested не зарегистрирован в pnLibrary."))
        return registration?.snapshot
    }

    private fun showHistory(context: CommandContext, product: String?) {
        if (!ready(context)) return
        val page = requestedPage(context) ?: return
        val entries = library.updates.historyPage(page, product).toCompletableFuture()
        val total = library.updates.historyCount(product).toCompletableFuture()
        CompletableFuture.allOf(entries, total).whenComplete { _, failure ->
            deliver(Runnable {
                if (failure != null) {
                    context.sender.send(error(historyFailure(failure, product)))
                } else {
                    render(context, entries.join(), total.join(), page, product)
                }
            })
        }
    }

    private fun render(context: CommandContext, entries: List<UpdateHistoryEntry>, total: Int, page: Int, product: String?) {
        val pages = maxOf(1, (total + UpdateCatalogView.PAGE_SIZE - 1) / UpdateCatalogView.PAGE_SIZE)
        UpdateCatalogView.history(entries, page, routesFor(context), totalPages = pages, product = product)
            .forEach(context.sender::send)
    }

    /** Without `confirm` it only warns and shows the command that does it: a database cannot be put back by accident. */
    private fun restoreDatabase(context: CommandContext, confirmed: Boolean) {
        if (!ready(context)) return
        val sender = context.sender
        val id = runCatching { java.util.UUID.fromString(context.get<String>("backupId")) }.getOrNull()
            ?: return sender.send(error("Некорректный ID резервного набора."))
        val plugin = context.get<String>("backupPlugin")
        val name = context.get<String>("databaseName")
        if (!confirmed) {
            sender.send(error("База «$name» плагина $plugin будет заменена сохранённой: всё, что записано в неё после копии, пропадёт."))
            sender.send(
                Component.text("Текущее состояние сохранится отдельной копией."),
            )
            sender.send(
                Component.text("Если согласны, выполните: /pn updates backups restore $id $plugin confirm $name"),
            )
            return
        }
        val actor = UpdateActor(if (sender.isConsole) UpdateActor.Kind.CONSOLE else UpdateActor.Kind.PLAYER, sender.id, sender.name)
        library.updates.restoreDatabase(id, plugin, name, actor).whenComplete { _, failure ->
            deliver(Runnable {
                if (failure == null) sender.send(Component.text("База «$name» возвращена. Перезапустите плагин $plugin."))
                else sender.send(error("Не удалось вернуть базу: ${generateSequence(failure) { it.cause }.last().message}"))
            })
        }
    }

    /** Hands out the saved databases and exports of a plugin as plain files, for what the library cannot put back itself. */
    private fun extractBackup(context: CommandContext) {
        if (!ready(context)) return
        val sender = context.sender
        val id = runCatching { java.util.UUID.fromString(context.get<String>("backupId")) }.getOrNull()
            ?: return sender.send(error("Некорректный ID резервного набора."))
        val plugin = context.get<String>("backupPlugin")
        val actor = UpdateActor(if (sender.isConsole) UpdateActor.Kind.CONSOLE else UpdateActor.Kind.PLAYER, sender.id, sender.name)
        library.updates.extractBackup(id, plugin, actor).whenComplete { folder, failure ->
            deliver(Runnable {
                if (failure == null) sender.send(Component.text("Файлы плагина $plugin лежат в папке: $folder"))
                else sender.send(error("Не удалось выдать файлы: ${generateSequence(failure) { it.cause }.last().message}"))
            })
        }
    }

    private fun deleteBackup(context: CommandContext) {
        if (!ready(context)) return
        val sender = context.sender
        val id = runCatching { java.util.UUID.fromString(context.get<String>("backupId")) }.getOrNull()
            ?: return sender.send(error("Некорректный ID резервного набора."))
        val actor = UpdateActor(if (sender.isConsole) UpdateActor.Kind.CONSOLE else UpdateActor.Kind.PLAYER, sender.id, sender.name)
        library.updates.deleteBackup(id, actor).whenComplete { _, failure ->
            deliver(Runnable {
                if (failure == null) sender.send(Component.text("Резервный набор $id удалён."))
                else sender.send(error("Не удалось удалить набор: ${generateSequence(failure) { it.cause }.last().message}"))
            })
        }
    }

    private fun showBackups(context: CommandContext) {
        if (!ready(context)) return
        val page = requestedPage(context) ?: return
        val query = backupQuery(context) ?: return
        library.updates.backups().whenComplete { backups, failure ->
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

    private fun historyFailure(failure: Throwable, product: String?): String {
        val cause = generateSequence(failure) { it.cause }.last()
        return if (product != null && cause is IllegalArgumentException) "Некорректное имя плагина: $product."
        else "Не удалось прочитать историю обновлений."
    }

    private fun requestedPage(context: CommandContext): Int? {
        val page = if (context.contains("page")) context.get<Int>("page") else 1
        if (page >= 1) return page
        context.sender.send(error("Номер страницы должен быть положительным."))
        return null
    }

    private fun ready(context: CommandContext): Boolean {
        if (!guard(context)) return false
        if (!library.isClosed) return true
        context.sender.send(error("pnLibrary не готова. Дождитесь полного запуска сервера."))
        return false
    }

    private fun error(text: String): Component = catalogError(text)
}
