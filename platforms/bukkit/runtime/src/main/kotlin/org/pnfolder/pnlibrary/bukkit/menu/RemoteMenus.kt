package org.pnfolder.pnlibrary.bukkit.menu

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.menu.ViewerInfo
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Runs a chat command for a viewer from another server: the buttons of a remote menu end up here.
 *
 * The command runs as a console that holds only the viewer's permissions and collects what it prints.
 */
internal object RemoteMenus {
    /** Runs [command] for [viewer] on this server, on the server thread, and returns what it printed. */
    fun run(scheduler: ServerScheduler, viewer: ViewerInfo, command: String): List<Component> {
        val printed = CompletableFuture<List<Component>>()
        scheduler.execute {
            runCatchingNonFatal { execute(viewer, command) }.fold(printed::complete, printed::completeExceptionally)
        }
        return printed.get(RUN_SECONDS, TimeUnit.SECONDS)
    }

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
