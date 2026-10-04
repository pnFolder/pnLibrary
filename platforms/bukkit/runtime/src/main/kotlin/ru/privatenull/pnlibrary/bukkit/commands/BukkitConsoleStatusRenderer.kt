package ru.privatenull.pnlibrary.bukkit.commands

import org.bukkit.Bukkit
import ru.privatenull.pnlibrary.api.runtime.PnLibraryBrand
import ru.privatenull.pnlibrary.api.updates.ReleaseSummary
import ru.privatenull.pnlibrary.api.updates.UpdateChannel
import ru.privatenull.pnlibrary.api.updates.UpdateRegistration
import ru.privatenull.pnlibrary.api.updates.UpdateSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateState
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.console.ConsoleCard
import ru.privatenull.pnlibrary.console.ConsoleTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Builds the detailed console-only pnLibrary status card. */
internal object BukkitConsoleStatusRenderer {
    fun render(entries: List<UpdateRegistration>): List<String> {
        val card = ConsoleCard.builder(THEME, "СОСТОЯНИЕ PNFOLDER")
            .mascot("^.^", "pnLibrary", "библиотека платформы")
            .blank()
            .firstDetail("Продукт", "pnLibrary")
            .detail("Назначение", "общая библиотека pnFolder")
            .detail("Платформа", "Bukkit / Paper")
            .detail("Ядро", Bukkit.getBukkitVersion())
            .detail("Java", BukkitStatusLabels.javaRuntime())
            .lastDetail("Поддержка", PnLibraryBrand.SUPPORT_URL)
            .blank()
            .divider("ОБНОВЛЕНИЯ")
            .blank()

        if (entries.isEmpty()) {
            card.lastItem("зарегистрированных обновлений нет")
        } else {
            renderRegisteredUpdates(card, entries)
        }

        return card.blank()
            .divider("ПРОВЕРКА")
            .blank()
            .firstDetail("Последняя проверка", utcNow())
            .lastDetail("Следующая проверка", "по расписанию библиотеки")
            .blank()
            .status(status(entries))
            .build()
            .render()
    }

    private fun renderRegisteredUpdates(
        card: ConsoleCard.Builder,
        entries: List<UpdateRegistration>,
    ) {
        val first = entries.first().snapshot
        card.firstDetail("Выбранный канал", BukkitStatusLabels.channel(first))
            .detail("Доступные каналы", "Stable · RC · Beta · Alpha · Dev")
            .lastDetail("Установленная версия", first.currentVersion)
            .blank()
            .divider("ПОСЛЕДНИЕ ВЕРСИИ")
            .blank()

        val selectedByProduct = entries
            .map(UpdateRegistration::snapshot)
            .associateBy { it.product.lowercase(Locale.ROOT) }

        entries.forEachIndexed { index, registration ->
            val snapshot = registration.snapshot
            val selectedVersion = selectedByProduct[snapshot.product.lowercase(Locale.ROOT)]?.latestVersion
            renderReleaseHistory(card, snapshot, selectedVersion)
            if (index != entries.lastIndex) {
                card.blank()
            }
        }

        val available = entries
            .map(UpdateRegistration::snapshot)
            .filter { snapshot -> snapshot.hasAvailableUpdate() }
        if (available.isEmpty()) {
            card.blank().lastItem("Новых совместимых обновлений не найдено")
            return
        }

        val selected = available.first()
        card.blank()
            .divider("ДОСТУПНО ОБНОВЛЕНИЕ")
            .blank()
            .firstDetail("Установлена", selected.currentVersion)
            .lastDetail("Новая версия", selected.latestVersion ?: "не указана")
            .blank()
            .divider("СВЕДЕНИЯ ОБ ОБНОВЛЕНИИ")
            .blank()
            .firstDetail("Канал", BukkitStatusLabels.channel(selected))
            .detail("Источник", if (selected.releaseUrl.isNullOrBlank()) "не указан" else "GitHub Releases")
            .detail("Платформа", "Bukkit / Paper")
            .detail(
                "Совместимость API",
                selected.supportedApi?.let { "${it.minimum}–${it.maximum}" } ?: "не указана",
            )
            .detail("Java", "${selected.requiredJava}+")
            .detail("Почему выбрана", "версия новее и совместима")
            .lastDetail(
                "Установка",
                if (selected.automaticDownload) "автоматическая" else "вручную, через /pn update",
            )
    }

    private fun renderReleaseHistory(
        card: ConsoleCard.Builder,
        snapshot: UpdateSnapshot,
        selectedVersion: String?,
    ) {
        val history = snapshot.availableReleases.toMutableList()
        mergeSelectedRelease(history, snapshot, selectedVersion ?: snapshot.latestVersion)

        val releases = history
            .groupBy(ReleaseSummary::channel)
            .mapValues { (_, values) -> values.maxByOrNull(ReleaseSummary::version) }
        val channels = DISPLAYED_CHANNELS.mapNotNull { channel ->
            releases[channel]?.let { release -> channel to release }
        }

        if (channels.isEmpty()) {
            card.firstDetail("Продукт", BukkitStatusLabels.product(snapshot.product))
                .lastDetail("Версия", snapshot.latestVersion ?: snapshot.currentVersion)
            return
        }

        card.blank()
        channels.forEachIndexed { index, (channel, release) ->
            card.section(channelLabel(channel))
                .detail("Версия", "${channelColor(channel)}${release.version}§r")
                .lastDetail(
                    "Опубликована",
                    release.publishedAt?.let(::publishedAtLabel) ?: "дата неизвестна",
                )
            if (index != channels.lastIndex) {
                card.blank()
            }
        }
    }

