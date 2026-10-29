package org.pnfolder.pnlibrary.core.updates.policy

import org.pnfolder.pnlibrary.api.config.ConfigComment
import org.pnfolder.pnlibrary.api.config.ConfigDefaultOnInvalid
import org.pnfolder.pnlibrary.api.config.ConfigNewLine
import org.pnfolder.pnlibrary.api.config.ConfigPattern
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import java.util.Locale

/** Content of `plugins/pnLibrary/updates.yml`: what pnLibrary downloads and installs on its own. */
@ConfigComment(
    "pnLibrary: обновления (updates.yml)",
    "",
    "Проверки, уведомления и защита файлов работают всегда и отключаются только командой на время (hold).",
    "Канал, автозагрузку и паузу конкретного плагина меняют командами:",
    "  set <плагин> channel <канал>, set <плагин> auto <on|off>, set <плагин> hold <период>.",
)
/**
 * Configuration section with the few update choices an administrator makes in a file.
 */
internal class UpdateSettings {
    @field:ConfigNewLine
    @field:ConfigComment("=== Сама pnLibrary ===")
    var library: Library = Library()

    @field:ConfigNewLine
    @field:ConfigComment("=== Плагины, которые обновляет pnLibrary ===")
    var plugins: Plugins = Plugins()

    @field:ConfigNewLine
    @field:ConfigComment("=== Установка ===")
    var installation: Installation = Installation()

    /** Update policy of pnLibrary itself. */
    class Library {
        @field:ConfigComment("Канал релизов pnLibrary сейчас фиксирован (dev) и это значение не применяется.")
        @field:ConfigPattern(CHANNEL_PATTERN)
        var channel: String = "dev"

        @field:ConfigDefaultOnInvalid
        @field:ConfigComment("Скачивать новую версию pnLibrary автоматически.")
        var automaticDownload: Boolean = false
    }

    /** Update policy shared by the plugins registered in pnLibrary. */
    class Plugins {
        @field:ConfigDefaultOnInvalid
        @field:ConfigComment("Скачивать обновления плагинов автоматически.")
        var automaticDownload: Boolean = false

        @field:ConfigComment(
            "Плагины, которые pnLibrary не обновляет никогда (их идентификаторы). Например: [pncases].",
            "Остальные плагины обновляются по правилам выше.",
        )
        var ignored: List<String> = emptyList()
    }

    /** Safety rules of installation. */
    class Installation {
        @field:ConfigDefaultOnInvalid
        @field:ConfigComment("Разрешить устанавливать плагины, которых на сервере ещё нет.")
        var allowNewPlugins: Boolean = false
    }

    /** Converts the file model to the immutable runtime policy. */
    fun toConfiguration(): UpdateConfiguration = UpdateConfiguration(
        installation = UpdateConfiguration.Installation(allowNewPlugins = installation.allowNewPlugins),
        library = UpdateConfiguration.LibraryPolicy(
            // TODO: применять library.channel, когда появятся стабильные релизы pnLibrary.
            channel = UpdateChannel.DEV,
            automaticDownload = library.automaticDownload,
        ),
        pluginAutomaticDownload = plugins.automaticDownload,
        ignoredPlugins = plugins.ignored.map { it.trim().lowercase(Locale.ROOT) }.toSet(),
    )

    companion object {
        /** Accepted plugin identifier in `plugins.ignored`. */
        val PLUGIN_ID = Regex("[a-z0-9][a-z0-9_.-]*")

        private const val CHANNEL_PATTERN = "(?i)stable|rc|beta|alpha|dev"
    }
}
