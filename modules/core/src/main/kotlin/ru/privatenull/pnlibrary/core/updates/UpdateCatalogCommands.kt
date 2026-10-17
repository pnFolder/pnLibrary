package ru.privatenull.pnlibrary.core.updates

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import ru.privatenull.pnlibrary.api.commands.ArgumentType
import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.CommandNodeBuilder
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.updates.UpdateHistoryEntry
import java.util.concurrent.CompletableFuture

/**
 * The read-only catalog commands `list`, `available`, `info`, `history` and `backups`, shared by the Bukkit
 * `/pn updates` tree and the console-only `/pnupdate` command.
 *
 * Platforms own what differs: the permissions, who may use the command ([guard]) and which thread results are
 * delivered on ([deliver]). Mutating commands (update, rollback, restart) stay with the platform because their
 * confirmation flows differ.
 *
 * @param viewPermission permission required for list, available, info and history, or null for none
 * @param backupsPermission permission required for backups, which expose rollback identities
 * @param guard returns whether the command may run for this sender; it reports the reason itself
 * @param deliver runs result delivery on the right thread; asynchronous results are never sent directly
 */
class UpdateCatalogCommands(
    private val library: PnLibrary,
    private val routes: UpdateCatalogRoutes,
    private val viewPermission: String? = null,
    private val backupsPermission: String? = viewPermission,
    private val guard: (CommandContext) -> Boolean = { true },
    private val deliver: (Runnable) -> Unit = Runnable::run,
) {
    /**
     * Adds the catalog literals through [addLiteral], which forwards to whichever command or node is being built.
     *
     * ```
     * command("pnupdate") { catalog.install { name, configure -> literal(name, configure) } }
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
            executes(::showBackups)
            argument("page", ArgumentType.integer()) { executes(::showBackups) }
        }
    }

    /** Shows the first page of every registered component; used as the default action of a command. */
    fun showList(context: CommandContext) = showCatalog(context, updatesOnly = false)

    /** Shows the first page of components with an available or prepared update. */
    fun showAvailable(context: CommandContext) = showCatalog(context, updatesOnly = true)

    private fun productNames(): List<String> = library.updates.all().map { it.snapshot.product }

    private fun showCatalog(context: CommandContext, updatesOnly: Boolean) {
        if (!ready(context)) return
        val page = requestedPage(context) ?: return
        UpdateCatalogView.catalog(library.updates.all().map { it.snapshot }, page, updatesOnly, routes)
            .forEach(context.sender::send)
    }

    private fun showInfo(context: CommandContext) {
        if (!ready(context)) return
        val requested = context.get<String>("plugin")
        val registration = library.updates.get(requested)
        if (registration == null) {
            context.sender.send(error("Плагин $requested не зарегистрирован в pnLibrary."))
            return
        }
        UpdateCatalogView.info(registration.snapshot, routes).forEach(context.sender::send)
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
        UpdateCatalogView.history(entries, page, routes, totalPages = pages, product = product)
            .forEach(context.sender::send)
    }

    private fun showBackups(context: CommandContext) {
        if (!ready(context)) return
        val page = requestedPage(context) ?: return
        library.updates.backups().whenComplete { backups, failure ->
            deliver(Runnable {
                if (failure != null) context.sender.send(error("Не удалось прочитать резервные наборы."))
                else UpdateCatalogView.backups(backups, page, routes).forEach(context.sender::send)
            })
        }
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

    private fun error(text: String): Component = Component.text(text, NamedTextColor.RED)
}
