package org.pnfolder.pnlibrary.core.menu.screens

import org.pnfolder.pnlibrary.api.chat.style.ChatTone
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel
import org.pnfolder.pnlibrary.core.exceptions.rootMessage
import org.pnfolder.pnlibrary.core.menu.ItemSpec
import org.pnfolder.pnlibrary.core.menu.MenuRoutes
import org.pnfolder.pnlibrary.core.menu.MenuScreens
import org.pnfolder.pnlibrary.core.menu.MenuSession
import org.pnfolder.pnlibrary.core.menu.MenuViewer
import org.pnfolder.pnlibrary.core.menu.Menus
import org.pnfolder.pnlibrary.core.menu.style.HOLDS
import org.pnfolder.pnlibrary.core.menu.style.Icons
import org.pnfolder.pnlibrary.core.menu.style.SETTINGS
import org.pnfolder.pnlibrary.core.menu.style.SMALL_BACK
import org.pnfolder.pnlibrary.core.menu.style.SMALL_ROWS
import org.pnfolder.pnlibrary.core.menu.style.Style
import org.pnfolder.pnlibrary.core.menu.style.backItem
import org.pnfolder.pnlibrary.core.menu.style.icon
import org.pnfolder.pnlibrary.core.menu.style.menuFrame
import org.pnfolder.pnlibrary.core.menu.style.menuTitle
import org.pnfolder.pnlibrary.core.menu.style.timeLeft
import java.time.Duration
import java.time.Instant

private val CHANNEL_ITEMS = mapOf(
    UpdateChannel.STABLE to "EMERALD",
    UpdateChannel.RC to "GOLD_INGOT",
    UpdateChannel.BETA to "IRON_INGOT",
    UpdateChannel.ALPHA to "REDSTONE",
    UpdateChannel.DEV to "COAL",
)
private val CHANNEL_HINTS = mapOf(
    UpdateChannel.STABLE to "Только проверенные релизы.",
    UpdateChannel.RC to "Кандидаты в релиз и стабильные.",
    UpdateChannel.BETA to "Бета-версии, кандидаты и стабильные.",
    UpdateChannel.ALPHA to "Ранние версии: возможны ошибки.",
    UpdateChannel.DEV to "Все версии, включая ежедневные сборки.",
)

/** Menu screens for per-plugin update policies. */
internal class PluginPolicyScreens(session: MenuSession, routes: MenuRoutes) : MenuScreens(session, routes) {
    /** The channel choice. It stays in the code and is switched on by [CHANNEL_SWITCH] once more than `dev` works. */
    fun channels(player: MenuViewer, product: String) {
        val current = data.snapshot(product)?.channel
        val builder = menuFrame(Menus.chest(menuTitle("Канал $product")).rows(SMALL_ROWS), SMALL_ROWS)
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

    /** The Java build choice for a release that has several builds, for example one for Java 8 and one for Java 17. */
    fun javaBuilds(player: MenuViewer, product: String) {
        val snapshot = data.snapshot(product) ?: return routes.home(player)
        val builder = menuFrame(Menus.chest(menuTitle("Сборка Java $product")).rows(SMALL_ROWS), SMALL_ROWS)
        snapshot.javaBuilds.take(7).forEachIndexed { index, java ->
            val chosen = java == snapshot.javaBuild
            val recommended = java == snapshot.recommendedJava
            val notes = buildList {
                if (chosen) add("Состояние" to "выбрана сейчас")
                if (recommended) add("Рекомендуется" to if (snapshot.javaBuild == null) "используется" else "подходит этому серверу")
                add("На сервере" to "Java ${snapshot.currentJava}")
            }
            val lore = Style.lore(listOf("Скачать сборку для Java $java."), notes, *if (chosen) emptyArray() else arrayOf(Style.left("выбрать")))
            builder.button(
                10 + index,
                icon(Icons.SETTINGS, "COMPARATOR", "Java $java", lore, good = chosen || (recommended && snapshot.javaBuild == null)),
                go { who -> allowed(who, SETTINGS) { chooseJava(who, product, java) } },
            )
        }
        builder.button(
            16,
            icon(
                Icons.RESET, "BARRIER", "Подбирать самому",
                Style.lore(listOf("Сборка, как у установленной версии, иначе самая новая из подходящих."), emptyList(), Style.left("сбросить")), muted = true,
            ),
            go { who -> allowed(who, SETTINGS) { chooseJava(who, product, null) } },
        )
        builder.button(SMALL_BACK, backItem(), go { routes.card(it, product) })
        show(player, builder.build())
    }

    private fun chooseJava(player: MenuViewer, product: String, java: Int?) {
        data.setJavaBuild(product, java, player.actor).whenComplete { _, error ->
            sync {
                if (error != null) tell(player, ChatTone.FAILURE, "Не удалось выбрать сборку Java.", error.rootMessage())
                else tell(player, ChatTone.SUCCESS, if (java == null) "Сборка Java $product подбирается сама." else "Сборка Java $product: $java.")
                routes.card(player, product)
            }
        }
    }

    private fun holdSummary(remaining: Duration?) = icon(
        Icons.CLOCK, "CLOCK", "Пауза сейчас",
        Style.lore(
            listOf("Пока пауза идёт, обновления не предлагаются и не скачиваются."),
            listOf("Состояние" to (remaining?.let { "‖ осталось ${timeLeft(it)}" } ?: "нет паузы")),
        ),
        alternative = "WATCH", warn = remaining != null,
    )

    private fun holdChoice(label: String, selectedRemaining: Duration?): ItemSpec {
        val note = selectedRemaining?.let { listOf("Выбрано" to "● осталось ${timeLeft(it)}") } ?: emptyList()
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
        val builder = menuFrame(Menus.chest(menuTitle("Пауза $product")).rows(SMALL_ROWS), SMALL_ROWS)
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
                if (error != null) tell(player, ChatTone.FAILURE, "Не удалось изменить канал.", error.rootMessage())
                else tell(player, ChatTone.SUCCESS, "Канал $product: ${channel?.name?.lowercase() ?: "как указал плагин"}.")
                routes.card(player, product)
            }
        }
    }

    private fun chooseHold(player: MenuViewer, product: String, duration: Duration?) {
        data.setHeld(product, duration, player.actor).whenComplete { _, error ->
            sync {
                if (error != null) tell(player, ChatTone.FAILURE, "Не удалось изменить паузу.", error.rootMessage())
                else tell(player, ChatTone.SUCCESS, if (duration == null) "Пауза $product снята." else "Обновления $product приостановлены.")
                routes.card(player, product)
            }
        }
    }
}
