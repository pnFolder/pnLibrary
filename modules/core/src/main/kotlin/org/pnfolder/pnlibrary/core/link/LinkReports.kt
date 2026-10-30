package org.pnfolder.pnlibrary.core.link

import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import java.time.Instant

/** Builds the report a game server sends to the proxy. */
object LinkReports {
    /** Describes this server from [library]; [platform] names the server software and [players] counts who is online. */
    fun build(library: PnLibrary, platform: String, players: Int, now: Instant = Instant.now()): LinkReport =
        LinkReport(
            platform = platform,
            libraryVersion = library.version.toString(),
            players = players,
            plugins = library.updates.all().map {
                val snapshot = it.snapshot
                val current = snapshot.currentVersion.toString()
                LinkPlugin(snapshot.product, current, snapshot.latestVersion?.toString()?.takeIf { latest -> latest != current }, snapshot.state.name)
            },
            sentAt = now,
        )
}
