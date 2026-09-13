package ru.privatenull.pnlibrary.api.actions

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import java.util.UUID
import java.util.function.Supplier

interface LibraryAudience {
    fun sendMessage(text: Component)
    fun actionBar(text: Component)
    fun playSound(sound: Sound): Boolean

    companion object {
        @JvmStatic fun dynamic(players: Supplier<out Iterable<LibraryPlayer>>): LibraryAudience = object : LibraryAudience {
            override fun sendMessage(text: Component) = players.get().forEach { it.sendMessage(text) }
            override fun actionBar(text: Component) = players.get().forEach { it.actionBar(text) }
            override fun playSound(sound: Sound): Boolean = players.get().map { it.playSound(sound) }.all { it }
        }
    }
}

interface LibraryPlayer : LibraryAudience {
    val uniqueId: UUID
    val name: String
    fun applyEffect(effect: PlayerEffect): Boolean
    fun spawnParticle(particle: PlayerParticle): Boolean
}

enum class ActionTarget { PLAYER, ALL_PLAYERS }
