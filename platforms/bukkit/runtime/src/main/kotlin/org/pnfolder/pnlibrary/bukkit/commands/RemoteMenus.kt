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
import org.pnfolder.pnlibrary.core.link.LinkCodec
import org.pnfolder.pnlibrary.core.link.LinkHub
import org.pnfolder.pnlibrary.core.link.ViewAction
import org.pnfolder.pnlibrary.core.link.ViewFrame
import org.pnfolder.pnlibrary.core.link.ViewNotices
import org.pnfolder.pnlibrary.core.link.ViewRequest
import org.pnfolder.pnlibrary.core.menu.ClickType
import org.pnfolder.pnlibrary.core.menu.ItemSpec
import org.pnfolder.pnlibrary.core.menu.ItemSpecs
import org.pnfolder.pnlibrary.core.menu.Menu
import org.pnfolder.pnlibrary.core.menu.MenuHost
import org.pnfolder.pnlibrary.core.menu.MenuViewer
import org.pnfolder.pnlibrary.core.menu.OwnerInfo
import org.pnfolder.pnlibrary.core.menu.RemoteMenuHost
import java.lang.reflect.Proxy
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.pnfolder.pnlibrary.bukkit.inventory.Menus as BukkitMenus

/**
 * The menu of another server, shown as a copy of that server's own menu.
 *
 * The server that owns the menu draws every page and sends it as a list of item descriptions; the viewer's server turns
 * them into stacks and sends back each click. So the copy is the original, whatever the original shows, and the actions
 * run on the owning server with the permissions the viewer holds at home. The proxy owns a menu too.
 */
internal object RemoteMenus {
    /** Opens the home page of this server for a viewer; set by the control command. */
    @Volatile
    var home: ((MenuViewer) -> Unit)? = null

    /** The side that draws menus for viewers on other servers, or null while the link is off. */
    @Volatile
    var host: RemoteMenuHost? = null

    /** The side that shows another server's menu to a local player, or null while the link is off. */
    @Volatile
    var client: RemoteMenuClient? = null

    /** Permissions the library checks in its menu; the viewer's server tells the owning server which of them the viewer holds. */
    val PERMISSIONS = listOf(
        "pnlibrary.admin", "pnlibrary.servers", "pnlibrary.updates.view", "pnlibrary.updates.download", "pnlibrary.updates.rollback",
        "pnlibrary.updates.settings", "pnlibrary.updates.restart", "pnlibrary.events.view", "pnlibrary.events.bus", "pnlibrary.tasks",
        "pnlibrary.debug", "pnlibrary.currency.list",
    )

    /** Runs [command] for [viewer] on this server and returns what it printed, for a viewer on another server. */
    fun run(viewer: MenuViewer, command: String): List<Component> {
        val lines = mutableListOf<Component>()
        val sender = Proxy.newProxyInstance(
            ConsoleCommandSender::class.java.classLoader,
            arrayOf(ConsoleCommandSender::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getName" -> viewer.name
                "hasPermission" -> (args?.firstOrNull() as? String)?.let(viewer::hasPermission) ?: false
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
        override fun open(menu: Menu) = open(player, menu)
        override fun close() = player.closeInventory()
        override fun send(lines: List<Component>) {
            library.audiences.player(player.uniqueId)?.let { audience -> lines.forEach(audience::sendMessage) }
        }

        override fun runCommand(command: String) {
            player.closeInventory()
            player.performCommand(command)
        }

        /** The player behind this viewer. */
        val native: Player get() = player
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

    override fun openRemote(viewer: org.pnfolder.pnlibrary.core.menu.MenuViewer, target: String): Boolean {
        val player = adapter.playerOf(viewer) ?: return false
        return RemoteMenus.client?.open(player, target) == true
    }

    private fun info(plugin: Plugin) = OwnerInfo(plugin.name, plugin.description.version, plugin.description.authors)

    private companion object {
        const val MILLIS_PER_TICK = 50L
    }
}

/** Shows the menu of another server to a local player and sends their clicks to that server. */
internal class RemoteMenuClient(
    private val plugin: Plugin,
    private val library: PnLibrary,
    private val key: ByteArray,
    private val adapter: BukkitMenuAdapter,
) {
    private class Shown(val player: Player, val target: String) {
        @Volatile
        var replacing = false
    }

    private val sessions = ConcurrentHashMap<UUID, Shown>()

    /** Opens the home page of [target] for [player]; returns false when the request could not be sent. */
    fun open(player: Player, target: String): Boolean {
        val session = UUID.randomUUID()
        sessions[session] = Shown(player, target)
        val held = RemoteMenus.PERMISSIONS.filter { player.hasPermission(it) }.toSet()
        val request = ViewRequest(session, target, "", ViewAction.OPEN, player.name, held, 0, "", Instant.now())
        val sent = LinkHub.uplink?.invoke(LinkCodec.encodeView(request, key)) == true
        if (!sent) sessions.remove(session)
        return sent
    }

    /** Puts a page or a message from the owning server on screen; call it on the server thread. */
    fun accept(frame: ViewFrame) {
        val shown = sessions[frame.session] ?: return
        val player = shown.player
        if (!player.isOnline) {
            sessions.remove(frame.session)
            return
        }
        if (frame.notices.isNotEmpty()) {
            val audience = library.audiences.player(player.uniqueId)
            frame.notices.mapNotNull(ViewNotices::decode).forEach { audience?.sendMessage(it) }
        }
        if (frame.closed) {
            sessions.remove(frame.session)
            shown.replacing = true
            player.closeInventory()
            shown.replacing = false
            return
        }
        if (frame.rows == 0) return
        val builder = BukkitMenus.chest(frame.title).rows(frame.rows)
        frame.items.forEach { (slot, bytes) ->
            val spec = ItemSpecs.decode(bytes) ?: return@forEach
            if (slot in 0 until frame.rows * SLOTS_PER_ROW) {
                builder.button(slot, adapter.stack(spec)) { click -> send(frame.session, shown, ViewAction.CLICK, slot, click.click.name) }
            }
        }
        builder.onClose { if (!shown.replacing && sessions.remove(frame.session) != null) send(frame.session, shown, ViewAction.CLOSE, 0, "") }
        shown.replacing = true
        try {
            library.platform<BukkitPlatform>().menus.open(plugin, player, builder.build())
        } finally {
            shown.replacing = false
        }
    }

    private fun send(session: UUID, shown: Shown, action: ViewAction, slot: Int, click: String) {
        val request = ViewRequest(session, shown.target, "", action, shown.player.name, emptySet(), slot, click, Instant.now())
        LinkHub.uplink?.invoke(LinkCodec.encodeView(request, key))
    }

    /** Forgets every open copy. */
    fun stop() = sessions.clear()

    private companion object {
        const val SLOTS_PER_ROW = 9
    }
}
