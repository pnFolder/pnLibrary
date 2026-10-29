package org.pnfolder.pnlibrary.core.updates.policy

import java.time.Duration

/**
 * Fixed update rules that administrators cannot change in `updates.yml`.
 *
 * Checks and warnings are never switched off by a file: an administrator pauses one plugin with a command
 * (`hold`), which always expires. Every number the update system relies on lives here, so it is found in one place.
 */
internal object UpdateDefaults {
    /** How often the release catalogs are asked for new versions; the first check runs at startup. */
    val CHECK_INTERVAL: Duration = Duration.ofHours(6)

    /** How long the same console message about an update is not repeated. */
    val NOTIFICATION_REPEAT: Duration = Duration.ofHours(6)

    /** How long to wait for a connection while downloading an update file. */
    val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)

    /** How long to wait for data from a silent server while downloading an update file. */
    val READ_TIMEOUT: Duration = Duration.ofSeconds(30)

    /** The largest single update file accepted. */
    const val MAX_ARTIFACT_BYTES: Long = 512L * 1024 * 1024

    /** Every downloaded file must carry a SHA-256 checksum. */
    const val REQUIRE_SHA256: Boolean = true

    /** Plugins already managed by pnLibrary may be updated when installation is allowed. */
    const val ALLOW_MANAGED_PLUGINS: Boolean = true
}
