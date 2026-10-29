package org.pnfolder.pnlibrary.core.updates.catalog

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.chat.ChatNotice
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandNodeBuilder
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.UpdateActor
import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.api.updates.backup.UpdateHistoryEntry
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.common.text.RussianText
import org.pnfolder.pnlibrary.core.updates.engine.FreezeDuration
import java.time.Duration
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

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
    /**
     * A setting that was just saved: [setting] names it, for example "Канал", [from] is what it was and [to] what it
     * is now, both as short words such as "stable" or "включена".
     */
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
        addLiteral("set") {
            managePermission?.let(::permission)
            argument("plugin", ArgumentType.string()) {
                suggests { productNames() }
                literal("channel") {
                    argument("value", ArgumentType.string()) {
                        suggests { available().map { it.name.lowercase(Locale.ROOT) } + DEFAULT_CHANNEL }
                        executes(::setChannel)
                    }
                }
                literal("auto") {
                    argument("value", ArgumentType.string()) {
                        suggests { SWITCH_SUGGESTIONS }
                        executes(::setAutomatic)
                    }
                }
                literal("hold") {
                    argument("value", ArgumentType.string()) {
                        suggests { UpdateCatalogView.HOLD_PERIODS + "off" }
                        executes(::setHeld)
                    }
                }
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

    /** The channels an administrator may choose; only `dev` is published for now, so the others stay switched off. */
    private fun available(): List<UpdateChannel> = if (ONLY_DEV) listOf(UpdateChannel.DEV) else UpdateCatalogView.CHANNELS

    /** Sets the release channel, or removes the administrator's choice with `default` so the plugin decides again. */
    private fun setChannel(context: CommandContext) {
        val value = context.get<String>("value")
        if (value.equals(DEFAULT_CHANNEL, ignoreCase = true)) {
            change(context, "Канал", { it.channel.name.lowercase(Locale.ROOT) }, "как задал плагин") { product, actor ->
                library.updates.setChannel(product, null, actor)
            }
            return
        }
        val channel = UpdateCatalogView.CHANNELS.firstOrNull { it.name.equals(value, ignoreCase = true) }
        if (channel == null) {
            context.sender.send(error("Неизвестный канал $value. Доступны: ${available().joinToString { it.name.lowercase(Locale.ROOT) }}."))
            return
        }
        if (channel !in available()) {
            context.sender.send(error("Сейчас работает только канал dev, остальные пока не выпускаются. Выберите dev или default."))
            return
        }
        change(context, "Канал", { it.channel.name.lowercase(Locale.ROOT) }, channel.name.lowercase(Locale.ROOT)) { product,
            actor -> library.updates.setChannel(product, channel, actor) }
    }

    private fun setAutomatic(context: CommandContext) {
        val enabled = switch(context) ?: return
        change(context, "Автозагрузка", { if (it.automaticDownload) "включена" else "выключена" }, if (enabled) "включена" else "выключена") { product, actor ->
            library.updates.setAutomaticDownload(product, enabled, actor)
        }
    }

    /** Pauses updates for a period such as `3d` (`on` means seven days), or lifts the pause with `off`. */
    private fun setHeld(context: CommandContext) {
        val value = context.get<String>("value").lowercase(Locale.ROOT)
        val duration = when (value) {
            in OFF -> null
            in ON -> DEFAULT_HOLD
            else -> runCatchingNonFatal { FreezeDuration.parse(value) }.getOrElse {
                context.sender.send(error("Пауза — от 1m до 7d (например 3d) или off, получено: $value."))
                return
            }
        }
        change(context, "Пауза", { if (it.state == UpdateState.FROZEN) "на паузе" else "нет" },
            if (duration == null) "нет" else RussianText.period(duration)) { product, actor -> library.updates.setHeld(product, duration, actor) }
    }

    private fun switch(context: CommandContext): Boolean? {
        val value = context.get<String>("value")
        val parsed = when (value.lowercase(Locale.ROOT)) {
            in ON -> true
            in OFF -> false
            else -> null
        }
        if (parsed == null) context.sender.send(error("Укажите on или off, получено: $value."))
        return parsed
    }

    /** Applies a setting, then confirms it below the refreshed settings card, or shows why it was refused. */
    private fun change(context: CommandContext, setting: String, current: (UpdateSnapshot) -> String, value: String, apply: (String,
        UpdateActor) -> CompletionStage<UpdateSnapshot>) {
        if (!ready(context)) return
        val product = context.get<String>("plugin")
        val sender = context.sender
        val before = library.updates.get(product)?.snapshot?.let(current) ?: value
        val actor = UpdateActor(if (sender.isConsole) UpdateActor.Kind.CONSOLE else UpdateActor.Kind.PLAYER, sender.id, sender.name)
        apply(product, actor).whenComplete { snapshot, failure ->
            deliver(Runnable {
                if (failure != null) {
                    val cause = generateSequence(failure) { it.cause }.last()
                    sender.send(error(cause.message ?: "Не удалось изменить настройку."))
                } else {
                    val routes = routesFor(context)
                    val arrow = if (routes.ascii) "->" else "→"
                    val card = UpdateCatalogView.settings(snapshot, routes, library.updates.heldUntil(snapshot.product))
                    (card + UpdateCatalogView.notice(ChatNotice(ChatTone.SUCCESS, "Сохранено. $setting: $before $arrow $value."), routes)).forEach(sender::send)
                    onSaved(context, SettingSaved(snapshot.product, setting, before, value))
                }
            })
        }
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

    private fun showBackups(context: CommandContext) {
        if (!ready(context)) return
        val page = requestedPage(context) ?: return
        library.updates.backups().whenComplete { backups, failure ->
            deliver(Runnable {
                if (failure != null) context.sender.send(error("Не удалось прочитать резервные наборы."))
                else UpdateCatalogView.backups(backups, page, routesFor(context)).forEach(context.sender::send)
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

    private fun error(text: String): Component = Component.text(text, ChatPalette.DANGER)

    private companion object {
        val SWITCH_SUGGESTIONS = listOf("on", "off")
        val ON = setOf("on", "true", "yes", "1", "вкл")
        val OFF = setOf("off", "false", "no", "0", "выкл")
        val DEFAULT_HOLD: Duration = Duration.ofDays(7)
        const val DEFAULT_CHANNEL = "default"

        /** Whether only the `dev` channel can be chosen; set to `false` when more channels are published. */
        const val ONLY_DEV = true
    }
}
