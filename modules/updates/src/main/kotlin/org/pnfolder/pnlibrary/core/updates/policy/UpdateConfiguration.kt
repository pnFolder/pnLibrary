package org.pnfolder.pnlibrary.core.updates.policy

import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import java.time.Duration

/**
 * Update policy: fixed rules from [UpdateDefaults]. Nothing here is read from a file; an administrator changes the
 * channel, automatic download and pause of a plugin with commands or in the menu, and those choices are kept by
 * [UpdatePolicyOverrides].
 */
internal data class UpdateConfiguration(
    val checks: Checks = Checks(),
    val downloads: Downloads = Downloads(),
    val installation: Installation = Installation(),
    val library: LibraryPolicy = LibraryPolicy(),
    val pluginAutomaticDownload: Boolean = true,
) {
    data class LibraryPolicy(
        val channel: UpdateChannel = UpdateChannel.DEV,
        val automaticDownload: Boolean = true,
    )
    data class Checks(val interval: Duration = UpdateDefaults.CHECK_INTERVAL)
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
}
