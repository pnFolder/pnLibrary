package ru.privatenull.pnlibrary.bukkit

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.sound.Sound
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.actions.Action
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Lifecycle-owned Adventure bridge that selects the correct Bukkit/Paper facet at runtime. */
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

    /** Current transport can degrade at runtime without disabling the plugin. */
    val nativeTransportAvailable: Boolean get() = !fallbackOnly.get()

    fun player(player: Player): Action.LibraryPlayer =
        BukkitLibraryPlayer(player, this)

    fun player(playerId: UUID): Action.LibraryPlayer? {
        val player = pluginServer.getPlayer(playerId) ?: return null
        return player(player)
    }

    /** Resolves the current online-player collection whenever an action sends. */
    fun onlinePlayers(): Action.LibraryAudience = Action.LibraryAudience.dynamic {
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

    override fun close() {
        runCatching { audiences?.close() }
    }

    private companion object {
        val LEGACY: LegacyComponentSerializer = LegacyComponentSerializer.legacySection()

        fun rethrowFatal(error: Throwable) {
            if (error is VirtualMachineError || error is ThreadDeath) throw error
        }
    }
}
