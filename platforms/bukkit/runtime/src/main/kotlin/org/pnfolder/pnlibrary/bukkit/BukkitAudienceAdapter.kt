package org.pnfolder.pnlibrary.bukkit

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.pnfolder.pnlibrary.api.audiences.AudienceSender
import org.pnfolder.pnlibrary.api.audiences.Delivery
import org.pnfolder.pnlibrary.api.audiences.LibraryPlayer
import org.pnfolder.pnlibrary.spi.audiences.PlatformAudienceAdapter
import java.util.UUID

/** Converts native server recipients to portable audiences and player views. */
internal class BukkitAudienceAdapter(
    private val audiences: BukkitAudienceService,
) : PlatformAudienceAdapter {

    /** Returns the server console audience. */
    override fun console(): AudienceSender = wrap(Bukkit.getConsoleSender())

    /** Finds an online player by UUID. */
    override fun player(uniqueId: UUID): LibraryPlayer? = Bukkit.getPlayer(uniqueId)?.let { BukkitLibraryPlayer(it, audiences) }

    /** Wraps native Bukkit senders; unsupported objects return null. */
    override fun sender(native: Any): AudienceSender? = (native as? CommandSender)?.let(::wrap)

    /** Returns portable views of the currently connected players. */
    override fun onlinePlayers(): List<LibraryPlayer> = Bukkit.getOnlinePlayers().map { BukkitLibraryPlayer(it, audiences) }

    /** Uses the player transport where available, otherwise a legacy sender wrapper. */
    private fun wrap(sender: CommandSender): AudienceSender =
        if (sender is Player) BukkitLibraryPlayer(sender, audiences) else BukkitSenderAudience(sender)

    /** Uses chat for generic senders; action bars fall back to chat and sounds are unsupported. */
    private class BukkitSenderAudience(
        private val sender: CommandSender,
    ) : AudienceSender {
        override val id get() = sender.name
        override val name get() = sender.name
        override val isConsole get() = sender is ConsoleCommandSender

        override fun hasPermission(permission: String) = sender.hasPermission(permission)
        override fun sendMessage(text: Component) = sender.sendMessage(LEGACY.serialize(text))
        override fun actionBar(text: Component) = sendMessage(text)
        override fun playSound(sound: Sound) = Delivery.Unsupported
    }

    private companion object {
        private val LEGACY = LegacyComponentSerializer.legacySection()
    }
}
