package ru.privatenull.pnlibrary.core.updates

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import ru.privatenull.pnlibrary.api.updates.UpdateHistoryEntry
import ru.privatenull.pnlibrary.api.updates.UpdateBackup
import ru.privatenull.pnlibrary.api.updates.UpdateSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateState
import java.net.URI
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Shared chat/console presentation; contains no native server calls or update mutations. */
object UpdateCatalogView {
    private val time = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC)
    private val pending = setOf(UpdateState.UPDATE_AVAILABLE, UpdateState.UPDATE_STAGED, UpdateState.DOWNLOADING)

    /** Renders one bounded page of registered components, never the native plugin inventory. */
    fun catalog(snapshots: List<UpdateSnapshot>, page: Int, updatesOnly: Boolean, command: String): List<Component> {
        require(page > 0) { "page must be positive" }
        val selected = snapshots.filter { !updatesOnly || it.state in pending }.sortedBy { it.product }
        val offset = (page.toLong() - 1) * 10
        val entries = if (offset >= selected.size) emptyList() else selected.drop(offset.toInt()).take(10)
        val result = mutableListOf<Component>(Component.text("Каталог pnLibrary", NamedTextColor.LIGHT_PURPLE)
            .append(Component.text(" · ${selected.size} компонентов · ${selected.count { it.state == UpdateState.UPDATE_AVAILABLE }} к обновлению", NamedTextColor.GRAY)))
        entries.forEach { snapshot ->
            val color = if (snapshot.state in pending) NamedTextColor.GREEN else NamedTextColor.GRAY
            var line = Component.text("${snapshot.product} · ${snapshot.currentVersion}", NamedTextColor.WHITE)
                .append(Component.text(snapshot.latestVersion?.takeIf { it != snapshot.currentVersion }?.let { " → $it" }.orEmpty(), color))
                .append(Component.text(" · ${stateLabel(snapshot.state)}", color))
                .hoverEvent(HoverEvent.showText(Component.text("Канал: ${snapshot.channel}\nАвтозагрузка: ${snapshot.automaticDownload}\n${snapshot.message.orEmpty()}")))
            val link = snapshot.latestChangelogUrl?.takeIf(::safeLink)
            if (link != null) line = line.append(Component.text(" [Изменения]", NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(link)))
            if (snapshot.state == UpdateState.UPDATE_AVAILABLE && command.startsWith("/pn updates ")) {
                line = line.append(button(" [↑]", "/pn update ${snapshot.product}", "Проверить и подготовить обновление. Применение — после перезапуска.", NamedTextColor.GREEN))
            }
            result.add(line)
        }
        if (entries.isEmpty()) result.add(Component.text("Нет компонентов на этой странице.", NamedTextColor.GRAY))
        result.add(navigation(page, offset + entries.size < selected.size, command))
        return result
    }

    /** Renders durable timestamps, initiators and outcomes without interpreting stored text as markup. */
    fun history(entries: List<UpdateHistoryEntry>, page: Int, command: String): List<Component> {
        require(page > 0) { "page must be positive" }
        val result = mutableListOf<Component>(Component.text("История pnLibrary", NamedTextColor.LIGHT_PURPLE))
        entries.forEach { event ->
            val changes = event.changes.joinToString {
                if (event.operation == "ROLLBACK") "${it.product}: откат к ${it.from ?: "не установлено"}"
                else "${it.product}: ${it.from ?: "—"} → ${it.to}"
            }
            val color = when {
                event.state == UpdateState.FAILED -> NamedTextColor.RED
                event.operation == "HEALTH" || event.operation == "HEALTH_ROLLBACK" -> NamedTextColor.GREEN
                event.state == UpdateState.UPDATE_STAGED || event.operation == "ROLLBACK" -> NamedTextColor.YELLOW
                else -> NamedTextColor.GRAY
            }
            val outcome = when {
                event.operation == "HEALTH" && event.state == UpdateState.UP_TO_DATE -> "применено после перезапуска"
                event.operation == "HEALTH_ROLLBACK" && event.state == UpdateState.ROLLED_BACK -> "откат подтверждён после перезапуска"
                else -> stateLabel(event.state)
            }
            result.add(Component.text("${time.format(event.occurredAt)} · ${event.actor.name} · ${operationLabel(event.operation)} · $outcome", color)
                .hoverEvent(HoverEvent.showText(Component.text("Автор: ${event.actor.kind} ${event.actor.id}\nПлан: ${event.planId}\n${event.message.orEmpty()}"))))
            if (changes.isNotEmpty()) result.add(Component.text("  $changes", NamedTextColor.WHITE))
            else event.message?.takeIf { it.isNotBlank() }?.let { result.add(Component.text("  ${it.take(512)}", NamedTextColor.GRAY)) }
        }
        if (entries.isEmpty()) result.add(Component.text("Нет событий на этой странице.", NamedTextColor.GRAY))
        result.add(navigation(page, entries.size == 10, command))
        return result
    }

    /** Renders retained rollback identities and original versions, without exposing server paths. */
    fun backups(backups: List<UpdateBackup>, page: Int, command: String): List<Component> {
        require(page > 0) { "page must be positive" }
        val offset = (page.toLong() - 1) * 10
        val selected = if (offset >= backups.size) emptyList() else backups.drop(offset.toInt()).take(10)
        val result = mutableListOf<Component>(Component.text("Резервные наборы pnLibrary", NamedTextColor.LIGHT_PURPLE))
        selected.forEach { backup ->
            result.add(Component.text("${backup.createdAt?.let(time::format) ?: "Дата не записана"} · ${backup.id.toString().take(8)}", NamedTextColor.GRAY)
                .hoverEvent(HoverEvent.showText(Component.text("Набор: ${backup.id}\nКонфигураций: ${backup.configurationFiles}"))))
            var line = Component.text(backup.components.joinToString { "${it.product}: ${it.updatedVersion ?: "?"} → ${it.backupVersion ?: "версия не записана"}" }, NamedTextColor.WHITE)
                .append(button(" [Откат JAR]", "${command.substringBeforeLast(' ')} rollback ${backup.id}", "Открыть подтверждение отката без изменения конфигураций", NamedTextColor.YELLOW))
            if (backup.configurationFiles > 0) line = line.append(button(" [+Конфиг]", "${command.substringBeforeLast(' ')} rollback ${backup.id} configs", "Открыть подтверждение отката JAR и выбранных конфигураций", NamedTextColor.GOLD))
            result.add(line)
        }
        if (selected.isEmpty()) result.add(Component.text("Нет сохранённых наборов на этой странице.", NamedTextColor.GRAY))
        result.add(navigation(page, offset + selected.size < backups.size, command))
        return result
    }

    private fun navigation(page: Int, hasNext: Boolean, command: String): Component {
        var line = Component.text("Страница $page", NamedTextColor.DARK_GRAY)
        if (page > 1) line = line.append(Component.text(" [Назад]", NamedTextColor.LIGHT_PURPLE).clickEvent(ClickEvent.runCommand("$command ${page - 1}")))
        if (hasNext && page < Int.MAX_VALUE) line = line.append(Component.text(" [Далее]", NamedTextColor.LIGHT_PURPLE).clickEvent(ClickEvent.runCommand("$command ${page + 1}")))
        val root = command.substringBeforeLast(' ')
        line = line.append(Component.newline())
            .append(button("[Все] ", "$root list", "Все зарегистрированные компоненты", NamedTextColor.GRAY))
            .append(button("[Обновления] ", "$root available", "Только доступные и подготовленные обновления", NamedTextColor.LIGHT_PURPLE))
            .append(button("[История] ", "$root history", "Кто, когда и что обновлял", NamedTextColor.GRAY))
            .append(button("[Резервы] ", "$root backups", "Сохранённые версии для отката", NamedTextColor.GRAY))
            .append(button("[Проверить]", if (root == "/pn updates") "/pn check" else "$root check", "Проверить новые версии", NamedTextColor.AQUA))
        return line
    }

    private fun button(label: String, command: String, hint: String, color: NamedTextColor): Component =
        Component.text(label, color).clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(hint)))

    private fun safeLink(value: String): Boolean = runCatching {
        val uri = URI(value)
        value.length <= 4096 && uri.scheme.equals("https", true) && uri.host != null && uri.userInfo == null
    }.getOrDefault(false)

    private fun stateLabel(state: UpdateState): String = when (state) {
        UpdateState.UP_TO_DATE -> "актуально"
        UpdateState.UPDATE_AVAILABLE -> "доступно обновление"
        UpdateState.UPDATE_STAGED -> "подготовлено к перезапуску"
        UpdateState.FROZEN -> "приостановлено"
        UpdateState.INCOMPATIBLE -> "несовместимо"
        UpdateState.BLOCKED -> "заблокировано"
        UpdateState.CHECKING -> "проверка"
        UpdateState.DOWNLOADING -> "загрузка"
        UpdateState.FAILED -> "ошибка"
        UpdateState.ROLLED_BACK -> "откат подготовлен к перезапуску"
    }

    private fun operationLabel(operation: String): String = when (operation) {
        "CHECK" -> "проверка"
        "STAGE" -> "обновление"
        "ROLLBACK" -> "откат"
        "HEALTH" -> "проверка запуска"
        "HEALTH_ROLLBACK" -> "проверка отката"
        else -> "состояние"
    }
}
