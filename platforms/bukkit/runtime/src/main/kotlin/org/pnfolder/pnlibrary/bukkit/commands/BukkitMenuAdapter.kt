package org.pnfolder.pnlibrary.bukkit.commands

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatNotice
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.platform.platform
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.TaskSpec
import org.pnfolder.pnlibrary.api.updates.UpdateActor
import org.pnfolder.pnlibrary.bukkit.api.BukkitPlatform
import org.pnfolder.pnlibrary.bukkit.inventory.MenuSession
import org.pnfolder.pnlibrary.core.lifecycle.runtimeTaskScope
import org.pnfolder.pnlibrary.core.menu.ClickType
import org.pnfolder.pnlibrary.core.menu.ItemSpec
import org.pnfolder.pnlibrary.core.menu.Menu
import org.pnfolder.pnlibrary.core.menu.MenuViewer
import java.time.Duration
import java.util.UUID
import org.pnfolder.pnlibrary.bukkit.inventory.Menus as BukkitMenus

/** Turns the portable menu into Bukkit inventories. */
internal class BukkitMenuAdapter(private val plugin: Plugin, private val library: PnLibrary) {
    /** The viewer for [player] on this server. */
    fun viewer(player: Player): MenuViewer = BukkitViewer(player)

    /** The stack for [spec]: the head when the server can make one, otherwise the ordinary item. */
    fun stack(spec: ItemSpec): ItemStack {
        val name = HexColors.downgrade(spec.name)
        val lore = spec.lore.map(HexColors::downgrade)
        val head = spec.texture?.let { MenuHeads.head(it) }
        val base = head ?: ItemStack(listOfNotNull(spec.material, spec.alternative).firstNotNullOfOrNull(::resolve) ?: Material.PAPER)
        if (head == null && spec.legacyData != 0.toShort() && base.type.name in LEGACY_GLASS) base.durability = spec.legacyData
        return base.also {
            it.itemMeta?.let { meta ->
                meta.setDisplayName(name)
                meta.lore = lore
                ItemFlag.values().forEach { flag -> meta.addItemFlags(flag) }
                it.itemMeta = meta
            }
        }
    }

    /** The inventory page for [menu]; a click goes to its handler as a click by the local player. */
    fun page(menu: Menu): org.pnfolder.pnlibrary.bukkit.inventory.Menu {
        val builder = BukkitMenus.chest(menu.title).rows(menu.rows)
        menu.items.forEach { (slot, entry) ->
            val item = entry.item?.let(::stack)
            val handler = entry.handler
            if (handler == null) {
                builder.slot(slot, item)
            } else if (item != null) {
                builder.button(slot, item) { click ->
                    if (mayUse(click.player)) {
                        handler.handle(org.pnfolder.pnlibrary.core.menu.MenuClick(viewer(click.player), slot, kind(click.click)))
                    } else {
                        revoke(click.player, click.session)
                    }
                }
            }
        }
        return builder.build()
    }

    /** Opens [menu] for [player] with the quiet chest sound of the menu. */
    fun open(player: Player, menu: Menu) {
        val session = library.platform<BukkitPlatform>().menus.open(plugin, player, page(menu))
        watch(player, session)
        OPEN_SOUND?.let { player.playSound(player.location, it, SOUND_VOLUME, SOUND_PITCH) }
    }

    /** Whether [player] may still use the menu: the permissions `/pn gui` itself asks for. */
    private fun mayUse(player: Player): Boolean = MENU_ACCESS.any(player::hasPermission)

    /**
     * Rechecks the right to the menu while [session] is open, so a player who loses it (for example when a
     * permission plugin takes the group away) is not left with a working menu. The task ends with the session.
     */
    private fun watch(player: Player, session: MenuSession) {
        val menus = library.platform<BukkitPlatform>().menus
        library.runtimeTaskScope().schedule(
            TaskSpec.builder()
                .execution(TaskExecution.entity(player))
                .delay(RECHECK)
                .interval(RECHECK)
                .cancellable(false)
                .hidden(true)
                .cancelWhen { !player.isOnline || menus.session(player) !== session }
                .action { if (!mayUse(player)) revoke(player, session) }
                .build(),
        )
    }

    /** Closes [session] because [player] no longer holds the right to the menu, and says so. */
    private fun revoke(player: Player, session: MenuSession) {
        session.close()
        val notice = ChatNotice(ChatTone.FAILURE, "Меню закрыто: не хватает права.", MENU_ACCESS.first())
        library.audiences.player(player.uniqueId)?.let { audience -> ChatKit(ChatReader.PLAYER).notice(notice).forEach(audience::sendMessage) }
    }

    private fun kind(click: org.bukkit.event.inventory.ClickType): ClickType = ClickType.values().firstOrNull { it.name == click.name } ?: ClickType.OTHER

    private fun resolve(name: String): Material? =
        runCatching { Material.getMaterial(name) }.getOrNull()
            ?: Material.matchMaterial(name)
            ?: runCatching { Material.getMaterial("LEGACY_$name") }.getOrNull()

    private inner class BukkitViewer(private val player: Player) : MenuViewer {
        override val id: UUID get() = player.uniqueId
        override val name: String get() = player.name
        override val online: Boolean get() = player.isOnline
        override val remote: Boolean get() = false
        override val actor: UpdateActor get() = UpdateActor(UpdateActor.Kind.PLAYER, player.uniqueId.toString(), player.name)

        override fun hasPermission(permission: String): Boolean = player.hasPermission(permission)
        override fun open(menu: Menu) = onServerThread { open(player, menu) }
        override fun close() = onServerThread { player.closeInventory() }
        override fun send(lines: List<Component>) {
            library.audiences.player(player.uniqueId)?.let { audience -> lines.forEach(audience::sendMessage) }
        }

        override fun runCommand(command: String) = onServerThread {
            player.closeInventory()
            player.performCommand(command)
        }

        /** The player behind this viewer. */
        val native: Player get() = player
    }

    private fun onServerThread(action: () -> Unit) {
        if (Bukkit.isPrimaryThread()) action() else plugin.server.scheduler.runTask(plugin, Runnable { if (!library.isClosed) action() })
    }

    /** The player behind [viewer], or null when it is not a local one. */
    fun playerOf(viewer: MenuViewer): Player? = (viewer as? BukkitViewer)?.native

    private companion object {
        /** Any of these opens `/pn gui`; losing all of them closes the menu. */
        val MENU_ACCESS = listOf("pnlibrary.admin", "pnlibrary.updates.view")
        val RECHECK: Duration = Duration.ofSeconds(2)
        val LEGACY_GLASS = setOf("STAINED_GLASS_PANE", "LEGACY_STAINED_GLASS_PANE")
        const val SOUND_VOLUME = 0.22f
        const val SOUND_PITCH = 1.15f
        val OPEN_SOUND: org.bukkit.Sound? = listOf("BLOCK_BARREL_OPEN", "BLOCK_CHEST_OPEN", "CHEST_OPEN")
            .firstNotNullOfOrNull { name -> org.bukkit.Sound.values().firstOrNull { it.name == name } }
    }
}
