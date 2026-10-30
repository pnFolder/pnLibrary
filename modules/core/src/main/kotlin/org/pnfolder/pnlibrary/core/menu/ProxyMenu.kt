package org.pnfolder.pnlibrary.core.menu

import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.core.commands.ProxyCommandNames
import org.pnfolder.pnlibrary.core.link.LinkTransport

/** The way the proxy menu runs chat commands, kept apart so the command package does not depend on the menu. */
object ProxyMenuCommands {
    /** Runs a chat command for a viewer and returns what it printed; set by the runtime on a proxy. */
    @Volatile
    var runner: ((MenuViewer, String) -> List<net.kyori.adventure.text.Component>)? = null
}

/**
 * The menu of a proxy.
 *
 * A proxy has no inventories, so it never shows the menu to anyone itself. A player on a game server opens it from the
 * network menu: the proxy draws the pages with the same [LibraryMenu] as every server and sends them over the link.
 * The chat commands its buttons run go through [ProxyMenuCommands].
 */
object ProxyMenu {
    /**
     * Makes the host that answers viewers of the proxy's menu.
     *
     * @param library the proxy's library
     * @param key the shared link key
     * @param transport sends to game servers by name
     * @param later runs an action on the proxy after a delay in milliseconds
     * @param describe the plugin behind a registration owner, or null
     * @param warn reports a failure of a click
     */
    fun host(
        library: PnLibrary,
        key: ByteArray,
        transport: LinkTransport,
        later: (Long, () -> Unit) -> Unit,
        describe: (Any?) -> OwnerInfo?,
        warn: (String) -> Unit,
    ): RemoteMenuHost {
        val menu = LibraryMenu(library, ProxyMenuHost(later, describe))
        return RemoteMenuHost(
            key,
            home = menu::home,
            send = { bytes, origin -> transport.send(origin, bytes) },
            run = { viewer, command -> ProxyMenuCommands.runner?.invoke(viewer, command).orEmpty() },
            rewrite = ProxyCommandNames::rewrite,
            warn = warn,
        )
    }

    private class ProxyMenuHost(
        private val later: (Long, () -> Unit) -> Unit,
        private val describe: (Any?) -> OwnerInfo?,
    ) : MenuHost {
        override fun sync(action: () -> Unit) = action()

        override fun later(delayMillis: Long, action: () -> Unit) = later.invoke(delayMillis, action)

        override fun owner(owner: Any?): OwnerInfo? = describe(owner)

        override fun ownerNamed(name: String): OwnerInfo? = null

        override fun ownerOf(type: Class<*>?): OwnerInfo? = null

        override fun openRemote(viewer: MenuViewer, target: String): Boolean = false
    }
}
