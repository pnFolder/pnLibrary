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
    ) {
        private val objects = HashMap(initialObjects)

        fun component(text: String, type: ComponentSerializerType? = null): Component =
            components.deserialize(text, type ?: serializerType)

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

    interface LibraryPlayer {
        val uniqueId: UUID
        val name: String
        fun sendMessage(message: Component)
    }
}
