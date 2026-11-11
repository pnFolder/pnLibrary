package org.pnfolder.pnlibrary.core.updates.policy

import org.pnfolder.pnlibrary.api.config.ConfigProblem
import org.pnfolder.pnlibrary.api.config.ConfigValueValidator
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.config.LibraryConfigFiles
import java.nio.file.Path
import java.time.Duration
import java.util.Locale
import java.util.function.Supplier

/**
 * Update policy: the few choices in `updates.yml` plus the fixed rules of [UpdateDefaults].
 *
 * Per-plugin channel, automatic download and temporary pause are chosen with commands and kept in
 * `updates/`; the file lists automatic downloads, new plugins and plugins that must never be updated ([ignoredPlugins]).
 */
internal data class UpdateConfiguration(
    val checks: Checks = Checks(),
    val notifications: Notifications = Notifications(),
    val downloads: Downloads = Downloads(),
    val installation: Installation = Installation(),
    val library: LibraryPolicy = LibraryPolicy(),
    val pluginAutomaticDownload: Boolean = false,
    val ignoredPlugins: Set<String> = emptySet(),
) {
    data class LibraryPolicy(
        val channel: UpdateChannel = UpdateChannel.DEV,
        val automaticDownload: Boolean = false,
    )
    data class Checks(val interval: Duration = UpdateDefaults.CHECK_INTERVAL)
    data class Notifications(val repeatInterval: Duration = UpdateDefaults.NOTIFICATION_REPEAT)
    data class Downloads(
        val allowManagedPlugins: Boolean = UpdateDefaults.ALLOW_MANAGED_PLUGINS,
        val connectTimeout: Duration = UpdateDefaults.CONNECT_TIMEOUT,
        val readTimeout: Duration = UpdateDefaults.READ_TIMEOUT,
        val maxArtifactBytes: Long = UpdateDefaults.MAX_ARTIFACT_BYTES,
    )
    data class Installation(
        val allowNewPlugins: Boolean = false,
        val requireSha256: Boolean = UpdateDefaults.REQUIRE_SHA256,
    )

    /** Whether any component may be downloaded without a command. */
    val effectiveAutomaticDownloads get() = library.automaticDownload || pluginAutomaticDownload

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
