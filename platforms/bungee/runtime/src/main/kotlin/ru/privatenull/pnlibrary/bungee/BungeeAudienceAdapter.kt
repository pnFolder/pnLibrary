package ru.privatenull.pnlibrary.bungee

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.md_5.bungee.api.CommandSender
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.connection.ProxiedPlayer
import net.md_5.bungee.api.plugin.Plugin
import ru.privatenull.pnlibrary.api.audiences.AudienceSender
import ru.privatenull.pnlibrary.api.actions.LibraryPlayer
import ru.privatenull.pnlibrary.spi.audiences.PlatformAudienceAdapter
import java.util.UUID

@Suppress("DEPRECATION")
internal class BungeeAudienceAdapter(private val plugin: Plugin) : PlatformAudienceAdapter {
    override fun console(): AudienceSender = wrap(plugin.proxy.console)
    override fun player(uniqueId: UUID): LibraryPlayer? = plugin.proxy.getPlayer(uniqueId)?.let(BungeeLibraryPlayer::of)
    override fun sender(native: Any): AudienceSender? = when (native) {
        is ProxiedPlayer -> BungeeLibraryPlayer.of(native)
        is CommandSender -> wrap(native)
        else -> null
    }
    override fun onlinePlayers(): List<LibraryPlayer> = plugin.proxy.players.map(BungeeLibraryPlayer::of)

    private fun wrap(sender: CommandSender) = object : AudienceSender {
        override val id get() = sender.name
        override val name get() = sender.name
        override val isConsole get() = sender === plugin.proxy.console
        override fun hasPermission(permission: String) = sender.hasPermission(permission)
        override fun sendMessage(text: Component) = sender.sendMessage(*TextComponent.fromLegacyText(LEGACY.serialize(text)))
        override fun actionBar(text: Component) = sendMessage(text)
        override fun playSound(sound: Sound) = false
    }

    private companion object { val LEGACY = LegacyComponentSerializer.legacySection() }
}
