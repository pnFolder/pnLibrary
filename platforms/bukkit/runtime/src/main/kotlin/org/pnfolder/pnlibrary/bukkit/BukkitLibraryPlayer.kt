package org.pnfolder.pnlibrary.bukkit

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.pnfolder.pnlibrary.api.actions.ItemQuery
import org.pnfolder.pnlibrary.api.actions.LibraryPlayer
import org.pnfolder.pnlibrary.api.actions.PlayerCapability
import org.pnfolder.pnlibrary.api.actions.PlayerEffect
import org.pnfolder.pnlibrary.api.actions.PlayerParticle
import java.time.Duration
import java.util.UUID

/**
 * Version-safe [LibraryPlayer] backed by a Bukkit [Player].
 *
 * Chat, action-bar, and sound delivery is delegated to [BukkitAudienceService], which can fall back
 * when native Adventure integration is unavailable. Effects and particles use Bukkit APIs with
 * reflective compatibility for older server versions.
 */
internal class BukkitLibraryPlayer(
    private val player: Player,
    private val audiences: BukkitAudienceService,
) : LibraryPlayer {
    override val capabilities: Set<PlayerCapability> = PlayerCapability.values().toSet()
    override val uniqueId: UUID get() = player.uniqueId
    override val name: String get() = player.name

    /** Checks the native player permission. */
    override fun hasPermission(permission: String): Boolean = player.hasPermission(permission)

    /** Delivers chat through the version-safe audience transport. */
    override fun sendMessage(text: Component) {
        audiences.sendMessage(player, text)
    }

    /** Delivers an action bar through the version-safe audience transport. */
    override fun actionBar(text: Component) {
        audiences.sendActionBar(player, text)
    }

    /** Plays a sound, falling back to the native Bukkit API where needed. */
    override fun playSound(sound: Sound): Boolean = audiences.playSound(player, sound)

    /** Shows a title through the version-safe audience transport. */
    override fun showTitle(title: Component, subtitle: Component, fadeIn: Duration, stay: Duration, fadeOut: Duration): Boolean =
        audiences.showTitle(player, Title.title(title, subtitle, Title.Times.times(fadeIn, stay, fadeOut)))

    /** Applies a potion effect using the best constructor supported by this server. */
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

    /** Dispatches a command as the player or as the console. */
    override fun runCommand(command: String, asConsole: Boolean): Boolean {
        val sender = if (asConsole) player.server.consoleSender else player
        return player.server.dispatchCommand(sender, command)
    }

    /** Counts items in the main inventory and hotbar that fit [query]; unknown materials count as zero. */
    override fun countItems(query: ItemQuery): Int? {
        val material = materialOf(query.material) ?: return 0
        return player.inventory.contents
            .filter { stack -> stack != null && stack.type == material && fits(stack, query) }
            .sumOf { stack -> stack.amount }
    }

    /** Returns experience levels or total points of the native player. */
    override fun experience(asLevels: Boolean): Double? =
        if (asLevels) player.level.toDouble() else player.totalExperience.toDouble()

    /** Measures the distance to a point, or returns null when the player is in another world. */
    override fun distanceTo(world: String, x: Double, y: Double, z: Double): Double? {
        val here = player.location
        val current = here.world ?: return null
        if (!current.name.equals(world, ignoreCase = true)) return null
        return here.distance(Location(current, x, y, z))
    }

    private fun materialOf(name: String): Material? =
        Material.matchMaterial(name.trim().removePrefix("minecraft:").uppercase())

    private fun fits(stack: ItemStack, query: ItemQuery): Boolean {
        if (query.name == null && query.nameContains == null && query.loreContains == null) return true
        val meta = stack.itemMeta ?: return false
        val ignoreCase = query.ignoreCase
        val display = if (meta.hasDisplayName()) meta.displayName else null
        val exactName = query.name
        if (exactName != null && display?.equals(colored(exactName), ignoreCase) != true) return false
        val partialName = query.nameContains
        if (partialName != null && display?.contains(colored(partialName), ignoreCase) != true) return false
        val needle = query.loreContains ?: return true
        return meta.lore.orEmpty().any { line -> line.contains(colored(needle), ignoreCase) }
    }

    private fun colored(text: String): String = ChatColor.translateAlternateColorCodes('&', text)

    /** Renders a supported particle, returning false when the server lacks the required API. */
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
