package org.pnfolder.pnlibrary.bukkit.audience

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.translation.Translator
import net.kyori.adventure.title.Title
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.pnfolder.pnlibrary.api.audience.Delivery
import org.pnfolder.pnlibrary.api.audience.ItemQuery
import org.pnfolder.pnlibrary.api.audience.LibraryPlayer
import org.pnfolder.pnlibrary.api.audience.PlayerCommands
import org.pnfolder.pnlibrary.api.audience.PlayerEffects
import org.pnfolder.pnlibrary.api.audience.PlayerExperience
import org.pnfolder.pnlibrary.api.audience.PlayerInventory
import org.pnfolder.pnlibrary.api.audience.PlayerPosition
import org.pnfolder.pnlibrary.api.audience.PlayerEffect
import org.pnfolder.pnlibrary.api.audience.PlayerParticle
import org.pnfolder.pnlibrary.api.audience.playerPointers
import org.pnfolder.pnlibrary.bukkit.currency.configured
import org.pnfolder.pnlibrary.bukkit.currency.error
import org.pnfolder.pnlibrary.common.lang.Failures
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.util.Locale
import java.util.UUID

/**
 * Version-safe [LibraryPlayer] backed by a Bukkit [Player].
 *
 * Chat, action-bar, and sound delivery is delegated to [BukkitAdventureTransport], which can fall back
 * when native Adventure integration is unavailable. Effects and particles use Bukkit APIs with
 * reflective compatibility for older server versions.
 */
internal class BukkitLibraryPlayer(
    private val player: Player,
    private val audiences: BukkitAdventureTransport,
) : LibraryPlayer, PlayerEffects, PlayerCommands, PlayerInventory, PlayerExperience, PlayerPosition {
    override val uniqueId: UUID get() = player.uniqueId

    override fun pointers() = playerPointers(
        name = { player.name },
        uniqueId = player.uniqueId,
        displayName = { LEGACY.deserialize(player.displayName) },
        locale = ::clientLocale,
        permission = player::hasPermission,
    )

    // Player.getLocale exists since 1.12 and the runtime is built against 1.8, so it is looked up once by name.
    private fun clientLocale(): Locale? {
        val read = LOCALE_READER ?: return null
        val tag = runCatchingNonFatal { read.invoke(player) as? String }.getOrNull() ?: return null
        return Translator.parseLocale(tag)
    }

    override fun sendMessage(message: Component) {
        audiences.sendMessage(player, message)
    }

    override fun sendActionBar(message: Component) {
        audiences.sendActionBar(player, message)
    }

    override fun playSound(sound: Sound) {
        audiences.playSound(player, sound)
    }

    override fun showTitle(title: Title) {
        audiences.showTitle(player, title)
    }

    override fun applyEffect(effect: PlayerEffect): Delivery {
        val type = PotionEffectType.getByName(effect.key.uppercase()) ?: return Delivery.unsupported()
        val ticks = (effect.duration.toMillis() / MILLIS_PER_TICK)
            .coerceIn(1L, Int.MAX_VALUE.toLong())
            .toInt()
        val configured = runCatchingNonFatal {
            PotionEffect::class.java.getConstructor(
                PotionEffectType::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            ).newInstance(type, ticks, effect.amplifier, effect.ambient, effect.particles, effect.icon)
        }.getOrElse { PotionEffect(type, ticks, effect.amplifier, effect.ambient) }
        return Delivery.of(player.addPotionEffect(configured, true))
    }

    override fun runCommand(command: String, asConsole: Boolean): Delivery {
        val sender = if (asConsole) player.server.consoleSender else player
        return Delivery.of(player.server.dispatchCommand(sender, command))
    }

    /** Counts items in the main inventory and hotbar that fit [query]; unknown materials count as zero. */
    override fun countItems(query: ItemQuery): Int {
        val material = materialOf(query.material) ?: return 0
        return player.inventory.contents
            .filter { stack -> stack != null && stack.type == material && fits(stack, query) }
            .sumOf { stack -> stack.amount }
    }

    override fun experience(asLevels: Boolean): Double =
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

    /** Renders a supported particle, returning [Delivery.unsupported()] when the server lacks the required API. */
    override fun spawnParticle(particle: PlayerParticle): Delivery {
        val type = runCatchingNonFatal { Class.forName("org.bukkit.Particle") }.getOrNull() ?: return Delivery.unsupported()
        val constant = type.enumConstants.firstOrNull {
            (it as Enum<*>).name.equals(particle.key, ignoreCase = true)
        } ?: return Delivery.unsupported()
        val method = player.javaClass.methods.firstOrNull {
            it.name == "spawnParticle" && it.parameterTypes.size == 7 && it.parameterTypes[0] == type
        } ?: return Delivery.unsupported()
        return runCatchingNonFatal {
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
            onSuccess = { Delivery.delivered() },
            onFailure = { error ->
                Failures.rethrowIfFatal(error)
                Delivery.failed(error)
            },
        )
    }

    private companion object {
        val LEGACY = LegacyComponentSerializer.legacySection()
        val LOCALE_READER = runCatching { Player::class.java.getMethod("getLocale") }.getOrNull()
        const val MILLIS_PER_TICK = 50L
    }
}
