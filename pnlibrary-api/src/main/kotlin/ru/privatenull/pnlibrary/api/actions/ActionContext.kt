package ru.privatenull.pnlibrary.api.actions

import net.kyori.adventure.text.Component
import ru.privatenull.pnlibrary.api.logging.PnLogger
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.text.ComponentSerializerType
import ru.privatenull.pnlibrary.api.text.ComponentService

class ActionContext(
    val player: LibraryPlayer,
    val allPlayers: LibraryAudience,
    val components: ComponentService,
    val logger: PnLogger,
    val tasks: TaskScope,
    val serializerType: ComponentSerializerType = ComponentSerializerType.ADAPTIVE,
    val values: Map<String, Any?> = emptyMap(),
    initialObjects: Map<Class<*>, Any> = emptyMap(),
) {
    private val objects = HashMap(initialObjects)
    fun component(text: String, type: ComponentSerializerType? = null): Component = components.deserialize(text, type ?: serializerType)
    fun component(lines: Iterable<String>, type: ComponentSerializerType? = null): Component = components.deserializeLines(lines, type ?: serializerType)
    fun target(target: ActionTarget): LibraryAudience = if (target == ActionTarget.PLAYER) player else allPlayers
    fun execute(actions: Iterable<Action>) = actions.forEach { it.execute(this) }
    fun value(name: String): Any? = values[name]
    fun <T : Any> put(type: Class<T>, value: T) = apply { objects[type] = value }
    fun <T : Any> get(type: Class<T>): T? = objects[type]?.let(type::cast)
    fun <T : Any> require(type: Class<T>): T = get(type) ?: error("Missing action context value: ${type.name}")
    fun has(type: Class<*>): Boolean = objects.containsKey(type)
    fun <T : Any> remove(type: Class<T>): T? = objects.remove(type)?.let(type::cast)
}
