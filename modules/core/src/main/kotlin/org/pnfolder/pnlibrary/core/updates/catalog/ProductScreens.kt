package org.pnfolder.pnlibrary.core.updates.catalog

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextColor
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.common.text.RussianText
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateChannelLabels.channelColor
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateChannelLabels.channelHint
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateChannelLabels.channelName
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** The two pages of one component: its details card and its settings card. */
internal class ProductScreens(routes: UpdateCatalogRoutes, now: Instant, zone: ZoneId) : CatalogStyle(routes, now, zone) {
    /** Versions, release date, channels, automatic download, pause and requirements, each setting linked to settings. */
    fun info(snapshot: UpdateSnapshot, heldUntil: Instant?): List<Component> {
        val product = snapshot.product
        val update = snapshot.latestVersion?.takeIf { it != snapshot.currentVersion }
        val result = mutableListOf(header(product, text(CatalogLabels.stateLabel(snapshot.state), CatalogLabels.stateColor(snapshot.state))))
        snapshot.message?.takeIf { it.isNotBlank() }?.let { result += note(it.take(MAX_MESSAGE)) }
        result += blank()
        var version = field("Версия", versions(snapshot.currentVersion, update))
        snapshot.latestChangelogUrl?.takeIf(::safeLink)?.takeIf { update != null }?.let { version = version.append(changelogButton(it)) }
        result += version
        releasedAt(snapshot, update ?: snapshot.currentVersion)?.let { result += field("Выпущена", text(released(it), ChatPalette.TEXT)) }
        result += field("Канал", channelTag(snapshot.channel)).hoverEvent(tooltip(text(channelHint(snapshot.channel), ChatPalette.TEXT)))
            .append(change(product, "Выбрать другой канал обновлений"))
        otherChannels(snapshot)?.let { result += it }
        result += field("Автозагрузка", if (snapshot.automaticDownload) text("включена", ChatPalette.OK) else text("выключена", ChatPalette.DANGER))
            .append(change(product, "Включить или выключить автоматическую загрузку"))
        val held = snapshot.state == UpdateState.FROZEN || heldUntil != null
        result += field("Пауза", if (held) text(holdText(heldUntil), ChatPalette.WARN) else text("нет", ChatPalette.TEXT))
            .append(change(product, "Приостановить обновления на время или снять паузу"))
        result += requirements(snapshot)
        result += blank()
        val updateCommand = routes.update?.takeIf { snapshot.state == UpdateState.UPDATE_AVAILABLE }?.invoke(product)
        actions(
            updateCommand?.let {
                button("[Обновить]", it, "Скачать, проверить и подготовить обновление.\nПрименится после перезапуска.", ChatPalette.OK)
            },
            button("[Настройки]", routes.settings(product), "Канал, автозагрузка и пауза обновлений", ChatPalette.ACCENT),
            button("[История]", routes.history(product), "События только этого плагина", ChatPalette.ACCENT),
            button("[Назад]", routes.list, "К списку плагинов", ChatPalette.SOFT),
        )?.let { result += it }
        if (!routes.interactive) {
            result += commandHints(
                updateCommand to "обновить",
                routes.settings(product) to "настройки",
                routes.history(product) to "история плагина",
            )
        }
        return framed(result)
    }

    /** Channel, automatic download and a pause of at most a week, each choice a button. */
    fun settings(snapshot: UpdateSnapshot, heldUntil: Instant?): List<Component> {
        val product = snapshot.product
        val result = mutableListOf(
            header(product, text("настройки", ChatPalette.LABEL)),
            text("  ${version(snapshot.currentVersion)}", ChatPalette.SOFT).append(dot()).append(channelTag(snapshot.channel)),
        )
        if (routes.interactive && !routes.editable) result += note("Только просмотр: нет права менять настройки.")
        if (clickable) result += note("Нажмите на кнопку, чтобы выбрать. ${glyph('✔')} — выбрано сейчас.")
        if (clickable) result += note("Пауза — не дольше 7 дней.")
        result += blank()
        result += choices("Канал", UpdateCatalogView.CHANNELS.map { it to channelName(it) }, snapshot.channel, ::channelColor) { channel ->
            routes.set(product, "channel", channelName(channel)) to channelHint(channel)
        }
        if (clickable) {
            actions(
                button(
                    "[Канал плагина]",
                    routes.set(product, "channel", "default"),
                    "Убрать выбор администратора: канал определяет сам плагин",
                    ChatPalette.SOFT,
                ),
            )?.let { result += it }
        }
        result += choices("Автозагрузка", listOf(true to "вкл", false to "выкл"), snapshot.automaticDownload,
            { on -> if (on) ChatPalette.OK else ChatPalette.DANGER }) { on ->
            routes.set(product, "auto", if (on) "on" else "off") to
                if (on) "Скачивать и проверять новые версии автоматически.\nПрименение — после перезапуска." else "Обновлять только по команде администратора"
        }
        result += holdRows(product, snapshot.state == UpdateState.FROZEN || heldUntil != null, heldUntil)
        result += blank()
        actions(
            button("[Карточка]", routes.info(product), "Сведения о плагине", ChatPalette.ACCENT),
            button("[История]", routes.history(product), "События только этого плагина", ChatPalette.ACCENT),
            button("[Назад]", routes.list, "К списку плагинов", ChatPalette.SOFT),
        )?.let { result += it }
        if (!routes.interactive || !routes.editable) {
            result += commandHints(
                routes.set(product, "channel",
                    "<${UpdateCatalogView.CHANNELS.joinToString("|") { channelName(it) }}|default>") to "канал (default: как задал плагин)",
                routes.set(product, "auto", "<on|off>") to "автозагрузка",
                routes.set(product, "hold", "<${UpdateCatalogView.HOLD_PERIODS.joinToString("|")}|off>") to "пауза",
            )
        }
        return framed(result)
    }