    private fun mergeSelectedRelease(
        history: MutableList<ReleaseSummary>,
        snapshot: UpdateSnapshot,
        selectedVersion: String?,
    ) {
        val latest = selectedVersion ?: return
        val parsedLatest = SemanticVersion.tryParse(latest) ?: return
        val current = history
            .filter { it.channel == snapshot.channel }
            .maxByOrNull { release -> SemanticVersion.tryParse(release.version) ?: ZERO_VERSION }
        val parsedCurrent = current?.let { SemanticVersion.tryParse(it.version) }
        if (parsedCurrent == null || parsedLatest > parsedCurrent) {
            history.removeAll { it.channel == snapshot.channel }
            history += ReleaseSummary(latest, snapshot.channel, null)
        }
    }

    private fun status(entries: List<UpdateRegistration>): String {
        val available = entries.firstOrNull { it.snapshot.hasAvailableUpdate() }?.snapshot
            ?: return "Все зарегистрированные компоненты актуальны"
        return "Доступно обновление · ${BukkitStatusLabels.channel(available)}"
    }

    private fun UpdateSnapshot.hasAvailableUpdate(): Boolean =
        state == UpdateState.UPDATE_AVAILABLE || state == UpdateState.AVAILABLE

    private fun publishedAtLabel(instant: Instant): String =
        "${publishedAge(instant)} · ${UTC_DATE_TIME.format(instant)}"

    private fun publishedAge(instant: Instant): String {
        val elapsed = Duration.between(instant, Instant.now()).coerceAtLeast(Duration.ZERO)
        return when {
            elapsed.toMinutes() < 60 -> "опубликована ${elapsed.toMinutes()} мин. назад"
            elapsed.toHours() < 24 -> "опубликована ${elapsed.toHours()} ч. назад"
            else -> "опубликована ${elapsed.toDays()} дн. назад"
        }
    }

    private fun utcNow(): String = UTC_DATE_TIME.format(Instant.now())

    private fun channelLabel(channel: UpdateChannel): String = when (channel) {
        UpdateChannel.STABLE -> "Стабильный канал"
        UpdateChannel.RC -> "Канал RC"
        UpdateChannel.BETA -> "Тестовый канал Beta"
        UpdateChannel.ALPHA -> "Экспериментальный канал Alpha"
        UpdateChannel.DEV -> "Разрабатываемый канал Dev"
    }

    private fun channelColor(channel: UpdateChannel): String = when (channel) {
        UpdateChannel.STABLE -> "§a"
        UpdateChannel.RC -> "§b"
        UpdateChannel.BETA -> "§e"
        UpdateChannel.ALPHA -> "§6"
        UpdateChannel.DEV -> "§c"
    }

    private val THEME = ConsoleTheme("§6", "§e", "§f", "§8", "§r")
    private val ZERO_VERSION = SemanticVersion.parse("0.0.0")
    private val DISPLAYED_CHANNELS = listOf(
        UpdateChannel.STABLE,
        UpdateChannel.RC,
        UpdateChannel.BETA,
        UpdateChannel.ALPHA,
        UpdateChannel.DEV,
    )
    private val UTC_DATE_TIME: DateTimeFormatter = DateTimeFormatter
        .ofPattern("dd.MM.yyyy HH:mm 'UTC'")
        .withZone(ZoneOffset.UTC)
}

/** Shared human-readable labels used by console and in-game status output. */
internal object BukkitStatusLabels {
    fun javaRuntime(): String {
        val version = System.getProperty("java.version")?.trim().orEmpty()
        val vm = System.getProperty("java.vm.name")?.trim().orEmpty()
        val runtime = when {
            vm.startsWith("OpenJDK", true) -> "OpenJDK"
            vm.isNotBlank() -> vm.substringBefore(" 64-Bit").trim()
            else -> System.getProperty("java.vm.vendor")?.trim().orEmpty()
        }
        return listOf(runtime, version)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .ifBlank { Runtime.version().toString() }
    }

    fun channel(snapshot: UpdateSnapshot): String = when (snapshot.channel) {
        UpdateChannel.STABLE -> "§aстабильный канал§r"
        UpdateChannel.RC -> "§bканал RC§r"
        UpdateChannel.BETA -> "§eтестовый канал Beta§r"
        UpdateChannel.ALPHA -> "§6экспериментальный канал Alpha§r"
        UpdateChannel.DEV -> "§cразрабатываемый канал Dev§r"
    }

    fun product(product: String): String =
        if (product.equals("pnlibrary", true)) "pnLibrary" else product
}
