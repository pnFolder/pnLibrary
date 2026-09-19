package ru.privatenull.pnlibrary.bukkit

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import ru.privatenull.pnlibrary.api.audiences.AudienceSender
import ru.privatenull.pnlibrary.api.actions.LibraryPlayer
import ru.privatenull.pnlibrary.spi.audiences.PlatformAudienceAdapter
import java.util.UUID

internal class BukkitAudienceAdapter(
    private val audiences: BukkitAudienceService,
) : PlatformAudienceAdapter {
    override fun console(): AudienceSender = wrap(Bukkit.getConsoleSender())
    override fun player(uniqueId: UUID): LibraryPlayer? = Bukkit.getPlayer(uniqueId)?.let { BukkitLibraryPlayer(it, audiences) }
    override fun sender(native: Any): AudienceSender? = (native as? CommandSender)?.let(::wrap)
    override fun onlinePlayers(): List<LibraryPlayer> = Bukkit.getOnlinePlayers().map { BukkitLibraryPlayer(it, audiences) }

    private fun wrap(sender: CommandSender): AudienceSender =
        if (sender is Player) BukkitLibraryPlayer(sender, audiences) else BukkitSenderAudience(sender)

    private class BukkitSenderAudience(private val sender: CommandSender) : AudienceSender {
        override val id get() = sender.name
        override val name get() = sender.name
        override val isConsole get() = sender is ConsoleCommandSender
        override fun hasPermission(permission: String) = sender.hasPermission(permission)
        override fun sendMessage(text: Component) = sender.sendMessage(LEGACY.serialize(text))
        override fun actionBar(text: Component) = sendMessage(text)
        override fun playSound(sound: Sound) = false
    }

    private companion object { val LEGACY = LegacyComponentSerializer.legacySection() }
}
