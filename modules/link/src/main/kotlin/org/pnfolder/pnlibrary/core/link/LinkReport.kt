package org.pnfolder.pnlibrary.core.link

import java.time.Instant

/**
 * One plugin as a game server reports it to the proxy.
 *
 * @property latest newest known version, or null when it is the installed one
 * @property state update state word, for example `UP_TO_DATE` or `UPDATE_AVAILABLE`
 */
data class LinkPlugin(val product: String, val version: String, val latest: String?, val state: String)

/**
 * What a game server tells the proxy about itself: the platform, the pnLibrary version, the players and the plugins
 * that use pnLibrary updates. It carries no commands: the link only reports.
 *
 * @property platform server software and version, for example `Paper 1.21.8`
 * @property plugins plugins registered with pnLibrary updates
 */
data class LinkReport(
    val platform: String,
    val libraryVersion: String,
    val players: Int,
    val plugins: List<LinkPlugin>,
    val sentAt: Instant,
)

/**
 * One game server in the map of the network, as the proxy knows it.
 *
 * @property name the name the proxy knows the server under
 * @property online whether the server reported recently
 */
data class NetworkServer(val name: String, val online: Boolean, val report: LinkReport)

/**
 * The map of the network the proxy sends to every game server: its own report and the latest report of each
 * server. A game server shows it in its menu, so any server can be used to look at the whole network.
 *
 * @property servers the game servers that reported to the proxy
 * @property groups the groups of servers kept on the proxy
 */
data class NetworkReport(
    val proxy: LinkReport,
    val servers: List<NetworkServer>,
    val sentAt: Instant,
    val groups: List<ServerGroups.Group> = emptyList(),
)
