package org.pnfolder.pnlibrary.core.link

import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import java.time.Duration
import java.time.Instant

/**
 * The proxy end that builds the signed network map sent to every game server.
 *
 * @param key the shared key
 * @param platform names the proxy software and version
 * @param players counts the players on the proxy
 */
class NetworkPublisher(
    private val library: PnLibrary,
    private val key: ByteArray,
    private val platform: () -> String,
    private val players: () -> Int,
    private val registry: LinkRegistry = LinkHub.registry,
    private val groups: () -> List<ServerGroups.Group> = { emptyList() },
) {
    /** The message to send now; the same bytes go to every server. */
    fun message(now: Instant = Instant.now()): ByteArray {
        val servers = registry.all().map {
            NetworkServer(it.name, Duration.between(it.receivedAt, now) < ONLINE, it.report)
        }
        val proxy = LinkReports.build(library, platform(), players(), now)
        return LinkCodec.encodeNetwork(NetworkReport(proxy, servers, now, groups()), key)
    }

    private companion object {
        val ONLINE: Duration = Duration.ofMinutes(2)
    }
}
