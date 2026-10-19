package org.pnfolder.pnlibrary.bukkit.commands

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogCommands
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogRoutes

/**
 * Bukkit-only `/pn` behavior expressed through the shared command builder.
 *
 * This class only wires the command tree: the update flow lives in [BukkitUpdateFlow], support helpers in
 * [BukkitSupportCommands], and reply rendering in [BukkitCommandReplies]. The native sender is used only for
 * permissions, identity and player-bound confirmations.
 */
internal class BukkitControlCommand(
    private val plugin: Plugin,
    private val library: PnLibrary,
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
        permission("pnlibrary.admin")
        executes(catalog::showList)
        literal("status") {
            executes { replies.native(it) { sender, reply -> updates.sendStatus(sender, reply, null) } }
            argument("plugin", ArgumentType.string()) {
                suggests { productNames() }
                executes { replies.native(it) { sender, reply -> updates.sendStatus(sender, reply, it.get<String>("plugin")) } }
            }
        }
        literal("updates") {
            executes(catalog::showList)
            catalog.install { name, configure -> literal(name, configure) }
            literal("rollback") {
                permission("pnlibrary.updates.rollback")
                argument("transaction", ArgumentType.string()) {
                    executes { updates.rollbackSaved(it) }
                    argument("confirmation", ArgumentType.string()) { executes { updates.rollbackSaved(it) } }
                    literal("configs") {
                        executes { updates.rollbackSaved(it, true) }
                        argument("confirmation", ArgumentType.string()) { executes { updates.rollbackSaved(it, true) } }
                    }
                }
            }
        }
        literal("check") { executes(updates::checkUpdates) }
        literal("update") {
            argument("plugin", ArgumentType.string()) {
                suggests { productNames() }
                executes { replies.native(it) { sender, reply -> updates.update(sender, reply, it.get<String>("plugin")) } }
            }
        }
        literal("update-confirm") {
            argument("token", ArgumentType.string()) {
                executes { replies.native(it) { sender, reply -> updates.confirmUpdate(sender, reply, it.get<String>("token")) } }
            }
        }
        literal("update-status") {
            executes { replies.native(it) { _, reply -> updates.sendUpdateStatus(reply, null) } }
            argument("plugin", ArgumentType.string()) {
                suggests { productNames() }
                executes { replies.native(it) { _, reply -> updates.sendUpdateStatus(reply, it.get<String>("plugin")) } }
            }
        }
        literal("update-rollback") {
            executes { replies.native(it) { sender, reply -> updates.rollbackUpdate(sender, reply, null) } }
            argument("token", ArgumentType.string()) {
                executes { replies.native(it) { sender, reply -> updates.rollbackUpdate(sender, reply, it.get<String>("token")) } }
            }
        }
        literal("restart") {
            executes { replies.native(it) { sender, reply -> updates.handleRestart(sender, reply, null) } }
            argument("token", ArgumentType.string()) {
                executes { replies.native(it) { sender, reply -> updates.handleRestart(sender, reply, it.get<String>("token")) } }
            }
        }
        literal("debug") {
            executes { replies.native(it) { _, reply -> support.debug(reply) } }
        }
        literal("support") {
            executes { replies.native(it) { _, reply -> support.support(reply) } }
        }
        literal("error") { executes { replies.native(it) { _, reply -> support.error(reply) } } }
        literal("error-repeat") {
            executes { replies.native(it) { _, reply -> support.errorRepeat(reply, null) } }
            argument("count", ArgumentType.integer()) {
                suggests { REPEAT_COUNTS }
                executes { replies.native(it) { _, reply -> support.errorRepeat(reply, it.get<Int>("count")) } }
            }
        }
        literal("error-chain") { executes { replies.native(it) { _, reply -> support.errorChain(reply) } } }
    }

    private fun productNames(): List<String> = library.updates.all().map { it.snapshot.product }

    internal companion object {
        /** Catalog routes of the `/pn` tree, shared with the join notice. */
        val ROUTES = UpdateCatalogRoutes("/pn updates", update = { product -> "/pn update $product" }, check = "/pn check")

        private val REPEAT_COUNTS = listOf("10", "100", "1000")
    }
}
