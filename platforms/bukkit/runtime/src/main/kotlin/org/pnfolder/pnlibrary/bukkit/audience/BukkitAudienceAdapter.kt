package org.pnfolder.pnlibrary.bukkit.audience

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer
import org.pnfolder.pnlibrary.api.audience.senderPointers
import org.pnfolder.pnlibrary.bukkit.currency.LEGACY
import org.pnfolder.pnlibrary.api.audience.Console
import org.pnfolder.pnlibrary.spi.audience.PlatformAudienceAdapter
import org.pnfolder.pnlibrary.spi.audience.PlatformConsole
import java.util.UUID

/** Converts native server recipients to portable audiences and player views. */
internal class BukkitAudienceAdapter(
    private val audiences: BukkitAdventureTransport,
) : PlatformAudienceAdapter {

    override fun console(): Console = PlatformConsole(wrap(Bukkit.getConsoleSender()))

    override fun player(uniqueId: UUID): OnlinePlayer? = Bukkit.getPlayer(uniqueId)?.let { BukkitOnlinePlayer(it, audiences) }

    override fun of(native: Any): Audience? = (native as? CommandSender)?.let(::wrap)

    override fun players(): List<OnlinePlayer> = Bukkit.getOnlinePlayers().map { BukkitOnlinePlayer(it, audiences) }

    private fun wrap(sender: CommandSender): Audience =
        if (sender is Player) BukkitOnlinePlayer(sender, audiences) else BukkitSenderAudience(sender)

    /** Uses chat for generic senders; action bars fall back to chat and sounds are dropped. */
    private class BukkitSenderAudience(
        private val sender: CommandSender,
    ) : Audience {
        override fun pointers() = senderPointers(sender.name, permission = sender::hasPermission)

        override fun sendMessage(message: Component) = sender.sendMessage(LEGACY.serialize(message))

        override fun sendActionBar(message: Component) = sendMessage(message)
    }

    private companion object {
        private val LEGACY = LegacyComponentSerializer.legacySection()
    }
}
