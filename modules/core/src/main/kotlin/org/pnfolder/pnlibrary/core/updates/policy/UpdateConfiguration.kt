package org.pnfolder.pnlibrary.core.updates.policy

import org.pnfolder.pnlibrary.api.config.ConfigProblem
import org.pnfolder.pnlibrary.api.config.ConfigValueValidator
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.core.config.LibraryConfigFiles
import java.nio.file.Path
import java.time.Duration
import java.util.Locale
import java.util.function.Supplier

/**
 * Administrator update policy from `updates.yml`: notifications, downloads and installation limits.
 *
 * Per-plugin channel, automatic download and temporary pause are chosen with commands and kept in
 * `updates/`; the file only lists plugins that must never be updated ([ignoredPlugins]).
 */
internal data class UpdateConfiguration(
    val enabled: Boolean = true,
    val checks: Checks = Checks(),
    val notifications: Notifications = Notifications(),
    val downloads: Downloads = Downloads(),
    val installation: Installation = Installation(),
    val library: LibraryPolicy = LibraryPolicy(),
    val pluginUpdatesEnabled: Boolean = true,
    val pluginAutomaticDownload: Boolean = false,
    val ignoredPlugins: Set<String> = emptySet(),
) {
    data class LibraryPolicy(
        val channel: UpdateChannel = UpdateChannel.STABLE,
        val automaticDownload: Boolean = false,
    )
    data class Checks(val enabled: Boolean = true, val interval: Duration = Duration.ofHours(6))
    data class Notifications(
        val console: Boolean = true,
        val repeatInterval: Duration = Duration.ofHours(6),
    )
    data class Downloads(
        val automatic: Boolean = false,
        val allowManagedPlugins: Boolean = true,
    )
    data class Installation(
        val allowNewPlugins: Boolean = false,
        val requireSha256: Boolean = true,
    )

    // `enabled: false` is a safe/manual mode, never a blindfold: checks and warnings remain active.
    val effectiveChecksEnabled get() = true
    val effectiveConsoleNotifications get() = notifications.console
    val effectiveAutomaticDownloads get() = enabled && (
        downloads.automatic || library.automaticDownload || pluginAutomaticDownload
    )

    companion object {
        private val VALIDATOR = ConfigValueValidator<UpdateSettings> { settings ->
            settings.plugins.ignored
                .filterNot { UpdateSettings.PLUGIN_ID.matches(it.trim().lowercase(Locale.ROOT)) }
                .map { ConfigProblem("plugins.ignored", "недопустимый идентификатор плагина: $it") }
        }

        /**
         * Reads the policy from [file], creating the file with defaults when it is missing.
         *
         * An unreadable file never grants more rights: conservative defaults are used and [warning] says why.
         */
        fun load(file: Path, warning: (String) -> Unit = {}): UpdateConfiguration = try {
            LibraryConfigFiles.load(
                file = file,
                type = UpdateSettings::class.java,
                defaults = Supplier { UpdateSettings() },
                validator = VALIDATOR,
                warning = warning,
            ).toConfiguration()
        } catch (error: Exception) {
            warning("updates.yml не прочитан, используются безопасные значения по умолчанию: ${error.message}")
            UpdateConfiguration()
        }
    }
}
