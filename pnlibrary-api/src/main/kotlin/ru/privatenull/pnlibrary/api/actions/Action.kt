package ru.privatenull.pnlibrary.api.actions

import ru.privatenull.pnlibrary.api.text.ComponentSerializerType
import ru.privatenull.pnlibrary.api.text.ComponentService
import net.kyori.adventure.text.Component
import java.util.UUID
import java.util.function.Supplier
import java.time.Duration
import net.kyori.adventure.sound.Sound
import ru.privatenull.pnlibrary.api.logging.PnLogger
import ru.privatenull.pnlibrary.api.tasks.TaskScope

fun interface Action {

    fun execute(context: Context)

    class Context(
        val player: LibraryPlayer,
        /** Actual audience of all online players; it must never silently fall back to [player]. */
        val allPlayers: LibraryAudience,
        val components: ComponentService,
        val logger: PnLogger,
        val tasks: TaskScope,
        val serializerType: ComponentSerializerType =
            ComponentSerializerType.ADAPTIVE,
        initialObjects: Map<Class<*>, Any> = emptyMap(),
    ) {
        private val objects = HashMap(initialObjects)

        fun component(text: String, type: ComponentSerializerType? = null): Component =
            components.deserialize(text, type ?: serializerType)

        /** Joins source lines with real newline components. */
        fun component(lines: Iterable<String>, type: ComponentSerializerType? = null): Component =
            components.deserializeLines(lines, type ?: serializerType)

        /** Vararg convenience for a multiline component. */
        fun component(vararg lines: String): Component =
            components.deserializeLines(lines.asList(), serializerType)

        /** Preserves every source line as a separate component. */
        fun componentList(lines: Iterable<String>, type: ComponentSerializerType? = null): List<Component> =
            components.deserializeAll(lines, type ?: serializerType)

        fun target(target: Target): LibraryAudience = when (target) {
            Target.PLAYER -> player
            Target.ALL -> allPlayers
        }

        fun execute(actions: Iterable<Action>) {
            actions.forEach { it.execute(this) }
        }

        fun later(delay: Duration, actions: Iterable<Action>) =
            tasks.later(delay, Runnable { execute(actions) })

        fun <T : Any> put(type: Class<T>, value: T): Context = apply {
            objects[type] = value
        }

        fun <T : Any> get(type: Class<T>): T? =
            objects[type]?.let(type::cast)

        fun <T : Any> require(type: Class<T>): T =
            get(type) ?: error(
                "Missing action context value: ${type.name}"
            )

        fun has(type: Class<*>): Boolean =
            objects.containsKey(type)

        fun <T : Any> remove(type: Class<T>): T? =
            objects.remove(type)?.let(type::cast)

        fun clear() {
            objects.clear()
        }
    }

    interface LibraryAudience {
        fun sendMessage(text: Component)
        fun actionBar(text: Component)
        /** Returns false when this platform cannot produce client-side sounds. */
        fun playSound(sound: Sound): Boolean

        companion object {
            /** Creates one audience without exposing its player collection to actions. */
            @JvmStatic fun of(players: Iterable<LibraryPlayer>): LibraryAudience {
                val snapshot = players.toList()
                return object : LibraryAudience {
                    override fun sendMessage(text: Component) = snapshot.forEach { it.sendMessage(text) }
                    override fun actionBar(text: Component) = snapshot.forEach { it.actionBar(text) }
                    override fun playSound(sound: Sound): Boolean = snapshot.map { it.playSound(sound) }.all { it }
                }
            }

            /** Resolves online players for every send, suitable for delayed broadcast actions. */
            @JvmStatic fun dynamic(players: Supplier<out Iterable<LibraryPlayer>>): LibraryAudience =
                object : LibraryAudience {
                    override fun sendMessage(text: Component) = players.get().forEach { it.sendMessage(text) }
                    override fun actionBar(text: Component) = players.get().forEach { it.actionBar(text) }
                    override fun playSound(sound: Sound): Boolean = players.get().map { it.playSound(sound) }.all { it }
                }
        }
    }

    enum class Target { PLAYER, ALL }

    interface LibraryPlayer : LibraryAudience {
        val uniqueId: UUID
        val name: String
    }
}
