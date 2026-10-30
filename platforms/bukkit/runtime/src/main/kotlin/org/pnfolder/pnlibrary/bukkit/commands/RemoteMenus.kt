package org.pnfolder.pnlibrary.bukkit.commands

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.platform.platform
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.updates.UpdateActor
import org.pnfolder.pnlibrary.bukkit.api.BukkitPlatform
import org.pnfolder.pnlibrary.core.link.LinkHub
import org.pnfolder.pnlibrary.core.menu.ClickType
import org.pnfolder.pnlibrary.core.menu.ItemSpec
import org.pnfolder.pnlibrary.core.menu.LibraryMenu
import org.pnfolder.pnlibrary.core.menu.Menu
import org.pnfolder.pnlibrary.core.menu.MenuDataServer
import org.pnfolder.pnlibrary.core.menu.MenuHost
import org.pnfolder.pnlibrary.core.menu.MenuViewer
import org.pnfolder.pnlibrary.core.menu.OwnerInfo
import org.pnfolder.pnlibrary.core.menu.RemoteMenuData
import org.pnfolder.pnlibrary.core.menu.ViewerInfo
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import org.pnfolder.pnlibrary.bukkit.inventory.Menus as BukkitMenus

/**
 * What a Bukkit server answers when another server asks about its library.
 *
 * The menu of another server is built here, by the server the player stands on, from the answers of that server. This
 * object holds the part that answers: the handler of the incoming questions and the way a chat command runs for a
 * viewer from another server.
 */
internal object RemoteMenus {
    /** Answers the questions other servers ask of this one, or null while the link is off. */
    @Volatile
    var data: MenuDataServer? = null

    /** Runs [command] for [viewer] on this server, on the server thread, and returns what it printed. */
    fun run(plugin: Plugin, viewer: ViewerInfo, command: String): List<Component> =
        if (Bukkit.isPrimaryThread()) execute(viewer, command)
        else plugin.server.scheduler.callSyncMethod(plugin) { execute(viewer, command) }.get(RUN_SECONDS, TimeUnit.SECONDS)

    private const val RUN_SECONDS = 15L

    private fun holds(viewer: ViewerInfo, permission: String): Boolean =
        permission in viewer.permissions || "pnlibrary.admin" in viewer.permissions && permission.startsWith("pnlibrary.")

    private fun execute(viewer: ViewerInfo, command: String): List<Component> {
        val lines = mutableListOf<Component>()
        val sender = Proxy.newProxyInstance(
            ConsoleCommandSender::class.java.classLoader,
            arrayOf(ConsoleCommandSender::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getName" -> viewer.name
                "hasPermission" -> (args?.firstOrNull() as? String)?.let { holds(viewer, it) } ?: false
                "sendMessage", "sendRawMessage" -> {
                    args?.forEach { collect(it, lines) }
                    null
                }
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "RemoteSender(${viewer.name})"
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    else -> null
                }
            }
        } as CommandSender
        if (!Bukkit.dispatchCommand(sender, command)) lines += Component.text("Команда не найдена: /$command")
        return lines
    }

    private fun collect(value: Any?, lines: MutableList<Component>) {
        when (value) {
            is Component -> lines += value
            is String -> lines += Component.text(value)
            is Array<*> -> value.forEach { collect(it, lines) }
        }
    }
}

/** Turns the portable menu into Bukkit inventories. */
internal class BukkitMenuAdapter(private val plugin: Plugin, private val library: PnLibrary) {
    /** The viewer for [player] on this server. */
    fun viewer(player: Player): MenuViewer = BukkitViewer(player)

    /** The stack for [spec]: the head when the server can make one, otherwise the ordinary item. */
    fun stack(spec: ItemSpec): ItemStack {
        val name = MenuHeads.downgrade(spec.name)
        val lore = spec.lore.map(MenuHeads::downgrade)
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
                    handler.handle(org.pnfolder.pnlibrary.core.menu.MenuClick(viewer(click.player), slot, kind(click.click)))
                }
            }
        }
        return builder.build()
    }

    /** Opens [menu] for [player] with the quiet chest sound of the menu. */
    fun open(player: Player, menu: Menu) {
        library.platform<BukkitPlatform>().menus.open(plugin, player, page(menu))
        OPEN_SOUND?.let { player.playSound(player.location, it, SOUND_VOLUME, SOUND_PITCH) }
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
        val LEGACY_GLASS = setOf("STAINED_GLASS_PANE", "LEGACY_STAINED_GLASS_PANE")
        const val SOUND_VOLUME = 0.22f
        const val SOUND_PITCH = 1.15f
        val OPEN_SOUND: org.bukkit.Sound? = listOf("BLOCK_BARREL_OPEN", "BLOCK_CHEST_OPEN", "CHEST_OPEN")
            .firstNotNullOfOrNull { name -> org.bukkit.Sound.values().firstOrNull { it.name == name } }
    }
}

/** What the portable menu needs from a Bukkit server. */
internal class BukkitMenuHost(
    private val plugin: Plugin,
    private val library: PnLibrary,
    private val replies: BukkitCommandReplies,
    private val adapter: BukkitMenuAdapter,
) : MenuHost {
    override fun sync(action: () -> Unit) = replies.onServerThread(action)

    override fun async(action: () -> Unit) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable { if (!library.isClosed) action() })
    }

    override fun later(delayMillis: Long, action: () -> Unit) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable { if (!library.isClosed) action() }, (delayMillis / MILLIS_PER_TICK).coerceAtLeast(1))
    }

    override fun owner(owner: Any?): OwnerInfo? = (owner as? Plugin)?.let(::info)

    override fun ownerNamed(name: String): OwnerInfo? =
        (
            runCatching { plugin.server.pluginManager.getPlugin(name) }.getOrNull()
                ?: library.plugins.all().mapNotNull { it.owner as? Plugin }.firstOrNull { it.javaClass.simpleName == name || it.name.equals(name, true) }
            )?.let(::info)

    override fun ownerOf(type: Class<*>?): OwnerInfo? {
        val loader = type?.classLoader ?: return null
        return library.plugins.all().mapNotNull { it.owner as? Plugin }.firstOrNull { it.javaClass.classLoader === loader }?.let(::info)
    }

    override fun openRemote(viewer: MenuViewer, target: String): Boolean {
        if (LinkHub.key == null) return false
        remotes.computeIfAbsent(target.lowercase()) { LibraryMenu(RemoteMenuData(target), this, target) }.open(viewer)
        return true
    }

    private val remotes = ConcurrentHashMap<String, LibraryMenu>()

    private fun info(plugin: Plugin) = OwnerInfo(plugin.name, plugin.description.version, plugin.description.authors)

    private companion object {
        const val MILLIS_PER_TICK = 50L
    }
}
