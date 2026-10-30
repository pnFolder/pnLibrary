package org.pnfolder.pnlibrary.core.link

import java.time.Instant

/**
 * One plugin as a game server reports it to the proxy.
 *
 * @property product plugin name
 * @property version installed version
 * @property latest newest known version, or null when it is the installed one
 * @property state update state word, for example `UP_TO_DATE` or `UPDATE_AVAILABLE`
 */
data class LinkPlugin(val product: String, val version: String, val latest: String?, val state: String)

/**
 * What a game server tells the proxy about itself: the platform, the pnLibrary version, the players and the plugins
 * that use pnLibrary updates. It carries no commands: the link only reports.
 *
 * @property platform server software and version, for example `Paper 1.21.8`
 * @property libraryVersion pnLibrary version on that server
 * @property players number of players online
 * @property plugins plugins registered with pnLibrary updates
 * @property sentAt when the server built the report
 */
data class LinkReport(
    val platform: String,
    val libraryVersion: String,
    val players: Int,
    val plugins: List<LinkPlugin>,
    val sentAt: Instant,
)