    /** The newest release of every other channel; clicking one switches to its channel. */
    private fun otherChannels(snapshot: UpdateSnapshot): Component? {
        val newest = UpdateCatalogView.CHANNELS.filter { it != snapshot.channel }.mapNotNull { channel ->
            snapshot.availableReleases.filter { it.channel == channel }
                .maxByOrNull { it.publishedAt ?: Instant.EPOCH }?.let { channel to it }
        }
        if (newest.isEmpty()) return null
        val entries = newest.map { (channel, release) ->
            val entry = channelTag(channel).append(text(" ${version(release.version)}", ChatPalette.TEXT))
            val card = listOfNotNull(
                text("${channelName(channel)} ${version(release.version)}", channelColor(channel)),
                release.publishedAt?.let { hoverField("Выпущена", text(released(it), ChatPalette.TEXT)) },
                text(channelHint(channel), ChatPalette.LABEL),
                text("» Нажмите, чтобы переключиться на ${channelName(channel)}", ChatPalette.LABEL).takeIf { clickable },
            )
            val shown = entry.hoverEvent(tooltip(*card.toTypedArray()))
            if (clickable) shown.clickEvent(ClickEvent.runCommand(routes.set(snapshot.product, "channel", channelName(channel)))) else shown
        }
        return field("Другие каналы", entries.drop(1).fold(entries.first()) { line, entry -> line.append(dot()).append(entry) })
    }

    /** Java and API requirements; a Java version the server does not meet is shown in red with the actual one. */
    private fun requirements(snapshot: UpdateSnapshot): Component {
        val javaMet = snapshot.currentJava >= snapshot.requiredJava
        var value = text("Java ${snapshot.requiredJava}+", if (javaMet) ChatPalette.TEXT else ChatPalette.DANGER)
        if (!javaMet) value = value.append(text(" (на сервере ${snapshot.currentJava})", ChatPalette.DANGER))
        snapshot.supportedApi?.let { value = value.append(dot()).append(text("API ${it.minimum}–${it.maximum}", ChatPalette.TEXT)) }
        return field("Требует", value).hoverEvent(tooltip(text("На сервере Java ${snapshot.currentJava}", ChatPalette.TEXT)))
    }

    /** The current pause with a button to lift it, and the periods to pause for or to replace the pause with. */
    private fun holdRows(product: String, held: Boolean, heldUntil: Instant?): List<Component> {
        if (!clickable) return listOf(setting("Пауза", if (held) text(holdText(heldUntil), ChatPalette.WARN) else selected("нет", ChatPalette.OK)))
        if (!held) return listOf(setting("Пауза", selected("нет", ChatPalette.OK).append(text(" ")).append(holdPeriods(product, replacing = false))))
        return listOf(
            setting("Пауза", selected(holdText(heldUntil), ChatPalette.WARN)).append(text(" "))
                .append(button("[Снять]", routes.set(product, "hold", "off"), "Снова предлагать и загружать обновления", ChatPalette.OK)),
            setting("Продлить", holdPeriods(product, replacing = true)),
        )
    }

    private fun holdPeriods(product: String, replacing: Boolean): Component = UpdateCatalogView.HOLD_PERIODS.mapIndexed { index, period ->
        val hint = if (replacing) "Пауза на ${periodLabel(period)} с этого момента" else "Приостановить обновления на ${periodLabel(period)}"
        text(if (index == 0) "" else " ").append(
            option(periodLabel(period), routes.set(product, "hold", period), "$hint.\nНовые версии не предлагаются и не загружаются.", ChatPalette.WARN),
        )
    }.fold(Component.empty()) { line, period -> line.append(period) }

    private fun holdText(until: Instant?): String =
        if (until == null) "задана в updates.yml" else "до ${fullTime(until)} · ${RussianText.remaining(Duration.between(now, until))}"

    /** `3d` as `3 д`, the way periods read everywhere else. */
    private fun periodLabel(period: String): String = period.dropLast(1) + when (period.last()) {
        'h' -> " ч"
        'd' -> " д"
        else -> " мин"
    }

    /** A small button that opens the settings card, shown after the setting it changes. */
    private fun change(product: String, hint: String): Component =
        if (!clickable) Component.empty() else text(" ").append(button("[изменить]", routes.settings(product), hint, ChatPalette.ACCENT))

    /**
     * A row of mutually exclusive choices drawn as buttons, each in its own color: the current one is `[✔ name]` in
     * its color, the others have gray brackets and switch to that choice on click.
     */
    private fun <T> choices(
        label: String,
        options: List<Pair<T, String>>,
        current: T,
        color: (T) -> TextColor,
        command: (T) -> Pair<String, String>,
    ): Component {
        val shown = options.map { (value, name) ->
            when {
                value == current -> selected(name, color(value))
                clickable -> {
                    val (run, hint) = command(value)
                    bracketed(name, color(value)).clickEvent(ClickEvent.runCommand(run))
                        .hoverEvent(tooltip(text("Выбрать: $name", color(value)), text(hint, ChatPalette.TEXT), blank(), text(run, ChatPalette.LABEL)))
                }
                else -> text(name, ChatPalette.FAINT)
            }
        }
        return setting(label, shown.drop(1).fold(shown.first()) { line, option -> line.append(text(" ")).append(option) })
    }
}
