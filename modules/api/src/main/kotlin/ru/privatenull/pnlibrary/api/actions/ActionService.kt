package ru.privatenull.pnlibrary.api.actions

import ru.privatenull.pnlibrary.api.text.ComponentSerializerType

/**
 * Creates plugin-bound action contexts and executes already deserialized action graphs.
 *
 * Implementations supply infrastructure such as text parsing, logging, and task scope.
 * The service does not own action definitions and does not catch execution failures.
 */
interface ActionService {
    /**
     * Creates a fresh context for one logical invocation.
     *
     * @param player player that triggered or owns the invocation
     * @param allPlayers wider dynamic audience available to targeted actions
     * @param values named values exposed to conditions
     * @param serializerType default parser for text-producing actions
     */
    fun context(
        player: LibraryPlayer,
        allPlayers: LibraryAudience,
        values: Map<String, Any?> = emptyMap(),
        serializerType: ComponentSerializerType = ComponentSerializerType.ADAPTIVE,
    ): ActionContext

    /** Executes one [action] immediately with [context]. */
    fun execute(context: ActionContext, action: Action) = action.execute(context)

    /** Executes [actions] immediately and sequentially with [context]. */
    fun execute(context: ActionContext, actions: Iterable<Action>) = context.execute(actions)

    /** Creates a default context and executes [actions] sequentially. */
    fun execute(
        player: LibraryPlayer,
        allPlayers: LibraryAudience,
        actions: Iterable<Action>,
        values: Map<String, Any?> = emptyMap(),
    ) = execute(context(player, allPlayers, values), actions)
}
