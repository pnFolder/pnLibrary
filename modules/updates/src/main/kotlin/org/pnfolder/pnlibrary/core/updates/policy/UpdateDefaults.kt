package org.pnfolder.pnlibrary.core.updates.policy

import java.time.Duration

/**
 * Fixed update rules that administrators cannot change.
 *
 * Checks and warnings are never switched off by a file: an administrator pauses one plugin with a command
 * (`hold`), which always expires. Every number the update system relies on lives here, so it is found in one place.
 */
object UpdateDefaults {
    /** How often the release catalogs are asked for new versions; the first check runs at startup. */
    val CHECK_INTERVAL: Duration = Duration.ofHours(6)

    /**
     * How often administrators are reminded in the console and in chat about an update that is available or waits
     * for a restart. The reminder uses the known state and never asks GitHub: the check above does that on its own
     * schedule, and a release that disappeared there stops the reminders. Only a developer changes this value, between
     * [MIN_REMINDER_INTERVAL] and [MAX_REMINDER_INTERVAL].
     */
    val REMINDER_INTERVAL: Duration = reminderInterval(Duration.ofHours(6))

    val MIN_REMINDER_INTERVAL: Duration = Duration.ofMinutes(1)
    val MAX_REMINDER_INTERVAL: Duration = Duration.ofHours(6)

    private fun reminderInterval(value: Duration): Duration {
        require(value >= Duration.ofMinutes(1) && value <= Duration.ofHours(6)) { "Reminder interval must be 1 minute to 6 hours" }
        return value
    }

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
