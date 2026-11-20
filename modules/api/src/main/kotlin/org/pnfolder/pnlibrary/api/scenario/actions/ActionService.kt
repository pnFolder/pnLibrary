package org.pnfolder.pnlibrary.api.scenario.actions

import org.pnfolder.pnlibrary.api.audiences.LibraryAudience
import org.pnfolder.pnlibrary.api.audiences.player.LibraryPlayer
import org.pnfolder.pnlibrary.api.scenario.expression.ExpressionFunction
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializerType

/**
 * Creates the contexts of one module's scenarios, and keeps the actions, requirements and functions the plugin
 * defined by name.
 *
 * It holds no scenarios itself: the plugin keeps its parsed `List<Action>` and starts it for a player.
 *
 * ```kotlin
 * module.actions.define("give-kit") { ctx ->           // later: - { type: call, name: give-kit, args: { kit: starter } }
 *     kits.give(ctx.player, ctx.format("[kit]"))
 *     ActionResult.Done
 * }
 * module.actions.defineRequirement("is-vip", Requirement { ctx -> Verdict.of(ctx.player.hasPermission("vip"), "VIP only") })
 * module.actions.defineFunction("kills") { ctx, _ -> stats.kills(ctx.player.uniqueId) }   // kills() in expressions
 *
 * val result = module.actions.execute(config.onBuy, player, values = mapOf("item" to "diamond"))
 * ```
 */
interface ActionService {
    /**
     * Creates a fresh context for one run.
     *
     * @param player player the run is for
     * @param allPlayers audience for actions aimed at everyone; the player alone by default
     * @param values named values texts and requirements can read as `[name]`
     * @param serializerType default format of printed text
     */
    fun context(
        player: LibraryPlayer,
        allPlayers: LibraryAudience = player,
        values: Map<String, Any?> = emptyMap(),
        serializerType: ComponentSerializerType = ComponentSerializerType.ADAPTIVE,
    ): ActionContext

    /** Creates a context and runs [actions] as one scenario; see [ActionContext.execute]. */
    fun execute(
        actions: List<Action>,
        player: LibraryPlayer,
        allPlayers: LibraryAudience = player,
        values: Map<String, Any?> = emptyMap(),
    ): ActionResult = context(player, allPlayers, values).execute(actions)

    /**
     * Gives [action] a [name] so that scenarios can run it with `type: call`, passing `args` as values.
     * Names are lowercase letters, digits, dots, hyphens and underscores.
     *
     * @param scope how far the name reaches
     * @param conflict what to do when the name is taken
     * @return a handle that removes the definition when closed
     */
    fun define(
        name: String,
        action: Action,
        scope: DefinitionScope = DefinitionScope.MODULE,
        conflict: DefinitionConflict = DefinitionConflict.FAIL,
    ): AutoCloseable

    /** Gives [requirement] a [name] for `type: ref` and for [org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext.requirement]. */
    fun defineRequirement(
        name: String,
        requirement: Requirement,
        scope: DefinitionScope = DefinitionScope.MODULE,
        conflict: DefinitionConflict = DefinitionConflict.FAIL,
    ): AutoCloseable

    /** Adds [function] to the expression language under [name], for example `kills()`. Built-in names cannot be taken. */
    fun defineFunction(
        name: String,
        function: ExpressionFunction,
        scope: DefinitionScope = DefinitionScope.MODULE,
        conflict: DefinitionConflict = DefinitionConflict.FAIL,
    ): AutoCloseable
}
