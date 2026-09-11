package ru.privatenull.pnlibrary.api.actions

import ru.privatenull.pnlibrary.api.text.ComponentSerializerType
import ru.privatenull.pnlibrary.api.config.ConfigType

/** Displays an action-bar message to the selected audience. */
@ConfigType("action-bar")
data class ActionBarAction(
    val text: String = "",
    /** `null` inherits the serializer selected by the execution context. */
    val serializerType: ComponentSerializerType? = null,
    val target: Action.Target = Action.Target.PLAYER,
) : Action {
    override fun execute(context: Action.Context) {
        context.target(target).actionBar(context.component(text, serializerType))
    }
}
