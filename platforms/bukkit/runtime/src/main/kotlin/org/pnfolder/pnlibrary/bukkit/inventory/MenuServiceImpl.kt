@file:Suppress("DEPRECATION")

package org.pnfolder.pnlibrary.bukkit.inventory

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.tasks.TaskSpec
import java.time.Duration
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Runtime implementation of the public Bukkit menu contract. */
internal class MenuServiceImpl(
    private val host: Plugin,
    private val tasks: TaskScope,
    private val execution: MenuExecution = NativeMenuExecution(host),
    private val inventoryFactory: ((Menu, InventoryHolder) -> Inventory)? = null,
) : MenuService, Listener, AutoCloseable {
    private val stateLock = Any()
    private val sessions = hashMapOf<UUID, Session>()
    private val owners = IdentityHashMap<Plugin, MutableSet<Session>>()
    private val closed = AtomicBoolean(false)

    init {
        host.server.pluginManager.registerEvents(this, host)
    }

    /** Opens an owned menu, replacing the player's previous session. */
    override fun open(owner: Plugin, player: Player, menu: Menu): MenuSession {
        execution.requireOwner(player)
        check(!closed.get()) { "Menu service is closed" }
        check(owner.isEnabled) { "Plugin ${owner.name} is disabled" }
        synchronized(stateLock) { sessions[player.uniqueId] }?.finish(false)
        val holder = MenuInventoryHolder(UUID.randomUUID())
        val inventory = create(menu, holder)
        holder.bind(inventory)
        val session = Session(holder.id, owner, player, menu, inventory)
        menu.items.forEach { (slot, entry) -> inventory.setItem(slot, entry.item?.clone()) }
        synchronized(stateLock) {
            check(!closed.get() && owner.isEnabled) { "Menu service or owner is disabled" }
            sessions[player.uniqueId] = session
            owners.getOrPut(owner) { linkedSetOf() }.add(session)
        }
        try {
            player.openInventory(inventory)
            if (!session.finished.get()) menu.renderer?.render(session)
            if (!session.finished.get()) menu.openHandler?.handle(session)
        } catch (error: Throwable) {
            runCatching { session.terminate(closeView = true, notifyOwner = false) }.onFailure(error::addSuppressed)
            throw error
        }
        return session
    }

    /** Returns the player's currently tracked menu session. */
    override fun session(player: Player): MenuSession? = synchronized(stateLock) { sessions[player.uniqueId] }
    override fun close(owner: Plugin) {
        val current = synchronized(stateLock) { owners[owner]?.toList().orEmpty() }
        closeAll(current.map { session -> { session.terminate(closeView = true, notifyOwner = false) } })
    }

    /** Closes every active menu and unregisters this shared listener. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val current = synchronized(stateLock) { sessions.values.toList() }
        closeAll(current.map { session -> { session.terminate(closeView = true, notifyOwner = false) } } +
            listOf({ tasks.close() }, { HandlerList.unregisterAll(this) }))
    }

    /** Dispatches owned menu clicks while protecting non-editable slots. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun click(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = session(player) as? Session ?: return
        if (!owns(event.view.topInventory, session)) return
        if (!session.owner.isEnabled || closed.get()) {
            event.isCancelled = true
            session.terminate(closeView = true, notifyOwner = false)
            return
        }
        val top = event.rawSlot in 0 until session.inventory.size
        if (!top) {
            if (session.menu.cancelPlayerInventory) event.isCancelled = true
            return
        }
        val definition = session.menu.items[event.rawSlot]
        event.isCancelled = definition?.editable != true
        val renameText = if (session.menu.type == MenuType.ANVIL) {
            renameText(session.inventory)
        } else {
            null
        }
        val click = MenuClick(
            session,
            event.rawSlot,
            event.currentItem,
            event.cursor,
            event.click,
            event.action,
            true,
            renameText,
        )
        definition?.handler?.handle(click)
        session.menu.clickHandler?.handle(click)
    }

    /** Prevents dragging into protected menu slots. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun drag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = session(player) as? Session ?: return
        if (!owns(event.view.topInventory, session)) return
        if (event.rawSlots.any { it < session.inventory.size && session.menu.items[it]?.editable != true }) event.isCancelled = true
    }

    @EventHandler
    fun close(event: InventoryCloseEvent) {
        val session = synchronized(stateLock) { sessions[event.player.uniqueId] } ?: return
        if (owns(event.inventory, session)) session.finish(true)
    }
    /** Releases a departing player's menu session. */
    @EventHandler
    fun quit(event: PlayerQuitEvent) {
        (session(event.player) as? Session)?.finish(true)
    }

    /** Closes menus belonging to a disabled plugin. */
    @EventHandler
    fun disable(event: PluginDisableEvent) {
        close(event.plugin)
    }

    /** Checks both inventory identity and its unique menu holder. */
    private fun owns(inventory: Inventory, session: Session): Boolean {
        val holder = inventory.holder as? MenuInventoryHolder ?: return false
        return inventory === session.inventory && holder.id == session.id
    }

    /** Creates the native inventory matching the requested menu type. */
    private fun create(menu: Menu, holder: InventoryHolder): Inventory {
        inventoryFactory?.let { return it(menu, holder) }
        if (menu.type == MenuType.CHEST) return Bukkit.createInventory(holder, menu.size, menu.title)
        val type = InventoryType.valueOf(menu.type.name)
        return Bukkit.createInventory(holder, type, menu.title)
    }

    private fun closeAll(actions: List<() -> Unit>) {
        val failures = mutableListOf<Throwable>()
        actions.forEach { action -> runCatching(action).onFailure(failures::add) }
        failures.firstOrNull()?.let { first -> failures.drop(1).forEach(first::addSuppressed); throw first }
    }

    /** Reads anvil rename text through compatible APIs or item-name fallbacks. */
    private fun renameText(inventory: Inventory): String? = try {
        (inventory.javaClass.methods.firstOrNull { it.name == "getRenameText" && it.parameterTypes.isEmpty() }
            ?.invoke(inventory) as? String)?.takeIf { it.isNotBlank() }
            ?: inventory.getItem(2)?.itemMeta?.displayName
            ?: inventory.getItem(0)?.itemMeta?.displayName
    } catch (_: Throwable) {
        null
    }

    /** Owns one player's menu inventory and its lifecycle callbacks. */
    private inner class Session(
        val id: UUID,
        val owner: Plugin,
        override val player: Player,
        override val menu: Menu,
        override val inventory: Inventory,
    ) : MenuSession {
        val finished = AtomicBoolean(false)
        /** Updates a validated inventory slot. */
        override fun set(slot: Int, item: ItemStack?) {
            execution.requireOwner(player)
            check(!finished.get()) { "Menu session is closed" }
            require(slot in 0 until inventory.size)
            inventory.setItem(slot, item)
        }
        /** Reads the item in the requested slot. */
        override fun get(slot: Int): ItemStack? {
            execution.requireOwner(player)
            check(!finished.get()) { "Menu session is closed" }
            return inventory.getItem(slot)
        }
        /** Invokes the menu renderer while the session remains active. */
        override fun refresh() {
            execution.requireOwner(player)
            if (!finished.get()) menu.renderer?.render(this)
        }

        /** Schedules a refresh on the player's execution context. */
        override fun refreshAfter(delay: Duration) {
            if (finished.get()) return
            tasks.schedule(TaskSpec.builder()
                .execution(TaskExecution.entity(player))
                .delay(delay)
                .action { refresh() }
                .build())
        }

        override fun close() {
            if (finished.get()) return
            // A disabled owner can no longer run scheduled native work; drop tracking so the session cannot linger.
            if (!owner.isEnabled) { finish(false); return }
            execution.execute(player) {
                if (!finished.get()) {
                    try { if (owns(player.openInventory.topInventory, this)) player.closeInventory() }
                    finally { finish(true) }
                }
            }
        }

        /** Finishes the session and optionally closes its native view. */
        fun terminate(closeView: Boolean, notifyOwner: Boolean) {
            if (!finish(notifyOwner)) return
            if (closeView) execution.execute(player) {
                if (owns(player.openInventory.topInventory, this)) player.closeInventory()
            }
        }

        /** Removes session tracking and optionally notifies the owning plugin. */
        fun finish(callback: Boolean): Boolean {
            if (!finished.compareAndSet(false, true)) return false
            synchronized(stateLock) {
                sessions.remove(player.uniqueId, this)
                owners[owner]?.let { owned ->
                    owned.remove(this)
                    if (owned.isEmpty()) owners.remove(owner)
                }
            }
            if (callback && owner.isEnabled && !closed.get()) menu.closeHandler?.handle(MenuClose(this))
            return true
        }
    }

    /** Identifies a pnLibrary inventory without relying on its title. */
    private class MenuInventoryHolder(val id: UUID) : InventoryHolder {
        private lateinit var backing: Inventory
        fun bind(inventory: Inventory) {
            backing = inventory
        }

        override fun getInventory(): Inventory = backing
    }
}
