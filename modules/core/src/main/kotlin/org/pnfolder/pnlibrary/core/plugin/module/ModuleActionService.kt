package org.pnfolder.pnlibrary.core.plugin.module

import org.pnfolder.pnlibrary.api.audiences.LibraryAudience
import org.pnfolder.pnlibrary.api.audiences.player.LibraryPlayer
import org.pnfolder.pnlibrary.api.placeholders.Visibility
import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionService
import org.pnfolder.pnlibrary.api.scenario.actions.DefinitionConflict
import org.pnfolder.pnlibrary.api.scenario.actions.DefinitionScope
import org.pnfolder.pnlibrary.api.scenario.expression.Expression
import org.pnfolder.pnlibrary.api.scenario.expression.ExpressionFunction
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement
import org.pnfolder.pnlibrary.api.text.serializer.ComponentSerializerType

/**
 * Action service of one module: every context gets the module's text, logger, tasks, placeholders and services,
 * and sees the names it may see, the nearest first: its own module, its own plugin, the others.
 */
internal class ModuleActionService(
    private val resources: ModuleResources,
    private val shared: ScenarioDefinitions,
    private val owner: String,
) : ActionService {
    private val lookup = shared.view(owner)

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

    private fun visibility(scope: DefinitionScope) = when (scope) {
        DefinitionScope.MODULE -> Visibility.MODULE
        DefinitionScope.PLUGIN -> Visibility.PLUGIN
        DefinitionScope.LIBRARY -> Visibility.ALL
    }

    override fun define(name: String, action: Action, scope: DefinitionScope, conflict: DefinitionConflict) =
        shared.defineAction(name, action, owner, conflict, visibility(scope), resources.logger::warning)

    override fun defineRequirement(name: String, requirement: Requirement, scope: DefinitionScope, conflict: DefinitionConflict) =
        shared.defineRequirement(name, requirement, owner, conflict, visibility(scope), resources.logger::warning)

    override fun defineFunction(name: String, function: ExpressionFunction, scope: DefinitionScope, conflict: DefinitionConflict): AutoCloseable {
        require(!Expression.isBuiltin(name.trim().lowercase())) { "'$name' is a function of the expression language itself" }
        return shared.defineFunction(name, function, owner, conflict, visibility(scope), resources.logger::warning)
    }
}
