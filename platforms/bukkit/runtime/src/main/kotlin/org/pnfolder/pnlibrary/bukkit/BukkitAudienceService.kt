package org.pnfolder.pnlibrary.bukkit

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.title.Title
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.audiences.Delivery
import org.pnfolder.pnlibrary.api.audiences.LibraryAudience
import org.pnfolder.pnlibrary.api.audiences.LibraryPlayer
import org.pnfolder.pnlibrary.common.lang.Failures
import java.lang.reflect.Array
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lifecycle-owned Adventure transport for Bukkit players.
 *
 * Paper recipients use their native Adventure API directly, retaining click and hover events.
 * Older Bukkit servers use `adventure-platform-bukkit` and fall back to legacy Bukkit message
 * methods when Adventure initialization or delivery fails. Runtime degradation is sticky: after a
 * native transport failure, later messages use the fallback to avoid repeatedly failing calls.
 * Close this service with the owning plugin to release Adventure platform resources.
 */
internal class BukkitAudienceService internal constructor(plugin: Plugin, private val audiences: BukkitAudiences?) : AutoCloseable {
    constructor(plugin: Plugin) : this(plugin, createProvider(plugin))

    private val logger = plugin.logger
    private val pluginServer = plugin.server
    private val fallbackOnly = AtomicBoolean(audiences == null && !NATIVE_ADVENTURE)

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

    /** Delivers a chat component through the available transport. */
    internal fun sendMessage(player: Player, message: Component) {
        send(player, message, false)
    }

    /** Delivers an action bar, with a legacy fallback where necessary. */
    internal fun sendActionBar(player: Player, message: Component) {
        send(player, message, true)
    }

    /** Tries Adventure delivery before switching to the sticky legacy fallback. */
    private fun send(player: Player, message: Component, actionBar: Boolean) {
        val provider = audiences
        if (!fallbackOnly.get() && (player is Audience || provider != null)) {
            try {
                // Paper players are already native audiences. Do not resolve the command's
                // recipient through a separate UUID registry that may silently return empty.
                val recipient = player as? Audience ?: requireNotNull(provider).player(player.uniqueId)
                if (recipient === Audience.empty()) {
                    fallbackOnly.set(true)
                } else {
                    if (actionBar) recipient.sendActionBar(message) else recipient.sendMessage(message)
                    return
                }
            } catch (error: Throwable) {
                Failures.rethrowIfFatal(error)
                // A broken/incompatible platform bridge must not break the calling plugin.
                if (fallbackOnly.compareAndSet(false, true)) {
                    logger.warning("Adventure Bukkit transport failed (${error.javaClass.simpleName}); switched to legacy text fallback")
                }
            }
        }
        val legacy = LEGACY.serialize(message)
        if (actionBar) sendLegacyActionBar(player, legacy) else player.sendMessage(legacy)
    }

    /** Uses optional Spigot action-bar APIs, falling back to ordinary chat. */
    private fun sendLegacyActionBar(player: Player, message: String) {
        runCatching {
            val chatType = Class.forName("net.md_5.bungee.api.ChatMessageType").getField("ACTION_BAR").get(null)
            val componentType = Class.forName("net.md_5.bungee.api.chat.BaseComponent")
            val textComponent = Class.forName("net.md_5.bungee.api.chat.TextComponent")
            val components = textComponent.getMethod("fromLegacyText", String::class.java).invoke(null, message)
            player.javaClass.getMethod("spigot").invoke(player).javaClass
                .getMethod("sendMessage", chatType.javaClass, Array.newInstance(componentType, 0).javaClass)
                .invoke(player.spigot(), chatType, components)
        }.onFailure { player.sendMessage(message) }
    }

    /** Tries Adventure sound delivery, then the native Bukkit sound API. */
    internal fun playSound(player: Player, sound: Sound): Delivery {
        if (player is Audience && !fallbackOnly.get()) {
            try { player.playSound(sound); return Delivery.Delivered } catch (error: Throwable) { Failures.rethrowIfFatal(error) }
        }
        val provider = audiences
        if (provider != null && !fallbackOnly.get()) {
            try {
                provider.player(player.uniqueId).playSound(sound)
                return Delivery.Delivered
            } catch (error: Throwable) {
                Failures.rethrowIfFatal(error)
            }
        }
        return deliver { player.playSound(player.location, sound.name().asString(), sound.volume(), sound.pitch()) }
    }

    /** Shows a title through Adventure when available, otherwise through the legacy two-line API. */
    @Suppress("DEPRECATION")
    internal fun showTitle(player: Player, title: Title): Delivery {
        val provider = audiences
        if (!fallbackOnly.get() && (player is Audience || provider != null)) {
            try {
                val recipient = player as? Audience ?: requireNotNull(provider).player(player.uniqueId)
                recipient.showTitle(title)
                return Delivery.Delivered
            } catch (error: Throwable) {
                Failures.rethrowIfFatal(error)
            }
        }
        return deliver { player.sendTitle(LEGACY.serialize(title.title()), LEGACY.serialize(title.subtitle())) }
    }

    /** Releases the underlying Adventure audience provider when one was created. */
    override fun close() {
        runCatching { audiences?.close() }
    }

    private companion object {
        val LEGACY: LegacyComponentSerializer = LegacyComponentSerializer.legacySection()
        val NATIVE_ADVENTURE = Audience::class.java.isAssignableFrom(Player::class.java)

        fun createProvider(plugin: Plugin): BukkitAudiences? {
            if (NATIVE_ADVENTURE) return null
            return try { BukkitAudiences.create(plugin) } catch (error: Throwable) {
                Failures.rethrowIfFatal(error)
                plugin.logger.warning("Adventure Bukkit transport is unavailable (${error.javaClass.simpleName}); using legacy text fallback")
                null
            }
        }

        /** Runs the last-resort native call, keeping the platform's error instead of a bare failure flag. */
        inline fun deliver(send: () -> Unit): Delivery = try {
            send()
            Delivery.Delivered
        } catch (error: Throwable) {
            Failures.rethrowIfFatal(error)
            Delivery.Failed(error)
        }
    }
}
