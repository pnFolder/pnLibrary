package org.pnfolder.pnlibrary.core.plugin.module

import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionService
import org.pnfolder.pnlibrary.api.scenario.actions.DefinitionConflict
import org.pnfolder.pnlibrary.api.scenario.actions.DefinitionScope
import org.pnfolder.pnlibrary.api.audiences.LibraryAudience
import org.pnfolder.pnlibrary.api.audiences.player.LibraryPlayer
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement
import org.pnfolder.pnlibrary.api.scenario.expression.Expression
import org.pnfolder.pnlibrary.api.scenario.expression.ExpressionFunction
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializerType

/**
 * Action service of one module: every context gets the module's text, logger, tasks, placeholders and services,
 * and sees the names the module defined plus the ones defined for the whole library.
 */
internal class ModuleActionService(
    private val resources: ModuleResources,
    private val shared: ScenarioDefinitions,
    private val owner: String,
) : ActionService {
    private val own = ScenarioDefinitions()
    private val lookup = LayeredLookup(own, shared)

    override fun context(
        player: LibraryPlayer,
        allPlayers: LibraryAudience,
        values: Map<String, Any?>,
        serializerType: ComponentSerializerType,
    ) = ActionContext(
        player = player,
        allPlayers = allPlayers,
        components = resources.components,
        logger = resources.logger,
        tasks = resources.tasks,
        serializerType = serializerType,
        values = values,
        serviceLookup = { type -> resources.services.get(type) },
        lookup = lookup,
        placeholders = resources.placeholders,
    )

    private fun target(scope: DefinitionScope) = if (scope == DefinitionScope.LIBRARY) shared else own

    override fun define(name: String, action: Action, scope: DefinitionScope, conflict: DefinitionConflict) =
        target(scope).defineAction(name, action, owner, conflict)

    override fun defineRequirement(name: String, requirement: Requirement, scope: DefinitionScope, conflict: DefinitionConflict) =
        target(scope).defineRequirement(name, requirement, owner, conflict)

    override fun defineFunction(name: String, function: ExpressionFunction, scope: DefinitionScope, conflict: DefinitionConflict): AutoCloseable {
        require(!Expression.isBuiltin(name.trim().lowercase())) { "'$name' is a function of the expression language itself" }
        return target(scope).defineFunction(name, function, owner, conflict)
    }
}
