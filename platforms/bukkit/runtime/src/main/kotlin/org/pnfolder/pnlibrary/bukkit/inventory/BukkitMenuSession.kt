package org.pnfolder.pnlibrary.bukkit.inventory

import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.tasks.TaskSpec
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** One menu open for one player; ends when the player closes it, leaves or the owner plugin is disabled. */
internal class BukkitMenuSession(
    val id: UUID,
    val owner: Plugin,
    override val player: Player,
    override val menu: Menu,
    override val inventory: Inventory,
    private val execution: MenuExecution,
    private val tasks: TaskScope,
    private val sessions: MenuSessions,
) : MenuSession {
    private val ended = AtomicBoolean(false)

    val isActive: Boolean get() = !ended.get()

    override fun set(slot: Int, item: ItemStack?) {
        requireUsable()
        require(slot in 0 until inventory.size)
        inventory.setItem(slot, item)
    }

    override fun get(slot: Int): ItemStack? {
        requireUsable()
        return inventory.getItem(slot)
    }

    override fun refresh() {
        execution.requireOwner(player)
        if (isActive) menu.renderer?.render(this)
    }

    override fun refreshAfter(delay: Duration) {
        if (!isActive) return
        tasks.schedule(
            TaskSpec.builder()
                .execution(TaskExecution.entity(player))
                .delay(delay)
                .action { refresh() }
                .build(),
        )
    }

    override fun close() {
        if (!isActive) return
        // A disabled owner can no longer run scheduled native work, so only tracking is dropped.
        if (!owner.isEnabled) {
            end()
            return
        }
        execution.execute(player) {
            if (isActive) {
                try {
                    closeViewIfShown()
                } finally {
                    endAndNotify()
                }
            }
        }
    }

    /** Whether [inventory] is the inventory this session displays. */
    fun isShownIn(inventory: Inventory): Boolean {
        val holder = inventory.holder as? MenuHolder ?: return false
        return inventory === this.inventory && holder.id == id
    }

    /** Stops tracking without notifying the owner. Returns `false` if the session had already ended. */
    fun end(): Boolean {
        if (!ended.compareAndSet(false, true)) return false
        sessions.remove(this)
        return true
    }

    /** Ends the session after the player left the menu and tells the owner. */
    fun endAndNotify() {
        if (end() && owner.isEnabled && !sessions.isClosed) menu.closeHandler?.handle(MenuClose(this))
    }

    /** Ends the session without notifying the owner and closes the player's view. */
    fun discard() {
        if (end()) execution.execute(player, ::closeViewIfShown)
    }

    private fun closeViewIfShown() {
        if (isShownIn(player.openInventory.topInventory)) player.closeInventory()
    }

    private fun requireUsable() {
        execution.requireOwner(player)
        check(isActive) { "Menu session is closed" }
    }
}
