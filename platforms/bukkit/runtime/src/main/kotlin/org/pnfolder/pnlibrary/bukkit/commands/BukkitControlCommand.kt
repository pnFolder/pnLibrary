package org.pnfolder.pnlibrary.bukkit.commands

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.bukkit.currency.CurrencyCommandExecutor
import org.pnfolder.pnlibrary.core.commands.EventBusCommand
import org.pnfolder.pnlibrary.core.lifecycle.mountableCommand
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogCommands
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogRoutes

/**
 * The one Bukkit command of pnLibrary, `/pn`, expressed through the shared command builder.
 *
 * Everything an administrator or a player types hangs under it:
 *
 * | Command | What it does | Permission |
 * |---|---|---|
 * | `/pn`, `status`, `check`, `updates …` | the list of plugins, their state, the update catalog | `pnlibrary.admin` |
 * | `/pn update <plugin>` | stages the update of one plugin | `pnlibrary.admin` |
 * | `/pn update rollback`, `restart` | asks to roll back or to restart; answers with a token | `pnlibrary.admin` |
 * | `/pn update confirm <token>` | carries out what the token was issued for | `pnlibrary.admin` |
 * | `/pn update status [plugin]` | the state of the update plan | `pnlibrary.admin` |
 * | `/pn events …` | the journal of events | `pnlibrary.events.view` |
 * | `/pn bus …` | listeners, calls and traces of the event bus | `pnlibrary.events.bus` |
 * | `/pn debug …` | the encrypted report for support | `pnlibrary.debug` |
 * | `/pn money …` | currencies and balances | as in the currency settings |
 * | `/pn support`, `error …` | the support link, test errors | `pnlibrary.admin` |
 *
 * The root itself has no permission, so a moderator who may only read events can still enter `/pn events`.
 * The update flow lives in [BukkitUpdateFlow], support helpers in [BukkitSupportCommands], and reply rendering in
 * [BukkitCommandReplies]. The native sender is used only for permissions, identity and player-bound confirmations.
 */
internal class BukkitControlCommand(
    private val plugin: Plugin,
    private val library: PnLibrary,
    private val currencies: CurrencyProviderRegistry,
) {
    private val feedback = BukkitSettingFeedback(plugin)
    private val replies = BukkitCommandReplies(plugin, library)
    private val catalog = UpdateCatalogCommands(
        library = library,
        routes = ROUTES,
        viewPermission = "pnlibrary.updates.view",
        backupsPermission = "pnlibrary.updates.rollback",
        managePermission = "pnlibrary.updates.settings",
        deliver = { task -> if (!library.isClosed) plugin.server.scheduler.runTask(plugin, task) },
        onSaved = { context, saved -> (context.sender as? BukkitCommandSender)?.native?.let { it as? Player }?.let { feedback.show(it, saved) } },
    )
    private val updates = BukkitUpdateFlow(plugin, library, catalog, replies)
    private val support = BukkitSupportCommands(plugin, library, replies)

    /** Builds the permission-aware control command tree. */
    fun definition(): CommandDefinition = command("pn") {
        // Other labels for the same command: they keep it reachable when another plugin already owns `/pn`.
        aliases("pnlib", "pnlibrary")
        executes(::overview)
        literal("status") {
            permission(ADMIN)
            executes { replies.native(it) { sender, reply -> updates.sendStatus(sender, reply, null) } }
            argument("plugin", ArgumentType.string()) {
                suggests { productNames() }
                executes { replies.native(it) { sender, reply -> updates.sendStatus(sender, reply, it.get<String>("plugin")) } }
            }
        }
        literal("check") {
            permission(ADMIN)
            executes(updates::checkUpdates)
        }
        literal("updates") {
            permission(ADMIN)
            executes(catalog::showList)
            catalog.install { name, configure -> literal(name, configure) }
            literal("rollback") {
                permission("pnlibrary.updates.rollback")
                argument("transaction", ArgumentType.string()) {
                    executes { updates.rollbackSaved(it) }
                    literal("configs") { executes { updates.rollbackSaved(it, true) } }
                }
            }
        }
        // Words after `update` are subcommands first and a plugin name second, so no plugin may be named like one.
        literal("update") {
            permission(ADMIN)
            argument("plugin", ArgumentType.string()) {
                suggests { productNames() }
                executes { replies.native(it) { sender, reply -> updates.update(sender, reply, it.get<String>("plugin")) } }
            }
            literal("confirm") {
                argument("token", ArgumentType.string()) {
                    executes { replies.native(it) { sender, reply -> updates.confirm(sender, reply, it.get<String>("token")) } }
                }
            }
            literal("status") {
                executes { replies.native(it) { _, reply -> updates.sendUpdateStatus(reply, null) } }
                argument("plugin", ArgumentType.string()) {
                    suggests { productNames() }
                    executes { replies.native(it) { _, reply -> updates.sendUpdateStatus(reply, it.get<String>("plugin")) } }
                }
            }
            literal("rollback") {
                executes { replies.native(it) { sender, reply -> updates.rollbackUpdate(sender, reply) } }
            }
            literal("restart") {
                executes { replies.native(it) { sender, reply -> updates.restartUpdate(sender, reply) } }
            }
        }
        library.mountableCommand("events")?.let { mount("events", it) }
        library.mountableCommand("debug")?.let { mount("debug", it) }
        mount("bus", EventBusCommand(library).definition())
        mount("money", CurrencyCommandExecutor(plugin, currencies).definition())
        literal("support") {
            permission(ADMIN)
            executes { replies.native(it) { _, reply -> support.support(reply) } }
        }
        literal("error") {
            permission(ADMIN)
            executes { replies.native(it) { _, reply -> support.error(reply) } }
        }
        literal("error-repeat") {
            permission(ADMIN)
            executes { replies.native(it) { _, reply -> support.errorRepeat(reply, null) } }
            argument("count", ArgumentType.integer()) {
                suggests { REPEAT_COUNTS }
                executes { replies.native(it) { _, reply -> support.errorRepeat(reply, it.get<Int>("count")) } }
            }
        }
        literal("error-chain") {
            permission(ADMIN)
            executes { replies.native(it) { _, reply -> support.errorChain(reply) } }
        }
    }

    /** `/pn` alone: the list of plugins for an administrator, otherwise the commands the sender may use. */
    private fun overview(context: CommandContext) {
        val sender = context.sender
        if (sender.hasPermission(ADMIN)) return catalog.showList(context)
        val available = buildList {
            if (sender.hasPermission("pnlibrary.events.view")) add("/pn events")
            if (sender.hasPermission("pnlibrary.events.bus")) add("/pn bus")
            if (sender.hasPermission("pnlibrary.debug")) add("/pn debug")
            add("/pn money")
        }
        replies.say(sender, ChatTone.INFO, "Команды pnLibrary, доступные вам:", available.joinToString("  "))
    }

    private fun productNames(): List<String> = library.updates.all().map { it.snapshot.product }

    internal companion object {
        /** Catalog routes of the `/pn` tree, shared with the join notice. */
        val ROUTES = UpdateCatalogRoutes("/pn updates", update = { product -> "/pn update $product" }, check = "/pn check")

        /** The permission of the control tree: updates, support helpers and the status pages. */
        const val ADMIN = "pnlibrary.admin"

        /** The command a confirmation card offers to carry out what [token] was issued for. */
        fun confirmCommand(token: String): String = "/pn update confirm $token"

        private val REPEAT_COUNTS = listOf("10", "100", "1000")
    }
}
