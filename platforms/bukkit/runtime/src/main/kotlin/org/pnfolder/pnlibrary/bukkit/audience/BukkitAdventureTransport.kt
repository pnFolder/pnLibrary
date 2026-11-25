package org.pnfolder.pnlibrary.bukkit.audience

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.title.Title
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.audience.delivery.Delivery
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer
import org.pnfolder.pnlibrary.bukkit.PnLoggerBuffer
import org.pnfolder.pnlibrary.bukkit.currency.LEGACY
import org.pnfolder.pnlibrary.bukkit.currency.error
import org.pnfolder.pnlibrary.common.lang.Failures
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.lang.reflect.Array
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lifecycle-owned Adventure transport for Bukkit players.
 *
 * Paper players are native audiences and keep click and hover events. Older servers go through
 * `adventure-platform-bukkit`, and every kind of delivery falls back to the legacy Bukkit API when Adventure
 * is missing or fails. Close it with the owning plugin to release the platform resources.
 */
internal class BukkitAdventureTransport(
    plugin: Plugin,
    private val log: PnLoggerBuffer,
    private val audiences: BukkitAudiences? = createProvider(plugin, log),
) : AutoCloseable {
    private val pluginServer = plugin.server
    private val fallbackOnly = AtomicBoolean(audiences == null && !NATIVE_ADVENTURE)
    private val failedKinds = ConcurrentHashMap.newKeySet<String>()

    /** Whether the native Adventure transport is still available for new deliveries. */
    val nativeTransportAvailable: Boolean get() = !fallbackOnly.get()

    /** Wraps an online Bukkit [player] for the library API. */
    fun player(player: Player): OnlinePlayer =
        BukkitOnlinePlayer(player, this)

    internal fun sendMessage(player: Player, message: Component) {
        if (!viaAdventure(player, "message") { it.sendMessage(message) }) player.sendMessage(LEGACY.serialize(message))
    }

    internal fun sendActionBar(player: Player, message: Component) {
        if (!viaAdventure(player, "action bar") { it.sendActionBar(message) }) sendLegacyActionBar(player, LEGACY.serialize(message))
    }

    /** Tries Adventure sound delivery, then the native Bukkit sound API. */
    internal fun playSound(player: Player, sound: Sound): Delivery {
        if (viaAdventure(player, "sound") { it.playSound(sound) }) return Delivery.delivered()
        return deliver { player.playSound(player.location, sound.name().asString(), sound.volume(), sound.pitch()) }
    }

    /** Shows a title through Adventure when available, otherwise through the legacy two-line API. */
    @Suppress("DEPRECATION")
    internal fun showTitle(player: Player, title: Title): Delivery {
        if (viaAdventure(player, "title") { it.showTitle(title) }) return Delivery.delivered()
        return deliver { player.sendTitle(LEGACY.serialize(title.title()), LEGACY.serialize(title.subtitle())) }
    }

    /**
     * Runs [send] on the Adventure recipient of [player] and says whether it did. A kind of delivery that failed once
     * stays on the legacy path, so a broken bridge is reported once and not on every message.
     */
    private inline fun viaAdventure(player: Player, kind: String, send: (Audience) -> Unit): Boolean {
        if (fallbackOnly.get() || kind in failedKinds) return false
        val recipient = recipient(player) ?: return false
        return try {
            send(recipient)
            true
        } catch (error: Throwable) {
            Failures.rethrowIfFatal(error)
            if (failedKinds.add(kind)) log.log(LogLevel.ERROR, "Adventure $kind delivery failed; using the legacy fallback", error)
            false
        }
    }

    /** Paper players are already audiences; elsewhere the platform provider finds them, and an empty answer means it is broken. */
    private fun recipient(player: Player): Audience? {
        val found = player as? Audience ?: audiences?.player(player.uniqueId)
        if (found != null && found !== Audience.empty()) return found
        fallbackOnly.set(true)
        return null
    }

    private fun sendLegacyActionBar(player: Player, message: String) {
        runCatchingNonFatal {
            val chatType = Class.forName("net.md_5.bungee.api.ChatMessageType").getField("ACTION_BAR").get(null)
            val componentType = Class.forName("net.md_5.bungee.api.chat.BaseComponent")
            val textComponent = Class.forName("net.md_5.bungee.api.chat.TextComponent")
            val components = textComponent.getMethod("fromLegacyText", String::class.java).invoke(null, message)
            player.javaClass.getMethod("spigot").invoke(player).javaClass
                .getMethod("sendMessage", chatType.javaClass, Array.newInstance(componentType, 0).javaClass)
                .invoke(player.spigot(), chatType, components)
        }.onFailure { player.sendMessage(message) }
    }

    /** Releases the underlying Adventure audience provider when one was created. */
    override fun close() {
        runCatchingNonFatal { audiences?.close() }.onFailure { log.log(LogLevel.ERROR, "Could not close the Adventure transport", it) }
    }

    private companion object {
        val LEGACY: LegacyComponentSerializer = LegacyComponentSerializer.legacySection()
        val NATIVE_ADVENTURE = Audience::class.java.isAssignableFrom(Player::class.java)

        fun createProvider(plugin: Plugin, log: PnLoggerBuffer): BukkitAudiences? {
            if (NATIVE_ADVENTURE) return null
            return try { BukkitAudiences.create(plugin) } catch (error: Throwable) {
                Failures.rethrowIfFatal(error)
                log.log(LogLevel.WARNING, "Adventure Bukkit transport is unavailable (${error.javaClass.simpleName}); using legacy text fallback")
                null
            }
        }

        /** Runs the last-resort native call, keeping the platform's error instead of a bare failure flag. */
        inline fun deliver(send: () -> Unit): Delivery = try {
            send()
            Delivery.delivered()
        } catch (error: Throwable) {
            Failures.rethrowIfFatal(error)
            Delivery.failed(error)
        }
    }
}
