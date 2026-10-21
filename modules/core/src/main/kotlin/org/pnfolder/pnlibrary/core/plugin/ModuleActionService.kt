package org.pnfolder.pnlibrary.core.plugin

import org.pnfolder.pnlibrary.api.actions.Action
import org.pnfolder.pnlibrary.api.actions.ActionContext
import org.pnfolder.pnlibrary.api.actions.ActionService
import org.pnfolder.pnlibrary.api.actions.LibraryAudience
import org.pnfolder.pnlibrary.api.actions.LibraryPlayer
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
    ) = ActionContext(
        player = player,
        allPlayers = allPlayers,
        components = resources.components,
        logger = resources.logger,
        tasks = resources.tasks,
        serializerType = serializerType,
        values = values,
        templates = templates,
        placeholders = resources.placeholders,
    )
}
