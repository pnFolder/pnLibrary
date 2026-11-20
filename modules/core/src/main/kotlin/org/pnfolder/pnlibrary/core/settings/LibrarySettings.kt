package org.pnfolder.pnlibrary.core.settings

import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import org.pnfolder.pnlibrary.updates.policy.UpdateDefaults
import java.time.Duration
import java.time.ZoneId

/**
 * Every setting of the library an administrator may change, with its default value.
 *
 * Each property has exactly one entry in [SettingsRegistry], which knows its key, limits and description; a test fails
 * when a property is added here without one. The values are kept by [SettingsStore] in `data/settings.json`.
 */
internal data class LibrarySettings(
    /** Whether personal data of players is left out of diagnostic reports. */
    val privacy: Boolean = false,
    /** Days the diagnostic history is kept. */
    val historyRetentionDays: Int = 30,
    /** Whether warnings and errors of plugins that are not connected to pnLibrary go to the journal too. */
    val journalForeignPlugins: Boolean = false,
    /** Zone id used for every time pnLibrary shows. */
    val timeZone: String = ZoneId.systemDefault().id,
    /** What a player sees without the permission for a command. */
    val noPermissionMessage: String = "Недостаточно прав для этой команды.",
    /** Whether plugins may download files through pnLibrary at all. */
    val downloadsEnabled: Boolean = true,
    /** Whether downloads that a plugin starts itself, without a command, are allowed. */
    val downloadsAutomatic: Boolean = true,
    /** Whether a file may be saved into the data folder of the plugin that asked for it. */
    val downloadsToDataFolder: Boolean = true,
    /** Whether a file may be saved into the shared cache of pnLibrary. */
    val downloadsToCache: Boolean = true,
    /** How often the release catalogs are asked for new versions. */
    val updateCheckInterval: Duration = UpdateDefaults.CHECK_INTERVAL,
    /** How long to wait for a connection while downloading an update file. */
    val updateConnectTimeout: Duration = UpdateDefaults.CONNECT_TIMEOUT,
    /** How long to wait for data from a silent server while downloading an update file. */
    val updateReadTimeout: Duration = UpdateDefaults.READ_TIMEOUT,
) {
    /** The runtime policy of diagnostics and command answers; everything not listed here keeps its default. */
    fun toConfig(): PnLibraryConfig = PnLibraryConfig(
        historyRetentionDays = historyRetentionDays,
        privacy = privacy,
        noPermissionMessage = noPermissionMessage,
        journalForeignPlugins = journalForeignPlugins,
        timeZone = timeZone,
    )
}
