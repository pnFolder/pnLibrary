package org.pnfolder.pnlibrary.core.link

import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import java.time.Duration
import java.time.Instant

/**
 * The proxy end that builds the signed network map sent to every game server.
 *
 * @param link the proxy's link; it must have a key
 * @param platform names the proxy software and version
 */
class NetworkPublisher(
    private val library: PnLibrary,
    private val link: Link,
    private val platform: () -> String,
    private val players: () -> Int,
) {
    private val key = requireNotNull(link.key) { "The network map is signed with the link key" }

    /** The message to send now; the same bytes go to every server. */
    fun message(now: Instant = Instant.now()): ByteArray {
        val servers = link.registry.all().map {
            NetworkServer(it.name, Duration.between(it.receivedAt, now) < ONLINE, it.report)
        }
        val proxy = LinkReports.build(library, platform(), players(), now)
        return LinkCodec.encodeNetwork(NetworkReport(proxy, servers, now, link.groups().all()), key)
    }

    private companion object {
        val ONLINE: Duration = Duration.ofMinutes(2)
    }
}
