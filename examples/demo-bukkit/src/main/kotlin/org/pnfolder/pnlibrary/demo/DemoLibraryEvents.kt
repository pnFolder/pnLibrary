package org.pnfolder.pnlibrary.demo

import org.bukkit.entity.Player
import org.pnfolder.pnlibrary.api.actions.ActionTarget
import org.pnfolder.pnlibrary.api.actions.MessageAction
import org.pnfolder.pnlibrary.api.events.Event
import org.pnfolder.pnlibrary.api.events.EventHandler
import org.pnfolder.pnlibrary.api.events.Listener
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider

class DemoEvent : Event()

class DemoLibraryEvents(private val plugin: DemoPlugin) : Listener {
    @EventHandler
    fun onDemo(event: DemoEvent) {
        plugin.logger.info("received ${event.eventName}")
    }

    fun sendAction(player: Player) {
        val audience = PnLibraryProvider.get().audiences.player(player.uniqueId) ?: return
        plugin.context.actions.execute(
            audience, PnLibraryProvider.get().audiences.all(),
            listOf(MessageAction(listOf("<aqua>pnDemo <white>uses the unified Action API"), target = ActionTarget.PLAYER)),
        )
    }
}
