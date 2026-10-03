package ru.privatenull.pnlibrary.core.updates

import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import ru.privatenull.pnlibrary.api.updates.UpdateChannel

internal data class UpdateConfiguration(
    val enabled: Boolean = true,
    val checks: Checks = Checks(),
    val notifications: Notifications = Notifications(),
    val downloads: Downloads = Downloads(),
    val installation: Installation = Installation(),
    val safety: Safety = Safety(),
    val library: LibraryPolicy = LibraryPolicy(),
    val pluginUpdatesEnabled: Boolean = true,
    val pluginAutomaticDownload: Boolean = false,
    val plugins: Map<String, PluginPolicy> = emptyMap(),
    val legacyChannel: String = "stable",
) {
    data class LibraryPolicy(
        val channel: UpdateChannel = UpdateChannel.STABLE,
        val automaticDownload: Boolean = false,
    )
    data class PluginPolicy(
        val channel: UpdateChannel? = null,
        val enabled: Boolean = true,
        val automaticDownload: Boolean = false,
        val pauseUntil: Instant? = null,
        val disabledModules: Set<String> = emptySet(),
    )
    data class Checks(val enabled: Boolean = true, val interval: Duration = Duration.ofHours(6))
    data class Notifications(
        val console: Boolean = true,
        val administrators: Boolean = true,
        val repeatInterval: Duration = Duration.ofHours(6),
        val permission: String = "pnlibrary.updates.notify",
        val operators: Boolean = true,
    )
    data class Downloads(
        val automatic: Boolean = false,
        val allowManagedPlugins: Boolean = true,
        val allowExternalUrls: Boolean = false,
    )
    data class Installation(
        val allowNewPlugins: Boolean = false,
        val requireSha256: Boolean = true,
        val restartAfterConfirmation: Boolean = false,
        val restartCommand: String = "restart",
    )
    data class Safety(
        val maximumOnlineForOneClick: Int = 10,
        val requireSecondConfirmationAboveLimit: Boolean = true,
    )

    // Legacy `enabled: false` is a safe/manual mode, never a blindfold: checks and warnings remain active.
    val effectiveChecksEnabled get() = true
    val effectiveConsoleNotifications get() = notifications.console
    val effectiveAdministratorNotifications get() = notifications.administrators
    val effectiveAutomaticDownloads get() = enabled && (
        downloads.automatic || library.automaticDownload || pluginAutomaticDownload ||
            plugins.values.any { it.automaticDownload }
    )
    val effectiveRestart get() = enabled && installation.restartAfterConfirmation

    companion object {
        fun load(file: Path, warning: (String) -> Unit = {}): UpdateConfiguration =
            UpdateConfigurationLoader(warning).load(file)
    }

    internal fun toYaml(): String = """# pnLibrary update policy
# Проверки всегда выполняются. Флаг enabled управляет только автоматическими действиями.
updates:
  enabled: $enabled
  library:
    # Канал релизов для самой pnLibrary: stable, rc, beta, alpha или dev.
    channel: ${library.channel.name.lowercase()}
    # Разрешить автоматическую загрузку новой версии самой pnLibrary.
    automatic-download: ${library.automaticDownload}
  plugins:
    # Глобальное разрешение автоматических обновлений плагинов.
    enabled: $pluginUpdatesEnabled
    # Значение по умолчанию для новых записей в policies.
    automatic-download: $pluginAutomaticDownload
    # Здесь перечисляются плагины. Отсутствующий плагин работает в обычном режиме.
    # update: paused временно приостанавливает обновление; pause ограничен семью днями.
    # modules: необязательный список внутренних модулей плагина.
    plugins: {}
  notifications:
    # Сообщения о найденных обновлениях в консоли и администраторам.
    console: ${notifications.console}
    administrators: ${notifications.administrators}
    # Интервал повторения сообщения; интервал самой проверки задаётся библиотекой.
    repeat-interval: ${notifications.repeatInterval.toHours()}h
    permission: ${notifications.permission}
    operators: ${notifications.operators}
  downloads:
    # Автоматические загрузки файлов, объявленных через API.
    automatic: ${downloads.automatic}
    allow-managed-plugins: ${downloads.allowManagedPlugins}
    allow-external-urls: ${downloads.allowExternalUrls}
    # Любые адреса разрешены; список ограничений по хостам отсутствует.
  installation:
    allow-new-plugins: ${installation.allowNewPlugins}
    require-sha256: ${installation.requireSha256}
    restart-after-confirmation: ${installation.restartAfterConfirmation}
    restart-command: "${installation.restartCommand.replace("\"", "")}" 
  safety:
    maximum-online-for-one-click: ${safety.maximumOnlineForOneClick}
    require-second-confirmation-above-limit: ${safety.requireSecondConfirmationAboveLimit}
"""
}
