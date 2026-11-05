package org.pnfolder.pnlibrary.bukkit.menu

import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandReplies
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler
import org.pnfolder.pnlibrary.core.link.Link
import org.pnfolder.pnlibrary.core.menu.LibraryMenu
import org.pnfolder.pnlibrary.core.menu.MenuHost
import org.pnfolder.pnlibrary.core.menu.MenuViewer
import org.pnfolder.pnlibrary.core.menu.OwnerInfo
import org.pnfolder.pnlibrary.core.menu.RemoteMenuData
import java.util.concurrent.ConcurrentHashMap

/** What the portable menu needs from a Bukkit server. */
internal class BukkitMenuHost(
    private val plugin: Plugin,
    private val library: PnLibrary,
    private val replies: BukkitCommandReplies,
    private val adapter: BukkitMenuAdapter,
    override val link: Link,
    private val scheduler: ServerScheduler,
) : MenuHost {
    override fun sync(action: () -> Unit) = replies.onServerThread(action)

    override fun async(action: () -> Unit) {
        scheduler.async { if (!library.isClosed) action() }
    }

    override fun later(delayMillis: Long, action: () -> Unit) {
        scheduler.later((delayMillis / MILLIS_PER_TICK).coerceAtLeast(1)) { if (!library.isClosed) action() }
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
        if (!link.enabled) return false
        remotes.computeIfAbsent(target.lowercase()) { LibraryMenu(RemoteMenuData(target, link.data), this, target) }.open(viewer)
        return true
    }

    private val remotes = ConcurrentHashMap<String, LibraryMenu>()

    private fun info(plugin: Plugin) = OwnerInfo(plugin.name, plugin.description.version, plugin.description.authors)

    private companion object {
        const val MILLIS_PER_TICK = 50L
    }
}
