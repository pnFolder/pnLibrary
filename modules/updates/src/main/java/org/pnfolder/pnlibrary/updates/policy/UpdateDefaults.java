package org.pnfolder.pnlibrary.updates.policy;

import java.time.Duration;
import org.jetbrains.annotations.NotNull;

/**
 * The update rules and their default values.
 *
 * <p>Checks and warnings are never switched off: an administrator pauses one plugin with a command ({@code hold}),
 * which always expires. Every number the update system relies on lives here, so it is found in one place. The check
 * interval and the two download timeouts are only defaults: an administrator changes them with {@code /pn settings}
 * (see {@link UpdateTiming}).
 */
public final class UpdateDefaults {
    /** How often the release catalogs are asked for new versions; the first check runs at startup. */
    public static final Duration CHECK_INTERVAL = Duration.ofHours(6);

    public static final Duration MIN_REMINDER_INTERVAL = Duration.ofMinutes(1);
    public static final Duration MAX_REMINDER_INTERVAL = Duration.ofHours(6);

    /**
     * How often administrators are reminded in the console and in chat about an update that is available or waits
     * for a restart. The reminder uses the known state and never asks GitHub: the check above does that on its own
     * schedule, and a release that disappeared there stops the reminders. Only a developer changes this value,
     * between {@link #MIN_REMINDER_INTERVAL} and {@link #MAX_REMINDER_INTERVAL}.
     */
    public static final Duration REMINDER_INTERVAL = reminderInterval(Duration.ofHours(6));

    /** How long to wait for a connection while downloading an update file. */
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** How long to wait for data from a silent server while downloading an update file. */
    public static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    /** The largest single update file accepted. */
    public static final long MAX_ARTIFACT_BYTES = 512L * 1024 * 1024;

    /** Every downloaded file must carry a SHA-256 checksum. */
    public static final boolean REQUIRE_SHA256 = true;

    /** Plugins already managed by pnLibrary may be updated when installation is allowed. */
    public static final boolean ALLOW_MANAGED_PLUGINS = true;

    private UpdateDefaults() {
    }

    @NotNull
    private static Duration reminderInterval(@NotNull Duration value) {
        if (value.compareTo(MIN_REMINDER_INTERVAL) < 0 || value.compareTo(MAX_REMINDER_INTERVAL) > 0) {
            throw new IllegalArgumentException("Reminder interval must be 1 minute to 6 hours");
        }
        return value;
    }
}
