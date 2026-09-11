package ru.privatenull.pnlibrary.api.actions.impl

import ru.privatenull.pnlibrary.api.actions.Action
import ru.privatenull.pnlibrary.api.text.ComponentSerializerType

class ActionBarImpl(
    var text: String = "",
    /** `null` means: inherit the serializer selected by the execution context. */
    var serializerType: ComponentSerializerType? = null,
    var target: Action.Target = Action.Target.PLAYER,
) : Action {

    override fun execute(context: Action.Context) {
        context.target(target).actionBar(context.component(text, serializerType))
    }
}
