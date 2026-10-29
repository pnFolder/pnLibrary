package org.pnfolder.pnlibrary.core.updates.policy

import org.pnfolder.pnlibrary.api.config.ConfigComment
import org.pnfolder.pnlibrary.api.config.ConfigDefaultOnInvalid
import org.pnfolder.pnlibrary.api.config.ConfigNewLine
import org.pnfolder.pnlibrary.api.config.ConfigPattern
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import java.time.Duration
import java.util.Locale

/** Content of `plugins/pnLibrary/updates.yml`: what pnLibrary downloads and installs on its own. */
@ConfigComment(
    "pnLibrary: обновления (updates.yml)",
    "",
    "Проверка новых версий выполняется всегда. Параметры ниже управляют загрузкой и установкой.",
    "Канал, автозагрузку и временную паузу конкретного плагина меняют командами:",
    "  set <плагин> channel <канал>, set <плагин> auto <on|off>, set <плагин> hold <период>.",
)
/**
 * Configuration section that controls update checks, downloads and installation.
 */
internal class UpdateSettings {
    @field:ConfigDefaultOnInvalid
    @field:ConfigComment("Главный выключатель автоматических действий. false: только проверки и уведомления.")
    var enabled: Boolean = true

    @field:ConfigNewLine
    @field:ConfigComment("=== Сама pnLibrary ===")
    var library: Library = Library()

    @field:ConfigNewLine
    @field:ConfigComment("=== Плагины, которые обновляет pnLibrary ===")
    var plugins: Plugins = Plugins()

    @field:ConfigNewLine
    @field:ConfigComment("=== Проверка новых версий ===")
    var checks: Checks = Checks()

    @field:ConfigNewLine
    @field:ConfigComment("=== Уведомления о найденных обновлениях ===")
    var notifications: Notifications = Notifications()

    @field:ConfigNewLine
    @field:ConfigComment("=== Загрузка ===")
    var downloads: Downloads = Downloads()

    @field:ConfigNewLine
    @field:ConfigComment("=== Установка ===")
    var installation: Installation = Installation()

    /** Update policy of pnLibrary itself. */
    class Library {
        @field:ConfigComment("Канал релизов: stable, rc, beta, alpha или dev. Пока у pnLibrary нет стабильных релизов, нужен dev.")
        @field:ConfigPattern(CHANNEL_PATTERN)
        var channel: String = "dev"

        @field:ConfigDefaultOnInvalid
        @field:ConfigComment("Скачивать новую версию pnLibrary автоматически.")
        var automaticDownload: Boolean = false
    }

    /** Update policy shared by the plugins registered in pnLibrary. */
    class Plugins {
        @field:ConfigDefaultOnInvalid
        @field:ConfigComment("Обновлять плагины. false: обновления плагинов отключены совсем.")
        var enabled: Boolean = true

        @field:ConfigDefaultOnInvalid
        @field:ConfigComment("Скачивать обновления плагинов автоматически.")
        var automaticDownload: Boolean = false

        @field:ConfigComment(
            "Плагины, которые pnLibrary не обновляет никогда (их идентификаторы). Например: [pncases].",
            "Остальные плагины обновляются по правилам выше.",
        )
        var ignored: List<String> = emptyList()
    }

    /** How often the release catalogs are asked for new versions. */
    class Checks {
        @field:ConfigComment(
            "Как часто проверять новые версии: число и единица s, m, h, d или w (например, 30m, 6h, 1d).",
            "Первая проверка идёт сразу при старте. Меньше 5m не принимается: так GitHub не заблокирует сервер.",
        )
        @field:ConfigPattern(DURATION_PATTERN)
        var interval: String = "6h"
    }

    /** Messages about found updates. */
    class Notifications {
        @field:ConfigDefaultOnInvalid
        @field:ConfigComment("Писать о найденных обновлениях в консоль.")
        var console: Boolean = true

        @field:ConfigComment("Как часто повторять одно и то же сообщение: число и единица s, m, h, d или w (например, 30m, 6h, 1d).")
        @field:ConfigPattern(DURATION_PATTERN)
        var repeatInterval: String = "6h"
    }

