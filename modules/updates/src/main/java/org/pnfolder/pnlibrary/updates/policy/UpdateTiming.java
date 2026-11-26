package org.pnfolder.pnlibrary.updates.policy;

import java.time.Duration;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** The update times an administrator may change; the library reads them once at startup. */
public final class UpdateTiming {
    private final Duration checkInterval;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    /** Creates the default times from {@link UpdateDefaults}. */
    public UpdateTiming() {
        this(UpdateDefaults.CHECK_INTERVAL, UpdateDefaults.CONNECT_TIMEOUT, UpdateDefaults.READ_TIMEOUT);
    }

    /**
     * Creates the times.
     *
     * @param checkInterval how often the release catalogs are asked for new versions
     * @param connectTimeout how long to wait for a connection while downloading an update file
     * @param readTimeout how long to wait for data from a silent server while downloading an update file
     */
    public UpdateTiming(@NotNull Duration checkInterval, @NotNull Duration connectTimeout,
                        @NotNull Duration readTimeout) {
        this.checkInterval = Objects.requireNonNull(checkInterval, "checkInterval");
        this.connectTimeout = Objects.requireNonNull(connectTimeout, "connectTimeout");
        this.readTimeout = Objects.requireNonNull(readTimeout, "readTimeout");
    }

    /** How often the release catalogs are asked for new versions. */
    @NotNull
    public Duration getCheckInterval() {
        return checkInterval;
    }

    /** How long to wait for a connection while downloading an update file. */
    @NotNull
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /** How long to wait for data from a silent server while downloading an update file. */
    @NotNull
    public Duration getReadTimeout() {
        return readTimeout;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof UpdateTiming)) {
            return false;
        }
        UpdateTiming that = (UpdateTiming) other;
        return checkInterval.equals(that.checkInterval) && connectTimeout.equals(that.connectTimeout)
            && readTimeout.equals(that.readTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(checkInterval, connectTimeout, readTimeout);
    }

    @Override
    public String toString() {
        return "UpdateTiming(checkInterval=" + checkInterval + ", connectTimeout=" + connectTimeout
            + ", readTimeout=" + readTimeout + ")";
    }
}
