package org.pnfolder.pnlibrary.core.runtime

import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.api.audience.hasPermission
import org.pnfolder.pnlibrary.core.exceptions.ResourceCleanup
import org.pnfolder.pnlibrary.core.link.LinkReceiver
import org.pnfolder.pnlibrary.core.link.wire.LinkCodec
import org.pnfolder.pnlibrary.core.menu.OwnerInfo
import org.pnfolder.pnlibrary.core.proxy.ProxyLink
import org.pnfolder.pnlibrary.core.updates.catalog.ProxyUpdateReminder
import org.pnfolder.pnlibrary.spi.platform.ProxyPlatformAdapter
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskHandle
import java.time.Duration

/**
 * Shared runtime logic for all proxy platforms (Velocity, BungeeCord).
 *
 * Uses the platform adapter for scheduling and proxy access, plus two platform-specific
 * hooks — [registerLinkReceiver] and [describe] — that cannot go through the adapter
 * because their types live in core rather than spi.
 */
class ProxyRuntime(
    private val adapter: ProxyPlatformAdapter,
    private val registerLinkReceiver: (LinkReceiver) -> AutoCloseable,
    private val describe: (Any?) -> OwnerInfo?,
) : AutoCloseable {
    private val proxy get() = adapter.networkProxy

    private var link: ProxyLink? = null
    private var linkListener: AutoCloseable? = null
    private var networkTask: PlatformTaskHandle? = null
    private var reminderTask: PlatformTaskHandle? = null

    /** Connects to the game servers and starts the periodic tasks; [close] undoes whatever got started. */
    fun start(host: PnLibraryRuntimeHost) {
        connectLink(host)
        startUpdateReminder(host)
    }

    private fun connectLink(host: PnLibraryRuntimeHost) {
        val proxyLink = createLink(host)
        proxy.registerChannel(LinkCodec.CHANNEL)
        linkListener = registerLinkReceiver(proxyLink.receiver)
        link = proxyLink

        if (!proxyLink.announces) return
        networkTask = adapter.taskAdapter.buildTask()
            .task(proxyLink::announce)
            .delay(Duration.ofSeconds(ProxyLink.NETWORK_DELAY_SECONDS))
            .every(Duration.ofSeconds(ProxyLink.NETWORK_PERIOD_SECONDS))
            .schedule()
    }

    private fun createLink(host: PnLibraryRuntimeHost) = ProxyLink(
        library = host.library,
        folder = adapter.dataFolder,
        warn = adapter::warn,
        transport = { name, bytes -> proxy.server(name)?.send(LinkCodec.CHANNEL, bytes) ?: false },
        describe = describe,
        holds = { viewer, permission -> proxy.player(viewer)?.hasPermission(permission) == true },
        platformName = { "${proxy.name} ${proxy.version}" },
        players = { proxy.playerCount },
        broadcast = { bytes ->
            proxy.servers.forEach { runCatchingNonFatal { it.send(LinkCodec.CHANNEL, bytes) } }
        },
    )

    private fun startUpdateReminder(host: PnLibraryRuntimeHost) {
        val period = Duration.ofSeconds(ProxyUpdateReminder.INTERVAL_SECONDS)
        reminderTask = adapter.taskAdapter.buildTask()
            .task {
                proxy.players
                    .filter { ProxyUpdateReminder.canSee(it::hasPermission) }
                    .forEach { ProxyUpdateReminder.send(host.library, it.uniqueId) }
            }
            .delay(period)
            .every(period)
            .schedule()
    }

    /** Stops scheduled tasks, unregisters the link listener and channel, then closes the link. */
    override fun close() {
        val network = networkTask
        val reminder = reminderTask
        val listener = linkListener
        val proxyLink = link
        networkTask = null
        reminderTask = null
        linkListener = null
        link = null

        ResourceCleanup.closeAll(
            { network?.cancel() },
            { reminder?.cancel() },
            { listener?.close() },
            { proxy.unregisterChannel(LinkCodec.CHANNEL) },
            { proxyLink?.close() },
        )
    }
}