    /** Which downloads pnLibrary may start itself. */
    class Downloads {
        @field:ConfigDefaultOnInvalid
        @field:ConfigComment("Скачивать обновления без команды администратора.")
        var automatic: Boolean = false

        @field:ConfigDefaultOnInvalid
        @field:ConfigComment("Разрешить обновлять плагины, которыми управляет pnLibrary.")
        var allowManagedPlugins: Boolean = true

        @field:ConfigComment("Сколько ждать соединения при скачивании файла обновления (например, 10s).")
        @field:ConfigPattern(DURATION_PATTERN)
        var connectTimeout: String = "10s"

        @field:ConfigComment("Сколько ждать данных при скачивании файла обновления, если сервер молчит (например, 30s).")
        @field:ConfigPattern(DURATION_PATTERN)
        var readTimeout: String = "30s"

        @field:ConfigComment("Наибольший размер одного файла обновления в мегабайтах, от 1 до 1024.")
        var maxArtifactMegabytes: Int = 512
    }

    /** Safety rules of installation. */
    class Installation {
        @field:ConfigDefaultOnInvalid
        @field:ConfigComment("Разрешить устанавливать плагины, которых на сервере ещё нет.")
        var allowNewPlugins: Boolean = false

        @field:ConfigDefaultOnInvalid
        @field:ConfigComment("Требовать контрольную сумму SHA-256 у каждого скачанного файла.")
        var requireSha256: Boolean = true
    }

    /** Converts the file model to the immutable runtime policy. */
    fun toConfiguration(): UpdateConfiguration = UpdateConfiguration(
        enabled = enabled,
        checks = UpdateConfiguration.Checks(
            interval = parseDuration(checks.interval).coerceAtLeast(MINIMUM_CHECK_INTERVAL),
        ),
        notifications = UpdateConfiguration.Notifications(
            console = notifications.console,
            repeatInterval = parseDuration(notifications.repeatInterval),
        ),
        downloads = UpdateConfiguration.Downloads(
            automatic = downloads.automatic,
            allowManagedPlugins = downloads.allowManagedPlugins,
            connectTimeout = parseDuration(downloads.connectTimeout),
            readTimeout = parseDuration(downloads.readTimeout),
            maxArtifactBytes = downloads.maxArtifactMegabytes.coerceIn(1, 1024) * BYTES_PER_MEGABYTE,
        ),
        installation = UpdateConfiguration.Installation(
            allowNewPlugins = installation.allowNewPlugins,
            requireSha256 = installation.requireSha256,
        ),
        library = UpdateConfiguration.LibraryPolicy(
            channel = UpdateChannel.valueOf(library.channel.trim().uppercase(Locale.ROOT)),
            automaticDownload = library.automaticDownload,
        ),
        pluginUpdatesEnabled = plugins.enabled,
        pluginAutomaticDownload = plugins.automaticDownload,
        ignoredPlugins = plugins.ignored.map { it.trim().lowercase(Locale.ROOT) }.toSet(),
    )

    companion object {
        /** Accepted plugin identifier in `plugins.ignored`. */
        val PLUGIN_ID = Regex("[a-z0-9][a-z0-9_.-]*")

        private const val CHANNEL_PATTERN = "(?i)stable|rc|beta|alpha|dev"
        private const val DURATION_PATTERN = "[1-9][0-9]*[smhdw]"
        private const val BYTES_PER_MEGABYTE = 1024L * 1024L
        private val MINIMUM_CHECK_INTERVAL: Duration = Duration.ofMinutes(5)

        private fun parseDuration(value: String): Duration {
            val amount = value.dropLast(1).toLong()
            return when (value.last()) {
                's' -> Duration.ofSeconds(amount)
                'm' -> Duration.ofMinutes(amount)
                'h' -> Duration.ofHours(amount)
                'd' -> Duration.ofDays(amount)
                else -> Duration.ofDays(amount * 7)
            }
        }
    }
}
