package ru.privatenull.pnlibrary.core.plugin

import ru.privatenull.pnlibrary.api.actions.ActionContext
import ru.privatenull.pnlibrary.api.actions.ActionService
import ru.privatenull.pnlibrary.api.actions.LibraryAudience
import ru.privatenull.pnlibrary.api.actions.LibraryPlayer
import ru.privatenull.pnlibrary.api.text.ComponentSerializerType

internal class ModuleActionService(
    private val resources: ModuleResources,
) : ActionService {
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
        placeholders = resources.placeholders,
    )
}
