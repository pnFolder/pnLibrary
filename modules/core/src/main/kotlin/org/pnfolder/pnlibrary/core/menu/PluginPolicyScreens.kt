package org.pnfolder.pnlibrary.core.menu

import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import java.time.Duration
import java.time.Instant

internal class PluginPolicyScreens(session: MenuSession, routes: MenuRoutes) : MenuScreens(session, routes) {
    /** The channel choice. It stays in the code and is switched on by [CHANNEL_SWITCH] once more than `dev` works. */
    fun channels(player: MenuViewer, product: String) {
        val current = data.snapshot(product)?.channel
        val builder = frame(Menus.chest(title("Канал $product")).rows(SMALL_ROWS), SMALL_ROWS)
        UpdateChannel.values().forEachIndexed { index, channel ->
            val chosen = channel == current
            val lore = Style.lore(
                listOf(CHANNEL_HINTS.getValue(channel)),
                if (chosen) listOf("Состояние" to "выбран сейчас") else emptyList(),
                *if (chosen) emptyArray() else arrayOf(Style.left("выбрать")),
            )
            builder.button(
                10 + index,
                icon(null, CHANNEL_ITEMS.getValue(channel), channel.name.lowercase(), lore, good = chosen),
                go { who -> allowed(who, SETTINGS) { chooseChannel(who, product, channel) } },
            )
        }
        builder.button(
            16,
            icon(
                Icons.RESET, "BARRIER", "Сбросить",
                Style.lore(listOf("Вернуть канал, который указал плагин."), emptyList(), Style.left("сбросить")), muted = true,
            ),
            go { who -> allowed(who, SETTINGS) { chooseChannel(who, product, null) } },
        )
        builder.button(SMALL_BACK, backItem(), go { routes.card(it, product) })
        show(player, builder.build())
    }

    private fun holdSummary(remaining: Duration?) = icon(
        Icons.CLOCK, "CLOCK", "Пауза сейчас",
        Style.lore(
            listOf("Пока пауза идёт, обновления не предлагаются и не скачиваются."),
            listOf("Состояние" to (remaining?.let { "‖ осталось ${left(it)}" } ?: "нет паузы")),
        ),
        alternative = "WATCH", warn = remaining != null,
    )

    private fun holdChoice(label: String, selectedRemaining: Duration?): ItemSpec {
        val note = selectedRemaining?.let { listOf("Выбрано" to "● осталось ${left(it)}") } ?: emptyList()
        val action = if (selectedRemaining != null) "начать срок заново" else "приостановить"
        return icon(
            Icons.CLOCK, "CLOCK", label,
            Style.lore(listOf("Приостановить обновления на этот срок."), note, Style.left(action)),
            alternative = "WATCH", good = selectedRemaining != null,
        )
    }

    fun holds(player: MenuViewer, product: String) {
        val until = data.heldUntil(product)?.takeIf { it.isAfter(Instant.now()) }
        val remaining = until?.let { Duration.between(Instant.now(), it) }
        val chosen = remaining?.let { holdIndex(it) } ?: -1
        val builder = frame(Menus.chest(title("Пауза $product")).rows(SMALL_ROWS), SMALL_ROWS)
        builder.slot(4, holdSummary(remaining))
        HOLDS.forEachIndexed { index, (label, duration) ->
            val item = holdChoice(label, if (index == chosen) remaining else null)
            builder.button(10 + index, item, go { who -> allowed(who, SETTINGS) { chooseHold(who, product, duration) } })
        }
        if (remaining != null) {
            builder.button(
                16,
                icon(Icons.RESET, "REDSTONE", "Снять паузу", Style.lore(listOf("Вернуть обновления сейчас."), emptyList(), Style.left("снять")), good = true),
                go { who -> allowed(who, SETTINGS) { chooseHold(who, product, null) } },
            )
        }
        builder.button(SMALL_BACK, backItem(), go { routes.card(it, product) })
        show(player, builder.build())
    }

    private fun holdIndex(remaining: Duration): Int = HOLDS.indexOfFirst { it.second >= remaining }.takeIf { it >= 0 } ?: (HOLDS.size - 1)

    private fun chooseChannel(player: MenuViewer, product: String, channel: UpdateChannel?) {
        data.setChannel(product, channel, player.actor).whenComplete { _, error ->
            sync {
                if (error != null) tell(player, ChatTone.FAILURE, "Не удалось изменить канал.", reason(error))
                else tell(player, ChatTone.SUCCESS, "Канал $product: ${channel?.name?.lowercase() ?: "как указал плагин"}.")
                routes.card(player, product)
            }
        }
    }

    private fun chooseHold(player: MenuViewer, product: String, duration: Duration?) {
        data.setHeld(product, duration, player.actor).whenComplete { _, error ->
            sync {
                if (error != null) tell(player, ChatTone.FAILURE, "Не удалось изменить паузу.", reason(error))
                else tell(player, ChatTone.SUCCESS, if (duration == null) "Пауза $product снята." else "Обновления $product приостановлены.")
                routes.card(player, product)
            }
        }
    }
}
