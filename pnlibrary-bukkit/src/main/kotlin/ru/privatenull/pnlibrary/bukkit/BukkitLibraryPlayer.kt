package ru.privatenull.pnlibrary.bukkit

import net.kyori.adventure.text.Component
import net.kyori.adventure.sound.Sound
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import ru.privatenull.pnlibrary.api.actions.LibraryPlayer
import ru.privatenull.pnlibrary.api.actions.PlayerEffect
import ru.privatenull.pnlibrary.api.actions.PlayerParticle
import java.util.UUID

/**
 * Version-safe [LibraryPlayer] backed by a Bukkit [Player].
 *
 * Chat, action-bar, and sound delivery is delegated to [BukkitAudienceService], which can fall back
 * when native Adventure integration is unavailable. Effects and particles use Bukkit APIs with
 * reflective compatibility for older server versions.
 */
internal class BukkitLibraryPlayer constructor(
    private val player: Player,
    private val audiences: BukkitAudienceService,
) : LibraryPlayer {
    override val uniqueId: UUID get() = player.uniqueId
    override val name: String get() = player.name
    override fun hasPermission(permission: String): Boolean = player.hasPermission(permission)

    override fun sendMessage(text: Component) {
        audiences.sendMessage(player, text)
    }

    override fun actionBar(text: Component) {
        audiences.sendActionBar(player, text)
    }

    override fun playSound(sound: Sound): Boolean = audiences.playSound(player, sound)

    override fun applyEffect(effect: PlayerEffect): Boolean {
        val type = PotionEffectType.getByName(effect.key.uppercase()) ?: return false
        val ticks = (effect.duration.toMillis() / MILLIS_PER_TICK)
            .coerceIn(1L, Int.MAX_VALUE.toLong())
            .toInt()
        val configured = runCatching {
            PotionEffect::class.java.getConstructor(
                PotionEffectType::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            ).newInstance(type, ticks, effect.amplifier, effect.ambient, effect.particles, effect.icon)
        }.getOrElse { PotionEffect(type, ticks, effect.amplifier, effect.ambient) }
        return player.addPotionEffect(configured, true)
    }

    override fun spawnParticle(particle: PlayerParticle): Boolean {
        val type = runCatching { Class.forName("org.bukkit.Particle") }.getOrNull() ?: return false
        val constant = type.enumConstants.firstOrNull {
            (it as Enum<*>).name.equals(particle.key, ignoreCase = true)
        } ?: return false
        val method = player.javaClass.methods.firstOrNull {
            it.name == "spawnParticle" && it.parameterTypes.size == 7 && it.parameterTypes[0] == type
        } ?: return false
        return runCatching {
            method.invoke(
                player,
                constant,
                player.location,
                particle.count,
                particle.offsetX,
                particle.offsetY,
                particle.offsetZ,
                particle.speed,
            )
        }.fold(
            onSuccess = { true },
            onFailure = { error ->
                rethrowFatal(error)
                false
            },
        )
    }

    private companion object {
        const val MILLIS_PER_TICK = 50L

        fun rethrowFatal(error: Throwable) {
            if (error is Error) throw error
        }
    }
}
