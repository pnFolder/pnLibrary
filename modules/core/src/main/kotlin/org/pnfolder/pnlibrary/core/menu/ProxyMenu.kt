package org.pnfolder.pnlibrary.core.menu

import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.core.commands.proxy.ProxyCommandNames
import org.pnfolder.pnlibrary.core.commands.proxy.ProxyControlCommand
import org.pnfolder.pnlibrary.core.link.Link
import java.util.UUID
import java.util.concurrent.Executors

/** Every permission that opens a page of the menu. */
object MenuPermissions {
    val ALL = listOf(
        "pnlibrary.admin", "pnlibrary.servers", "pnlibrary.updates.view", "pnlibrary.updates.download", "pnlibrary.updates.rollback",
        "pnlibrary.updates.settings", "pnlibrary.updates.restart", "pnlibrary.events.view", "pnlibrary.events.bus", "pnlibrary.tasks",
        "pnlibrary.stats", "pnlibrary.placeholders", "pnlibrary.debug", "pnlibrary.currency.list",
    )
}

/**
 * The questions about a proxy's library that game servers ask.
 *
 * A proxy has no inventories and builds no menu. The server a player stands on builds the menu of the proxy from these
 * answers and asks the proxy to run the commands behind its buttons.
 */
object ProxyMenu {
    /**
     * Makes the handler that answers game servers.
     *
     * @param library the proxy's library
     * @param link the proxy's link, which keeps the server groups
     * @param control the proxy's `/pn`, which runs the chat commands behind the buttons
     * @param describe the plugin behind a registration owner, or null
     */
    fun server(library: PnLibrary, link: Link, control: ProxyControlCommand, describe: (Any?) -> OwnerInfo?): MenuDataServer {
        val directory = object : PluginDirectory {
            override fun owner(owner: Any?): OwnerInfo? = describe(owner)

            override fun ownerNamed(name: String): OwnerInfo? =
                library.plugins.all().mapNotNull { describe(it.owner) }.firstOrNull { it.name.equals(name, ignoreCase = true) }

            override fun ownerOf(type: Class<*>?): OwnerInfo? = null
        }
        val run = { viewer: ViewerInfo, line: String ->
            val viewerId = UUID.nameUUIDFromBytes(viewer.name.toByteArray())
            control.run(viewerId, viewer.name, viewer.permissions::contains, line).map(ProxyCommandNames::rewrite)
        }
        val executor = Executors.newCachedThreadPool { task -> Thread(task, "pnlibrary-menu-data").apply { isDaemon = true } }
        return MenuDataServer(LocalMenuData(library, directory, run, link.groups()), executor)
    }
}
