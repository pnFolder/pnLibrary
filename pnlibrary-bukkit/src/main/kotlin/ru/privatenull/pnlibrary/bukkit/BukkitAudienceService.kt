package ru.privatenull.pnlibrary.bukkit

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.actions.LibraryAudience
import ru.privatenull.pnlibrary.api.actions.LibraryPlayer
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lifecycle-owned Adventure transport for Bukkit players.
 *
 * The service prefers `adventure-platform-bukkit` and falls back to legacy Bukkit/Bungee message
 * methods when Adventure initialization or delivery fails. Runtime degradation is sticky: after a
 * native transport failure, later messages use the fallback to avoid repeatedly failing calls.
 * Close this service with the owning plugin to release Adventure platform resources.
 */
class BukkitAudienceService(plugin: Plugin) : AutoCloseable {
    private val logger = plugin.logger
    private val audiences = try {
        BukkitAudiences.create(plugin)
    } catch (error: Throwable) {
        rethrowFatal(error)
        logger.warning("Adventure Bukkit transport is unavailable (${error.javaClass.simpleName}); using legacy text fallback")
        null
    }
    private val fallbackOnly = AtomicBoolean(audiences == null)

    /** Whether the native Adventure transport is still available for new deliveries. */
    val nativeTransportAvailable: Boolean get() = !fallbackOnly.get()

    /** Creates a platform-neutral view of the currently supplied Bukkit [player]. */
    fun player(player: Player): LibraryPlayer =
        BukkitLibraryPlayer(player, this)

    /** Returns a wrapper for the online player with [playerId], or `null` when not online. */
    fun player(playerId: UUID): LibraryPlayer? {
        val player = pluginServer.getPlayer(playerId) ?: return null
        return player(player)
    }

    /**
     * Creates a dynamic audience containing all players online at delivery time.
     *
     * The returned object does not retain a snapshot. Every message, action bar, or sound operation
     * asks the server for its current online-player collection.
     */
    fun onlinePlayers(): LibraryAudience = LibraryAudience.dynamic {
        pluginServer.onlinePlayers.map(::player)
    }

    private val pluginServer = plugin.server

    internal fun sendMessage(player: Player, message: Component) {
        send(player, message, false)
    }

    internal fun sendActionBar(player: Player, message: Component) {
        send(player, message, true)
    }

    private fun send(player: Player, message: Component, actionBar: Boolean) {
        val provider = audiences
        if (provider != null && !fallbackOnly.get()) {
            try {
                if (actionBar) provider.player(player.uniqueId).sendActionBar(message)
                else provider.player(player.uniqueId).sendMessage(message)
                return
            } catch (error: Throwable) {
                rethrowFatal(error)
                // A broken/incompatible platform bridge must not break the calling plugin.
                if (fallbackOnly.compareAndSet(false, true)) {
                    logger.warning("Adventure Bukkit transport failed (${error.javaClass.simpleName}); switched to legacy text fallback")
                }
            }
        }
        val legacy = LEGACY.serialize(message)
        if (actionBar) sendLegacyActionBar(player, legacy) else player.sendMessage(legacy)
    }

    private fun sendLegacyActionBar(player: Player, message: String) {
        runCatching {
            val chatType = Class.forName("net.md_5.bungee.api.ChatMessageType").getField("ACTION_BAR").get(null)
            val componentType = Class.forName("net.md_5.bungee.api.chat.BaseComponent")
            val textComponent = Class.forName("net.md_5.bungee.api.chat.TextComponent")
            val components = textComponent.getMethod("fromLegacyText", String::class.java).invoke(null, message)
            player.javaClass.getMethod("spigot").invoke(player).javaClass
                .getMethod("sendMessage", chatType.javaClass, java.lang.reflect.Array.newInstance(componentType, 0).javaClass)
                .invoke(player.spigot(), chatType, components)
        }.onFailure { player.sendMessage(message) }
    }

    internal fun playSound(player: Player, sound: Sound): Boolean {
        val provider = audiences
        if (provider != null && !fallbackOnly.get()) {
            try {
                provider.player(player.uniqueId).playSound(sound)
                return true
            } catch (error: Throwable) {
                rethrowFatal(error)
            }
        }
        return runCatching {
            player.playSound(player.location, sound.name().asString(), sound.volume(), sound.pitch())
        }.isSuccess
    }

    /** Releases the underlying Adventure audience provider when one was created. */
    override fun close() {
        runCatching { audiences?.close() }
    }

    private companion object {
        val LEGACY: LegacyComponentSerializer = LegacyComponentSerializer.legacySection()

        fun rethrowFatal(error: Throwable) {
            if (error is Error) throw error
        }
    }
}
