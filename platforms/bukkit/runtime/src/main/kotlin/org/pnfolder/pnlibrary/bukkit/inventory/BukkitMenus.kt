package org.pnfolder.pnlibrary.bukkit.inventory

import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.util.UUID

internal class BukkitMenus(
    private val host: Plugin,
    private val tasks: TaskScope,
    private val execution: MenuExecution = NativeMenuExecution(host),
    private val inventories: MenuInventories = NativeMenuInventories,
) : MenuService, AutoCloseable {
    private val sessions = MenuSessions()
    private val listener = MenuListener(sessions)

    init {
        host.server.pluginManager.registerEvents(listener, host)
    }

    override fun open(owner: Plugin, player: Player, menu: Menu): MenuSession {
        execution.requireOwner(player)
        check(!sessions.isClosed) { "Menu service is closed" }
        check(owner.isEnabled) { "Plugin ${owner.name} is disabled" }
        sessions.of(player)?.end()
        val session = createSession(owner, player, menu)
        sessions.add(session)
        try {
            show(session)
        } catch (error: Throwable) {
            runCatchingNonFatal { session.discard() }.onFailure(error::addSuppressed)
            throw error
        }
        return session
    }

    override fun session(player: Player): MenuSession? = sessions.of(player)

    override fun close(owner: Plugin) = runEach(sessions.of(owner).map { session -> session::discard })

    override fun close() {
        val open = sessions.shutdown() ?: return
        runEach(open.map { session -> session::discard } + tasks::close + { HandlerList.unregisterAll(listener) })
    }

    private fun createSession(owner: Plugin, player: Player, menu: Menu): BukkitMenuSession {
        val holder = MenuHolder(UUID.randomUUID())
        val inventory = inventories.create(menu, holder)
        holder.bind(inventory)
        menu.items.forEach { (slot, entry) -> inventory.setItem(slot, entry.item?.clone()) }
        return BukkitMenuSession(holder.id, owner, player, menu, inventory, execution, tasks, sessions)
    }

    private fun show(session: BukkitMenuSession) {
        session.player.openInventory(session.inventory)
        if (session.isActive) session.menu.renderer?.render(session)
        if (session.isActive) session.menu.openHandler?.handle(session)
    }
}
