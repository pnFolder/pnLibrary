package org.pnfolder.pnlibrary.bukkit.inventory

import org.bukkit.entity.HumanEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.inventory.Inventory

internal class MenuListener(private val sessions: MenuSessions) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onClick(event: InventoryClickEvent) {
        val session = shown(event.whoClicked, event.view.topInventory) ?: return
        if (!session.owner.isEnabled || sessions.isClosed) {
            event.isCancelled = true
            session.discard()
            return
        }
        if (event.rawSlot !in 0 until session.inventory.size) {
            if (session.menu.cancelPlayerInventory) event.isCancelled = true
            return
        }
        val item = session.menu.items[event.rawSlot]
        event.isCancelled = item?.editable != true
        val renameText = if (session.menu.type == MenuType.ANVIL) session.inventory.anvilRenameText() else null
        val click = MenuClick(session, event.rawSlot, event.currentItem, event.cursor, event.click, event.action, true, renameText)
        item?.handler?.handle(click)
        session.menu.clickHandler?.handle(click)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDrag(event: InventoryDragEvent) {
        val session = shown(event.whoClicked, event.view.topInventory) ?: return
        val size = session.inventory.size
        if (event.rawSlots.any { it < size && session.menu.items[it]?.editable != true }) event.isCancelled = true
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        shown(event.player, event.inventory)?.endAndNotify()
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        sessions.of(event.player)?.endAndNotify()
    }

    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        runEach(sessions.of(event.plugin).map { session -> session::discard })
    }

    private fun shown(viewer: HumanEntity, inventory: Inventory): BukkitMenuSession? {
        val player = viewer as? Player ?: return null
        return sessions.of(player)?.takeIf { it.isShownIn(inventory) }
    }
}
