package org.pnfolder.pnlibrary.core.downloads

import org.pnfolder.pnlibrary.api.config.ConfigComment
import org.pnfolder.pnlibrary.api.config.ConfigDefaultOnInvalid
import org.pnfolder.pnlibrary.api.downloads.DownloadDestination

/** Content of `plugins/pnLibrary/downloads.yml`: rules for files that plugins download through pnLibrary. */
@ConfigComment(
    "pnLibrary: загрузка файлов (downloads.yml)",
    "",
    "Правила для файлов, которые плагины скачивают через API pnLibrary.",
    "Адреса скачивания не ограничиваются; ограничиваются только действия и места сохранения.",
)
/**
 * Configuration section that restricts which download actions plugins may perform through pnLibrary.
 */
internal class DownloadSettings {
    @field:ConfigDefaultOnInvalid
    @field:ConfigComment("Включить систему загрузок. false: все запросы плагинов на загрузку отклоняются.")
    var enabled: Boolean = true

    @field:ConfigDefaultOnInvalid
    @field:ConfigComment("Разрешить загрузки, которые плагин запускает сам, без команды администратора.")
    var automatic: Boolean = false

    @field:ConfigComment("Куда разрешено сохранять скачанные файлы.")
    var destinations: Destinations = Destinations()

    /** Switches for the places a downloaded file may be saved to. */
    class Destinations {
        @field:ConfigDefaultOnInvalid
        @field:ConfigComment("Папка данных плагина, который запросил файл.")
        var dataFolder: Boolean = true

        @field:ConfigDefaultOnInvalid
        @field:ConfigComment("Общий кэш pnLibrary.")
        var cache: Boolean = true
    }

    /** Converts the file model to the immutable runtime policy. */
    fun toConfiguration(): DownloadConfiguration = DownloadConfiguration(
        enabled = enabled,
        automatic = automatic,
        destinations = buildSet {
            if (destinations.dataFolder) add(DownloadDestination.DATA_FOLDER)
            if (destinations.cache) add(DownloadDestination.CACHE)
        },
    )
}
