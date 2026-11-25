package org.pnfolder.pnlibrary.velocity.network

import java.time.Duration
import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.pnfolder.pnlibrary.api.network.NetworkPlayer
import org.pnfolder.pnlibrary.api.network.NetworkServer
import org.pnfolder.pnlibrary.api.audience.playerPointers
import java.net.InetSocketAddress
import java.util.UUID

internal class VelocityNetworkPlayer(
    internal val native: Player,
) : NetworkPlayer {

    override val uniqueId: UUID get() = native.uniqueId
    override val address: InetSocketAddress get() = native.remoteAddress
    override val ping: Duration get() = Duration.ofMillis(native.ping)
    override val currentServer: NetworkServer?
        get() = native.currentServer.orElse(null)?.server?.let(::VelocityNetworkServer)

    override fun pointers() = playerPointers(
        name = { native.username },
        uniqueId = native.uniqueId,
        displayName = { Component.text(native.username) },
        locale = { native.effectiveLocale },
        permission = native::hasPermission,
    )
    override fun sendMessage(message: Component) = native.sendMessage(message)
    override fun sendActionBar(message: Component) = native.sendActionBar(message)
    override fun playSound(sound: Sound) = native.playSound(sound)
    override fun showTitle(title: Title) = native.showTitle(title)

    override fun disconnect(reason: Component) = native.disconnect(reason)

    companion object {
        fun of(player: Player): NetworkPlayer = VelocityNetworkPlayer(player)
    }
}
