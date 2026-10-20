package org.pnfolder.pnlibrary.demo

import org.bukkit.entity.Player
import org.pnfolder.pnlibrary.api.actions.ActionTarget
import org.pnfolder.pnlibrary.api.actions.MessageAction
import org.pnfolder.pnlibrary.api.events.Event
import org.pnfolder.pnlibrary.api.events.EventHandler
import org.pnfolder.pnlibrary.api.events.Listener
import org.pnfolder.pnlibrary.api.events.ResultedEvent
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider

class DemoEvent : Event()

/** Example of a resulted event: a listener can deny it with a reason shown to the caller. */
class DemoPurchaseEvent : ResultedEvent<ResultedEvent.ComponentResult>() {
    override var result: ResultedEvent.ComponentResult = ResultedEvent.ComponentResult.allowed()
}

/** Example of a plain allow/deny event that needs no reason: a listener only says yes or no. */
class DemoAccessEvent(val playerName: String) : ResultedEvent<ResultedEvent.GenericResult>() {

    constructor(playerName: String, reason: String) : this(playerName) {
        result = GenericResult.denied()

    }

    override var result: ResultedEvent.GenericResult = ResultedEvent.GenericResult.allowed()
}

class DemoLibraryEvents(private val plugin: DemoPlugin) : Listener {
    @EventHandler
    fun onDemo(event: DemoEvent) {
        plugin.logger.info("received ${event.eventName}")
    }

    /** Denies access for one name; the caller reads [Event.isAllowed] after [Event.callEvent]. */
    @EventHandler(ignoreCancelled = true)
    fun onAccess(event: DemoAccessEvent) {
        event.result = ResultedEvent.GenericResult.denied()
        if (event.playerName.equals("banned", ignoreCase = true)) {
            event.result = ResultedEvent.GenericResult.denied()
        }
    }

    fun sendAction(player: Player) {
        val audience = PnLibraryProvider.get().audiences.player(player.uniqueId) ?: return
        plugin.context.actions.execute(
            audience, PnLibraryProvider.get().audiences.all(),
            listOf(MessageAction(listOf("<aqua>pnDemo <white>uses the unified Action API"), target = ActionTarget.PLAYER)),
        )
    }
}
