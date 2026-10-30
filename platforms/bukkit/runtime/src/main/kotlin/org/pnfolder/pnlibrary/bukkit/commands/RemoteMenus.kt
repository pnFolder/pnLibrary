package org.pnfolder.pnlibrary.bukkit.commands

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import org.pnfolder.pnlibrary.api.platform.platform
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.bukkit.api.BukkitPlatform
import org.pnfolder.pnlibrary.bukkit.inventory.Menu
import org.pnfolder.pnlibrary.bukkit.inventory.MenuClick
import org.pnfolder.pnlibrary.bukkit.inventory.MenuSession
import org.pnfolder.pnlibrary.bukkit.inventory.Menus
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.link.LinkCodec
import org.pnfolder.pnlibrary.core.link.LinkHub
import org.pnfolder.pnlibrary.core.link.ViewAction
import org.pnfolder.pnlibrary.core.link.ViewFrame
import org.pnfolder.pnlibrary.core.link.ViewNotices
import org.pnfolder.pnlibrary.core.link.ViewRequest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The menu of another server of the network, shown as a copy of that server's own menu.
 *
 * The server that owns the menu draws every page itself and sends it as a list of item stacks; the viewer's server
 * puts the page on screen and sends back each click. So the copy is the original, whatever the original shows, and
 * the actions run on the owning server with the permissions the viewer holds at home.
 */
internal object RemoteMenus {
    /** Opens the home page of this server for a player, real or remote; set by the control command. */
    @Volatile
    var home: ((Player) -> Unit)? = null

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

    /** Whether a viewer with [held] permissions may open or use the menu of this server. */
    fun mayView(held: Set<String>): Boolean = "pnlibrary.admin" in held || "pnlibrary.servers" in held

    internal fun serialize(stack: ItemStack): ByteArray {
        val bytes = ByteArrayOutputStream()
        BukkitObjectOutputStream(bytes).use { it.writeObject(stack) }
        return bytes.toByteArray()
    }

    internal fun deserialize(bytes: ByteArray): ItemStack? = runCatchingNonFatal {
        BukkitObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as? ItemStack }
    }.getOrNull()
}

/** A player of another server, as the owning server's menu code sees them. */
internal class RemoteViewer(
    val session: UUID,
    val name: String,
    val origin: String,
    val permissions: Set<String>,
    private val host: RemoteMenuHost,
) : InvocationHandler {
    /** The page the viewer has on screen. */
    @Volatile
    var menu: Menu? = null

    @Volatile
    var touched: Instant = Instant.now()

    /** The stand-in the menu code receives as its `Player`; only the few calls the menu makes are answered. */
    val player: Player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java), this) as Player

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? = when (method.name) {
        "getUniqueId" -> session
        "getName", "getDisplayName" -> name
        "isOnline" -> true
        "hasPermission" -> holds(args?.firstOrNull())
        "closeInventory" -> host.close(this)
        "performCommand" -> host.command(this, args?.firstOrNull() as? String ?: "")
        "equals" -> proxy === args?.firstOrNull()
        "hashCode" -> System.identityHashCode(proxy)
        "toString" -> "RemoteViewer($name@$origin)"
        else -> defaultOf(method.returnType)
    }

    /** Whether the viewer holds [permission], as their own server said. */
    fun holds(permission: Any?): Boolean {
        val node = (permission as? String) ?: permission?.toString() ?: return false
        return node in permissions || "pnlibrary.admin" in permissions && node.startsWith("pnlibrary.")
    }

    companion object {
        /** The viewer behind [player], or null when [player] is an ordinary player. */
        fun of(player: Player): RemoteViewer? =
            if (Proxy.isProxyClass(player.javaClass)) Proxy.getInvocationHandler(player) as? RemoteViewer else null

        private fun defaultOf(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Double.TYPE -> 0.0
            java.lang.Float.TYPE -> 0f
            else -> null
        }
    }
}

