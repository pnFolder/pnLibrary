package ru.privatenull.pnlibrary.demo

import org.bukkit.entity.Player
import ru.privatenull.pnlibrary.api.actions.ActionTarget
import ru.privatenull.pnlibrary.api.actions.MessageAction
import ru.privatenull.pnlibrary.api.events.Event
import ru.privatenull.pnlibrary.api.events.EventHandler
import ru.privatenull.pnlibrary.api.events.Listener
import ru.privatenull.pnlibrary.api.runtime.PnLibraryProvider

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
