package ru.privatenull.pnlibrary.bukkit

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
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

    private val pluginServer = plugin.server

    internal fun sendMessage(player: Player, message: Component) {
        val provider = audiences
        if (provider != null && !fallbackOnly.get()) {
            try {
                provider.player(player.uniqueId).sendMessage(message)
                return
            } catch (error: Throwable) {
                rethrowFatal(error)
                // A broken/incompatible platform bridge must not break the calling plugin.
                if (fallbackOnly.compareAndSet(false, true)) {
                    logger.warning("Adventure Bukkit transport failed (${error.javaClass.simpleName}); switched to legacy text fallback")
                }
            }
        }
        player.sendMessage(LEGACY.serialize(message))
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
