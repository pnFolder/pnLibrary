package org.pnfolder.pnlibrary.bukkit.commands

import org.bukkit.Bukkit
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration
import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.api.updates.release.ReleaseSummary
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import org.pnfolder.pnlibrary.common.text.RussianText
import org.pnfolder.pnlibrary.console.ConsoleCard
import org.pnfolder.pnlibrary.core.ui.ConsoleStyle
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateChannelLabels
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Builds the detailed console-only pnLibrary status card. */
internal object BukkitConsoleStatusRenderer {
    /** Builds the console status card from registered update snapshots. */
    fun render(entries: List<UpdateRegistration>): List<String> {
        val card = ConsoleCard.builder(ConsoleStyle.THEME, "СОСТОЯНИЕ PNFOLDER")
            .mascot("^.^", PnLibraryIdentity.NAME, "библиотека платформы")
            .blank()
            .firstDetail("Продукт", PnLibraryIdentity.NAME)
            .detail("Назначение", "общая библиотека pnFolder")
            .detail("Платформа", "Bukkit / Paper")
            .detail("Ядро", Bukkit.getBukkitVersion())
            .detail("Java", BukkitStatusLabels.javaRuntime())
            .lastDetail("Поддержка", PnLibraryIdentity.SUPPORT_URL)
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

    /** Appends channel settings, release history, and the selected update. */
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

    /** Displays the latest known release in each configured display channel. */
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
            card.section(UpdateChannelLabels.channelTitle(channel).replaceFirstChar { it.titlecase(Locale.ROOT) })
                .detail("Версия", ConsoleStyle.paint(release.version, UpdateChannelLabels.channelColor(channel)))
                .lastDetail(
                    "Опубликована",
                    release.publishedAt?.let(::publishedAtLabel) ?: "дата неизвестна",
                )
            if (index != channels.lastIndex) {
                card.blank()
            }
        }
    }

    /** Ensures the selected compatible release is represented in the displayed history. */
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

    /** Summarizes whether a registered product has an available update. */
    private fun status(entries: List<UpdateRegistration>): String {
        val available = entries.firstOrNull { it.snapshot.hasAvailableUpdate() }?.snapshot
            ?: return "Все зарегистрированные компоненты актуальны"
        return "Доступно обновление · ${BukkitStatusLabels.channel(available)}"
    }

    /** Checks whether the snapshot represents a downloadable update. */
    private fun UpdateSnapshot.hasAvailableUpdate(): Boolean =
        state == UpdateState.UPDATE_AVAILABLE

    /** Combines the publication age with an absolute UTC timestamp. */
    private fun publishedAtLabel(instant: Instant): String =
        "${publishedAge(instant)} · ${UTC_DATE_TIME.format(instant)}"

    /** Formats how long ago a release was published, in the wording shared with the chat catalog. */
    private fun publishedAge(instant: Instant): String {
        return "опубликована ${RussianText.ago(Duration.between(instant, Instant.now()))}"
    }

    /** Formats the current instant in the console UTC format. */
    private fun utcNow(): String = UTC_DATE_TIME.format(Instant.now())

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
    /** Reads the JVM implementation and version from runtime properties. */
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
            .ifBlank { System.getProperty("java.runtime.version", "unknown") }
    }

    /** Returns the localized, colored channel label for a snapshot. */
    fun channel(snapshot: UpdateSnapshot): String =
        ConsoleStyle.paint(UpdateChannelLabels.channelTitle(snapshot.channel), UpdateChannelLabels.channelColor(snapshot.channel))

    /** Returns the branded library name or the registered product name. */
    fun product(product: String): String =
        if (product.equals(PnLibraryIdentity.PRODUCT_ID, true)) PnLibraryIdentity.NAME else product
}
