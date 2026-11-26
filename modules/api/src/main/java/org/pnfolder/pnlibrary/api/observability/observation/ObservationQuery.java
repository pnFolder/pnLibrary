package org.pnfolder.pnlibrary.api.observability.observation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Selects observations from the shared chronological journal.
 *
 * <p>Every non-null property narrows the result. Implementations return the newest matching observations first and
 * never return more than {@link #getLimit()}.
 */
public final class ObservationQuery {
    private final int limit;
    private final String plugin;
    private final ObservationLevel minimumLevel;
    private final Long since;
    private final Long until;

    /** The newest 50 observations of every plugin. */
    public ObservationQuery() {
        this(50, null, null, null, null);
    }

    /** The newest {@code limit} observations of every plugin. */
    public ObservationQuery(int limit) {
        this(limit, null, null, null, null);
    }

    /**
     * Creates a query.
     *
     * @param limit maximum number of observations to return
     * @param plugin optional logical plugin name
     * @param minimumLevel optional lowest accepted severity
     * @param since optional inclusive lower timestamp in epoch milliseconds
     * @param until optional inclusive upper timestamp in epoch milliseconds
     */
    public ObservationQuery(int limit, @Nullable String plugin, @Nullable ObservationLevel minimumLevel,
                            @Nullable Long since, @Nullable Long until) {
        this.limit = limit;
        this.plugin = plugin;
        this.minimumLevel = minimumLevel;
        this.since = since;
        this.until = until;
    }

    /** Maximum number of observations to return. */
    public int getLimit() {
        return limit;
    }

    /** The logical plugin name, or {@code null}. */
    @Nullable
    public String getPlugin() {
        return plugin;
    }

    /** The lowest accepted severity, or {@code null}. */
    @Nullable
    public ObservationLevel getMinimumLevel() {
        return minimumLevel;
    }

    /** The inclusive lower timestamp in epoch milliseconds, or {@code null}. */
    @Nullable
    public Long getSince() {
        return since;
    }

    /** The inclusive upper timestamp in epoch milliseconds, or {@code null}. */
    @Nullable
    public Long getUntil() {
        return until;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ObservationQuery)) {
            return false;
        }
        ObservationQuery that = (ObservationQuery) other;
        return limit == that.limit && Objects.equals(plugin, that.plugin) && minimumLevel == that.minimumLevel
                && Objects.equals(since, that.since) && Objects.equals(until, that.until);
    }

    @Override
    public int hashCode() {
        return Objects.hash(limit, plugin, minimumLevel, since, until);
    }

    @NotNull
    @Override
    public String toString() {
        return "ObservationQuery(limit=" + limit + ", plugin=" + plugin + ", minimumLevel=" + minimumLevel
                + ", since=" + since + ", until=" + until + ")";
    }
}
