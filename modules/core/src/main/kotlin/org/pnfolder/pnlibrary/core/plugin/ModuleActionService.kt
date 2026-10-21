package org.pnfolder.pnlibrary.core.plugin

import org.pnfolder.pnlibrary.api.actions.Action
import org.pnfolder.pnlibrary.api.actions.ActionContext
import org.pnfolder.pnlibrary.api.actions.ActionService
import org.pnfolder.pnlibrary.api.audiences.LibraryAudience
import org.pnfolder.pnlibrary.api.audiences.LibraryPlayer
import org.pnfolder.pnlibrary.api.checks.Requirement
import org.pnfolder.pnlibrary.api.text.ComponentSerializerType

/** Creates action contexts backed by one module's components, logger, tasks and placeholders. */
internal class ModuleActionService(
    private val resources: ModuleResources,
) : ActionService {
    override fun context(
        player: LibraryPlayer,
        allPlayers: LibraryAudience,
        values: Map<String, Any?>,
        serializerType: ComponentSerializerType,
        templates: Map<String, List<Action>>,
        checks: Map<String, Requirement>,
    ) = ActionContext(
        player = player,
        allPlayers = allPlayers,
        components = resources.components,
        logger = resources.logger,
        tasks = resources.tasks,
        serializerType = serializerType,
        values = values,
        templates = templates,
        checks = checks,
        placeholders = resources.placeholders,
    )
}
