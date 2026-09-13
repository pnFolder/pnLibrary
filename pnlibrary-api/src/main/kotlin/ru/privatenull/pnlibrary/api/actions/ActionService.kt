package ru.privatenull.pnlibrary.api.actions

import ru.privatenull.pnlibrary.api.text.ComponentSerializerType

/** Executes already deserialized actions with one explicit execution context. */
interface ActionService {
    fun context(
        player: LibraryPlayer,
        allPlayers: LibraryAudience,
        values: Map<String, Any?> = emptyMap(),
        serializerType: ComponentSerializerType = ComponentSerializerType.ADAPTIVE,
    ): ActionContext

    fun execute(context: ActionContext, action: Action) = action.execute(context)
    fun execute(context: ActionContext, actions: Iterable<Action>) = context.execute(actions)

    fun execute(
        player: LibraryPlayer,
        allPlayers: LibraryAudience,
        actions: Iterable<Action>,
        values: Map<String, Any?> = emptyMap(),
    ) = execute(context(player, allPlayers, values), actions)
}
