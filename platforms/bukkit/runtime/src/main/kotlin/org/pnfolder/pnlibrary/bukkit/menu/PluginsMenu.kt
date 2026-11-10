package org.pnfolder.pnlibrary.bukkit.menu

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandReplies
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler
import org.pnfolder.pnlibrary.core.link.Link
import org.pnfolder.pnlibrary.core.menu.LibraryMenu
import org.pnfolder.pnlibrary.core.menu.LocalMenuData
import org.pnfolder.pnlibrary.core.menu.MenuDataServer
import org.pnfolder.pnlibrary.core.menu.MenuViewer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.bukkit.command.CommandSender as NativeSender
import org.pnfolder.pnlibrary.api.commands.CommandSender as Reply

/**
 * The inventory menu of `/pn gui` on a Bukkit server.
 *
 * The screens themselves are [LibraryMenu], which every platform shares; this class only opens them for a player and
 * lets the menu reach what the server provides.
 */
internal class PluginsMenu(
    plugin: Plugin,
    library: PnLibrary,
    private val replies: BukkitCommandReplies,
    link: Link,
    scheduler: ServerScheduler,
) : AutoCloseable {
    private var menuData: ExecutorService? = null
    private val adapter = BukkitMenuAdapter(plugin, library, scheduler)
    private val host = BukkitMenuHost(plugin, library, replies, adapter, link, scheduler)
    private val data = LocalMenuData(library, host, run = { viewer, line -> RemoteMenus.run(scheduler, viewer, line) })
    private val menu = LibraryMenu(data, host)

    init {
        // Only a server with a link is asked menu questions by other servers; without one no thread pool is needed.
        if (link.enabled) {
            val executor = Executors.newCachedThreadPool { task -> Thread(task, "pnlibrary-menu-data").apply { isDaemon = true } }
            menuData = executor
            link.answerCallsWith(MenuDataServer(data, executor) { viewer, permission -> Bukkit.getPlayerExact(viewer)?.hasPermission(permission) == true })
        }
    }

    /** Stops the threads that answer menu questions from other servers. */
    override fun close() {
        menuData?.shutdown()
    }

    /** Opens the menu for [sender], or explains that the console has no inventory. */
    fun openFor(sender: NativeSender, reply: Reply) {
        if (sender is Player) menu.home(adapter.viewer(sender))
        else replies.say(reply, ChatTone.INFO, "Меню доступно только игроку.", "В консоли используйте /pn updates.")
    }

    fun home(viewer: MenuViewer) = menu.home(viewer)
}