/** Draws the menu for viewers on other servers and carries out their clicks. */
internal class RemoteMenuHost(
    private val plugin: Plugin,
    private val library: PnLibrary,
    private val key: ByteArray,
) {
    private val sessions = ConcurrentHashMap<UUID, RemoteViewer>()

    /** Handles a viewer's step; call it on the server thread. */
    fun handle(request: ViewRequest) {
        purge()
        when (request.action) {
            ViewAction.OPEN -> {
                if (!RemoteMenus.mayView(request.permissions)) {
                    return finish(request.session, request.origin, Component.text("Недостаточно прав для меню этого сервера."))
                }
                val viewer = RemoteViewer(request.session, request.viewer, request.origin, request.permissions, this)
                sessions[request.session] = viewer
                runCatchingNonFatal { RemoteMenus.home?.invoke(viewer.player) }.onFailure { error ->
                    finish(request.session, request.origin, Component.text("Не удалось открыть меню: ${error.message ?: error.javaClass.simpleName}"))
                }
            }
            ViewAction.CLICK -> sessions[request.session]?.let { click(it, request) }
                ?: finish(request.session, request.origin, Component.text("Сеанс меню закончился. Откройте его снова."))
            ViewAction.CLOSE -> sessions.remove(request.session)
        }
    }

    /** Shows [menu] to [viewer]: sends the page to the viewer's server. */
    fun render(viewer: RemoteViewer, menu: Menu) {
        viewer.menu = menu
        viewer.touched = Instant.now()
        val items = menu.items.mapNotNull { (slot, entry) -> entry.item?.let { slot to RemoteMenus.serialize(it) } }.toMap()
        send(ViewFrame(viewer.session, viewer.origin, menu.title, menu.rows, items, emptyList(), false, Instant.now()), viewer)
    }

    /** Sends [lines] to the viewer as chat messages. */
    fun notice(viewer: RemoteViewer, lines: List<Component>) {
        if (lines.isEmpty()) return
        send(ViewFrame(viewer.session, viewer.origin, "", 0, emptyMap(), lines.map(ViewNotices::encode), false, Instant.now()), viewer)
    }

    /** Closes the viewer's menu on their server. */
    fun close(viewer: RemoteViewer) {
        sessions.remove(viewer.session)
        send(ViewFrame(viewer.session, viewer.origin, "", 0, emptyMap(), emptyList(), true, Instant.now()), viewer)
    }

    /** Runs [command] for [viewer] with the permissions they hold and sends what it says back to them. */
    fun command(viewer: RemoteViewer, command: String) {
        val lines = mutableListOf<Component>()
        val sender = Proxy.newProxyInstance(
            ConsoleCommandSender::class.java.classLoader,
            arrayOf(ConsoleCommandSender::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getName" -> viewer.name
                "hasPermission" -> viewer.holds(args?.firstOrNull())
                "isPermissionSet", "isOp" -> false
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
        val done = runCatchingNonFatal { Bukkit.dispatchCommand(sender, command) }
        if (done.isFailure) lines += Component.text("Команда не выполнилась: /$command")
        notice(viewer, lines)
    }

    private fun collect(value: Any?, lines: MutableList<Component>) {
        when (value) {
            is Component -> lines += value
            is String -> lines += Component.text(value)
            is Array<*> -> value.forEach { collect(it, lines) }
        }
    }

    private fun click(viewer: RemoteViewer, request: ViewRequest) {
        viewer.touched = Instant.now()
        val menu = viewer.menu ?: return
        val entry = menu.items[request.slot] ?: return
        val type = ClickType.values().firstOrNull { it.name == request.click } ?: ClickType.LEFT
        val handler = entry.handler ?: return
        runCatchingNonFatal {
            handler.handle(MenuClick(RemoteSession(viewer, menu), request.slot, entry.item, null, type, InventoryAction.PICKUP_ALL, true))
        }.onFailure { error ->
            plugin.logger.warning("Remote menu click failed: ${error.message}")
        }
    }

    private fun finish(session: UUID, origin: String, message: Component) {
        val frame = ViewFrame(session, origin, "", 0, emptyMap(), listOf(ViewNotices.encode(message)), true, Instant.now())
        LinkCodec.encodeFrame(frame, key)?.let { LinkHub.uplink?.invoke(it) }
    }

    private fun send(frame: ViewFrame, viewer: RemoteViewer) {
        val message = LinkCodec.encodeFrame(frame, key)
        if (message == null) {
            val text = Component.text("Страница меню слишком большая для передачи.")
            finish(viewer.session, viewer.origin, text)
            return
        }
        LinkHub.uplink?.invoke(message)
    }

    private fun purge() {
        val limit = Instant.now().minus(IDLE)
        sessions.values.removeIf { it.touched.isBefore(limit) }
    }

    /** Stops every session. */
    fun stop() = sessions.clear()

    private class RemoteSession(private val viewer: RemoteViewer, override val menu: Menu) : MenuSession {
        override val player: Player get() = viewer.player
        override val inventory: Inventory get() = throw UnsupportedOperationException("A remote menu has no inventory here.")
        override fun set(slot: Int, item: ItemStack?) = Unit
        override fun get(slot: Int): ItemStack? = menu.items[slot]?.item
        override fun refresh() = Unit
        override fun refreshAfter(delay: Duration) = Unit
        override fun close() = viewer.player.closeInventory()
    }

    private companion object {
        val IDLE: Duration = Duration.ofMinutes(10)
    }
}

/** Shows the menu of another server to a local player and sends their clicks to that server. */
internal class RemoteMenuClient(
    private val plugin: Plugin,
    private val library: PnLibrary,
    private val key: ByteArray,
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
        val builder = Menus.chest(frame.title).rows(frame.rows)
        frame.items.forEach { (slot, bytes) ->
            val stack = RemoteMenus.deserialize(bytes) ?: return@forEach
            if (slot in 0 until frame.rows * 9) {
                builder.button(slot, stack) { click -> send(frame.session, shown, ViewAction.CLICK, slot, click.click.name) }
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
}
