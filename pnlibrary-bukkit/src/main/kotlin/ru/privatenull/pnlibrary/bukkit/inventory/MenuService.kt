package ru.privatenull.pnlibrary.bukkit.inventory

import org.bukkit.Bukkit
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
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import java.time.Duration
import java.util.IdentityHashMap
import java.util.UUID

/** Opens menus and tracks their owner-bound sessions. */
interface MenuService {
    fun open(owner: Plugin, player: Player, menu: Menu): MenuSession
    fun session(player: Player): MenuSession?
    fun close(owner: Plugin)
}

object PnMenus {
    @JvmStatic fun get(): MenuService = Bukkit.getServicesManager().load(MenuService::class.java)
        ?: error("pnLibrary menu service is unavailable")
}

internal class MenuServiceImpl(
    private val host: Plugin,
    private val tasks: TaskScope,
) : MenuService, Listener, AutoCloseable {
    private val sessions = hashMapOf<java.util.UUID, Session>()
    private val owners = IdentityHashMap<Plugin, MutableSet<Session>>()

    init { host.server.pluginManager.registerEvents(this, host) }

    override fun open(owner: Plugin, player: Player, menu: Menu): MenuSession {
        check(owner.isEnabled) { "Plugin ${owner.name} is disabled" }
        sessions.remove(player.uniqueId)?.finish(false)
        val holder = MenuInventoryHolder(UUID.randomUUID())
        val inventory = create(menu, holder)
        holder.bind(inventory)
        val session = Session(holder.id, owner, player, menu, inventory)
        menu.items.forEach { (slot, entry) -> inventory.setItem(slot, entry.item?.clone()) }
        sessions[player.uniqueId] = session
        owners.getOrPut(owner) { linkedSetOf() }.add(session)
        player.openInventory(inventory)
        menu.renderer?.render(session)
        menu.openHandler?.handle(session)
        return session
    }

    override fun session(player: Player): MenuSession? = sessions[player.uniqueId]
    override fun close(owner: Plugin) = owners.remove(owner)?.toList()?.forEach { it.close() } ?: Unit

    /** Closes every active menu and unregisters this shared listener. */
    override fun close() {
        owners.keys.toList().forEach(::close)
        tasks.close()
        org.bukkit.event.HandlerList.unregisterAll(this)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun click(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = sessions[player.uniqueId] ?: return
        if (!owns(event.view.topInventory, session)) return
        val top = event.rawSlot in 0 until session.inventory.size
        if (!top) {
            if (session.menu.cancelPlayerInventory) event.isCancelled = true
            return
        }
        val definition = session.menu.items[event.rawSlot]
        event.isCancelled = definition?.editable != true
        val click = MenuClick(session, event.rawSlot, event.currentItem,
            event.cursor, event.click, event.action, true, if (session.menu.type == MenuType.ANVIL) renameText(session.inventory) else null)
        definition?.handler?.handle(click)
        session.menu.clickHandler?.handle(click)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun drag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = sessions[player.uniqueId] ?: return
        if (!owns(event.view.topInventory, session)) return
        if (event.rawSlots.any { it < session.inventory.size && session.menu.items[it]?.editable != true }) event.isCancelled = true
    }

    @EventHandler fun close(event: InventoryCloseEvent) {
        val session = sessions[event.player.uniqueId] ?: return
        if (owns(event.inventory, session)) session.finish(true)
    }
    @EventHandler fun quit(event: PlayerQuitEvent) { sessions.remove(event.player.uniqueId)?.finish(true) }
    @EventHandler fun disable(event: PluginDisableEvent) { close(event.plugin) }

    private fun owns(inventory: Inventory, session: Session): Boolean {
        val holder = inventory.holder as? MenuInventoryHolder ?: return false
        return inventory === session.inventory && holder.id == session.id
    }

    private fun create(menu: Menu, holder: InventoryHolder): Inventory {
        if (menu.type == MenuType.CHEST) return Bukkit.createInventory(holder, menu.size, menu.title)
        val type = org.bukkit.event.inventory.InventoryType.valueOf(menu.type.name)
        return Bukkit.createInventory(holder, type, menu.title)
    }

    private fun renameText(inventory: Inventory): String? = try {
        (inventory.javaClass.methods.firstOrNull { it.name == "getRenameText" && it.parameterTypes.isEmpty() }
            ?.invoke(inventory) as? String)?.takeIf { it.isNotBlank() }
            ?: inventory.getItem(2)?.itemMeta?.displayName
            ?: inventory.getItem(0)?.itemMeta?.displayName
    } catch (_: Throwable) { null }

    private inner class Session(
        val id: UUID,
        val owner: Plugin,
        override val player: Player,
        override val menu: Menu,
        override val inventory: Inventory,
    ) : MenuSession {
        private var finished = false
        override fun set(slot: Int, item: ItemStack?) { require(slot in 0 until inventory.size); inventory.setItem(slot, item) }
        override fun get(slot: Int): ItemStack? = inventory.getItem(slot)
        override fun refresh() { if (!finished) menu.renderer?.render(this) }
        override fun refreshAfter(delay: Duration) { tasks.laterEntity(player, delay, Runnable { refresh() }) }
        override fun close() { if (!finished) player.closeInventory() }
        fun finish(callback: Boolean) {
            if (finished) return
            finished = true
            sessions.remove(player.uniqueId, this)
            owners[owner]?.let { it.remove(this); if (it.isEmpty()) owners.remove(owner) }
            if (callback) menu.closeHandler?.handle(MenuClose(this))
        }
    }

    private class MenuInventoryHolder(val id: UUID) : InventoryHolder {
        private lateinit var backing: Inventory
        fun bind(inventory: Inventory) { backing = inventory }
        override fun getInventory(): Inventory = backing
    }
}
