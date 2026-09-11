package ru.privatenull.pnlibrary.api.actions.impl

import ru.privatenull.pnlibrary.api.actions.Action
import ru.privatenull.pnlibrary.api.text.ComponentSerializerType

/** Sends one action-bar component to the audience supplied by the execution context. */
class BroadcastActionBarImpl(
    var text: String = "",
    var serializerType: ComponentSerializerType? = null,
) : Action {
    override fun execute(context: Action.Context) {
        context.audience.actionBar(context.component(text, serializerType))
    }
}
