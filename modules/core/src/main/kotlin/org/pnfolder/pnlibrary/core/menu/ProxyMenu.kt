package org.pnfolder.pnlibrary.core.menu

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.core.commands.ProxyCommandNames
import java.util.concurrent.Executors

/** The way a proxy runs a chat command for a viewer on another server, kept apart so the command package does not depend on the menu. */
object ProxyMenuCommands {
    /** Runs a chat command for a viewer and returns what it printed; set by the runtime on a proxy. */
    @Volatile
    var runner: ((ViewerInfo, String) -> List<Component>)? = null
}

/** The permissions the library checks in its menu; the viewer's server tells the server it asks which of them the viewer holds. */
object MenuPermissions {
    /** Every permission the menu uses. */
    val ALL = listOf(
        "pnlibrary.admin", "pnlibrary.servers", "pnlibrary.updates.view", "pnlibrary.updates.download", "pnlibrary.updates.rollback",
        "pnlibrary.updates.settings", "pnlibrary.updates.restart", "pnlibrary.events.view", "pnlibrary.events.bus", "pnlibrary.tasks",
        "pnlibrary.debug", "pnlibrary.currency.list",
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
     * @param describe the plugin behind a registration owner, or null
     */
    fun server(library: PnLibrary, describe: (Any?) -> OwnerInfo?): MenuDataServer {
        val directory = object : PluginDirectory {
            override fun owner(owner: Any?): OwnerInfo? = describe(owner)

            override fun ownerNamed(name: String): OwnerInfo? =
                library.plugins.all().mapNotNull { describe(it.owner) }.firstOrNull { it.name.equals(name, ignoreCase = true) }

            override fun ownerOf(type: Class<*>?): OwnerInfo? = null
        }
        val run = { viewer: ViewerInfo, line: String ->
            ProxyMenuCommands.runner?.invoke(viewer, line).orEmpty().map(ProxyCommandNames::rewrite)
        }
        val executor = Executors.newCachedThreadPool { task -> Thread(task, "pnlibrary-menu-data").apply { isDaemon = true } }
        return MenuDataServer(LocalMenuData(library, directory, run), executor)
    }
}
