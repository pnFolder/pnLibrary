package ru.privatenull.pnlibrary.core.updates.catalog

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import ru.privatenull.pnlibrary.api.updates.UpdateChannel
import ru.privatenull.pnlibrary.api.updates.UpdateSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateState
import ru.privatenull.pnlibrary.core.ui.ChatPalette
import java.time.Instant
import java.time.ZoneId

/** The plugin list, the server status card and the join notice: pages built from catalog rows. */
internal class CatalogScreen(routes: UpdateCatalogRoutes, now: Instant, zone: ZoneId) : CatalogStyle(routes, now, zone) {
    fun catalog(snapshots: List<UpdateSnapshot>, page: Int, updatesOnly: Boolean): List<Component> {
        require(page > 0) { "page must be positive" }
        val selected = snapshots.filter { !updatesOnly || it.state in CatalogLabels.PENDING }.sortedBy { it.product.lowercase() }
        val entries = selected.onPage(page)
        val result = mutableListOf(header(if (updatesOnly) "Обновления" else "Каталог", summary(snapshots)))
        if (routes.interactive && entries.isNotEmpty()) result += note("Нажмите на плагин, чтобы открыть его карточку.")
        result += blank()
        entries.forEach { result += rows(it) }
        if (entries.isEmpty()) result += when {
            selected.isNotEmpty() -> note("На этой странице пусто.")
            updatesOnly -> text("  Все плагины актуальны.", ChatPalette.OK)
            else -> note("Пока нет плагинов, зарегистрированных в pnLibrary.")
        }
        result += blank()
        actions(
            button("[Проверить]", routes.check, "Запросить новые версии у каталогов", ChatPalette.OK),
            if (updatesOnly) button("[Все]", routes.list, "Все зарегистрированные плагины", ChatPalette.ACCENT)
            else button("[Обновления]", routes.available, "Только доступные и подготовленные обновления", ChatPalette.ACCENT),
            button("[История]", routes.history, "Кто, когда и что обновлял", ChatPalette.ACCENT),
            button("[Резервы]", routes.backups, "Сохранённые версии для отката", ChatPalette.ACCENT),
        )?.let { result += it }
        pager(page, pageCount(selected.size), if (updatesOnly) routes.available else routes.list, "Предыдущая", "Следующая")
            ?.let { result += it }
        if (!routes.interactive) {
            result += commandHints(
                routes.check to "проверить обновления",
                routes.available to "только обновления",
                "${routes.root} info <плагин>" to "карточка плагина",
                "${routes.root} settings <плагин>" to "настройки",
                routes.history to "история",
                routes.backups to "резервы",
                routes.update?.invoke("<плагин>") to "обновить",
            )
        }
        return framed(result)
    }

    fun status(facts: List<Pair<String, String>>, snapshots: List<UpdateSnapshot>, support: String?): List<Component> {
        val result = mutableListOf(header("Состояние", if (snapshots.isEmpty()) null else summary(snapshots)), blank())
        facts.forEach { (label, value) -> result += field(label, text(value, ChatPalette.TEXT)) }
        result += blank()
        if (snapshots.isEmpty()) result += note("Пока нет плагинов, зарегистрированных в pnLibrary.")
        else snapshots.sortedBy { it.product.lowercase() }.forEach { result += rows(it) }
        result += blank()
        actions(
            button("[Каталог]", routes.list, "Все плагины с настройками и историей", ChatPalette.ACCENT),
            button("[Проверить]", routes.check, "Запросить новые версии у каталогов", ChatPalette.OK),
            support?.let(::supportButton),
        )?.let { result += it }
        return framed(result)
    }

    /** Only the components with an available or prepared update, or nothing when there are none. */
    fun announcement(snapshots: List<UpdateSnapshot>, support: String?): List<Component> {
        val actionable = snapshots.filter { it.state == UpdateState.UPDATE_AVAILABLE || it.state == UpdateState.UPDATE_STAGED }
            .sortedBy { it.product.lowercase() }
        if (actionable.isEmpty()) return emptyList()
        val result = mutableListOf(header("Обновления pnFolder", summary(actionable)), blank())
        actionable.forEach { result += rows(it) }
        result += blank()
        actions(
            button("[Обновления]", routes.available, "Доступные и подготовленные обновления", ChatPalette.ACCENT),
            button("[Проверить]", routes.check, "Повторно проверить все обновления", ChatPalette.OK),
            support?.let(::supportButton),
        )?.let { result += it }
        return framed(result)
    }

