package ru.privatenull.pnlibrary.bukkit

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import ru.privatenull.pnlibrary.api.actions.Action
import java.lang.reflect.Method
import java.util.UUID
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/** Version-safe Bukkit player bridge; works without depending on Paper Adventure methods. */
class BukkitLibraryPlayer private constructor(private val player: Player) : Action.LibraryPlayer {
    override val uniqueId: UUID get() = player.uniqueId
    override val name: String get() = player.name

    override fun sendMessage(message: Component) {
        val native = NATIVE_SEND.computeIfAbsent(player.javaClass) { Optional.ofNullable(findNativeSend(it)) }.orElse(null)
        if (native != null && runCatching { native.invoke(player, message) }.isSuccess) return
        player.sendMessage(LEGACY.serialize(message))
    }

    companion object {
        private val LEGACY = LegacyComponentSerializer.legacySection()
        private val NATIVE_SEND = ConcurrentHashMap<Class<*>, Optional<Method>>()

        /** Detects capabilities instead of guessing them from a fork name or version string. */
        private fun findNativeSend(type: Class<*>): Method? = type.methods.firstOrNull { method ->
            method.name == "sendMessage" &&
                method.parameterCount == 1 &&
                method.parameterTypes[0].name == Component::class.java.name
        }

        @JvmStatic fun of(player: Player): Action.LibraryPlayer = BukkitLibraryPlayer(player)
    }
}
