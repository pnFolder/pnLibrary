package org.pnfolder.pnlibrary.common.time;

import org.jetbrains.annotations.NotNull;

import java.time.ZoneId;

/**
 * Time zone in which every time shown to a person is written. Stored times are always UTC instants; only the display
 * changes. The runtime sets it from the {@code time-zone} setting at startup and when it changes.
 */
public final class DisplayZone {
    private static volatile ZoneId zone = ZoneId.systemDefault();

    private DisplayZone() {
    }

    /** The zone used for display; defaults to the system zone until the runtime sets it. */
    @NotNull
    public static ZoneId getZone() {
        return zone;
    }

    /** Sets the zone used for display from now on. */
    public static void setZone(@NotNull ZoneId value) {
        zone = value;
    }
}