    /** Counters beside the title; zero counters are left out and nothing pending reads as all clear. */
    private fun summary(snapshots: List<UpdateSnapshot>): Component {
        val available = snapshots.count { it.state == UpdateState.UPDATE_AVAILABLE }
        val staged = snapshots.count { it.state == UpdateState.UPDATE_STAGED || it.state == UpdateState.DOWNLOADING }
        val broken = snapshots.count { it.state in CatalogLabels.PROBLEMS }
        val chips = mutableListOf(counter(snapshots.size, "плагин", "плагина", "плагинов"))
        if (available > 0) chips += counter(available, "к обновлению", "к обновлению", "к обновлению")
        if (staged > 0) chips += counter(staged, "к перезапуску", "к перезапуску", "к перезапуску", ChatPalette.ORANGE)
        if (broken > 0) chips += counter(broken, "ошибка", "ошибки", "ошибок", ChatPalette.DANGER)
        if (available + staged == 0 && snapshots.isNotEmpty()) chips += text("всё актуально", ChatPalette.OK)
        return chips.drop(1).fold(chips.first()) { line, chip -> line.append(dot()).append(chip) }
    }

    /** A plugin row: name, versions, channel and state words; the whole row opens the plugin card. */
    private fun rows(snapshot: UpdateSnapshot): List<Component> {
        val update = updateTarget(snapshot)
        val changelog = snapshot.latestChangelogUrl?.takeIf(::safeLink)?.takeIf { snapshot.state in CatalogLabels.PENDING }
        var row = text("  ").append(text(snapshot.product, ChatPalette.TEXT)).append(text(" "))
            .append(versions(snapshot.currentVersion, update, changelog?.takeIf { routes.interactive }))
        if (snapshot.channel != UpdateChannel.STABLE) row = row.append(text("  ")).append(channelTag(snapshot.channel))
        if (snapshot.state != UpdateState.UP_TO_DATE && snapshot.state != UpdateState.UPDATE_AVAILABLE) {
            row = row.append(text("  ")).append(text(CatalogLabels.shortStateLabel(snapshot.state), CatalogLabels.stateColor(snapshot.state)))
        }
        routes.update?.takeIf { snapshot.state == UpdateState.UPDATE_AVAILABLE }?.let { command ->
            row = row.append(spaced(button("[Обновить]", command(snapshot.product),
                "Скачать, проверить и подготовить ${version(update ?: "")}.\nПрименится после перезапуска.", ChatPalette.OK)))
        }
        row = row.hoverEvent(tooltip(*card(snapshot, update).toTypedArray()))
        if (routes.interactive) row = row.clickEvent(ClickEvent.runCommand(routes.info(snapshot.product)))
        val rows = mutableListOf(row)
        if (!routes.interactive && changelog != null) rows += text("      изменения: $changelog", ChatPalette.LABEL)
        return rows
    }

    private fun card(snapshot: UpdateSnapshot, update: String?): List<Component> = listOfNotNull(
        text(snapshot.product, ChatPalette.TEXT),
        hoverField("Статус", text(CatalogLabels.stateLabel(snapshot.state), CatalogLabels.stateColor(snapshot.state))),
        hoverField("Версия", versions(snapshot.currentVersion, update)),
        releasedAt(snapshot, update ?: snapshot.currentVersion)?.let { hoverField("Выпущена", text(released(it), ChatPalette.TEXT)) },
        hoverField("Канал", channelTag(snapshot.channel)),
        hoverField("Автозагрузка", text(if (snapshot.automaticDownload) "включена" else "выключена", ChatPalette.TEXT)),
        snapshot.message?.takeIf { it.isNotBlank() }?.let { text(it.take(MAX_MESSAGE), ChatPalette.LABEL) },
        blank().takeIf { routes.interactive },
        text("Нажмите, чтобы открыть карточку", ChatPalette.ACCENT).takeIf { routes.interactive },
    )
}
