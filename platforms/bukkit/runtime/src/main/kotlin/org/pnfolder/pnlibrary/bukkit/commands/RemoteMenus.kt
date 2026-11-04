package org.pnfolder.pnlibrary.bukkit.commands

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.core.menu.MenuDataServer
import org.pnfolder.pnlibrary.core.menu.ViewerInfo
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit

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
