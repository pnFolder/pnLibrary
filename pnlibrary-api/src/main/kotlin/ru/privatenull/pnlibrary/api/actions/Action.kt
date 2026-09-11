package ru.privatenull.pnlibrary.api.actions

import ru.privatenull.pnlibrary.api.text.ComponentSerializerType
import ru.privatenull.pnlibrary.api.text.ComponentService
import net.kyori.adventure.text.Component
import java.util.UUID

fun interface Action {

    fun execute(context: Context)

    class Context(
        val player: LibraryPlayer,
        val components: ComponentService,
        val serializerType: ComponentSerializerType =
            ComponentSerializerType.ADAPTIVE,
        initialObjects: Map<Class<*>, Any> = emptyMap(),
        /** Target used by broadcast actions; defaults to the current player. */
        val audience: LibraryAudience = player,
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

        companion object {
            /** Creates one audience without exposing its player collection to actions. */
            @JvmStatic fun of(players: Iterable<LibraryPlayer>): LibraryAudience {
                val snapshot = players.toList()
                return object : LibraryAudience {
                    override fun sendMessage(text: Component) = snapshot.forEach { it.sendMessage(text) }
                    override fun actionBar(text: Component) = snapshot.forEach { it.actionBar(text) }
                }
            }
        }
    }

    interface LibraryPlayer : LibraryAudience {
        val uniqueId: UUID
        val name: String
    }
}
