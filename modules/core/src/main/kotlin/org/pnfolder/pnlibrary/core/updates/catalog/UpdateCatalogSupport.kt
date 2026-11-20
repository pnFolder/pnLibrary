package org.pnfolder.pnlibrary.core.updates.catalog

import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.updates.UpdateActor

/** The update actor that stands for this sender in the update history. */
internal fun CommandSender.asUpdateActor(): UpdateActor =
    UpdateActor(if (isConsole) UpdateActor.Kind.CONSOLE else UpdateActor.Kind.PLAYER, id, name)

/** The names of all plugins registered for updates, for tab completion. */
internal fun PnLibrary.updateProductNames(): List<String> = updates.all().map { it.snapshot.product }
