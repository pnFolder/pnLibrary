package org.pnfolder.pnlibrary.api.updates.release;

import java.time.Instant;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;

/** Minimal release history entry used by status views. */
public final class ReleaseSummary {
    private final String version;
    private final UpdateChannel channel;
    private final Instant publishedAt;
    private final String changelogUrl;

    /**
     * Creates the entry.
     *
     * @param version published version text
     * @param channel declared release maturity
     * @param publishedAt publisher-provided release time
     * @param changelogUrl link to the human-readable changelog for this release, when published
     */
    public ReleaseSummary(@NotNull String version, @NotNull UpdateChannel channel, @Nullable Instant publishedAt,
                          @Nullable String changelogUrl) {
        this.version = Objects.requireNonNull(version, "version");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.publishedAt = publishedAt;
        this.changelogUrl = changelogUrl;
    }

    /** Creates an entry with no publication time and no changelog. */
    public ReleaseSummary(@NotNull String version, @NotNull UpdateChannel channel) {
        this(version, channel, null, null);
    }

    /** Creates an entry with a publication time and no changelog. */
    public ReleaseSummary(@NotNull String version, @NotNull UpdateChannel channel, @Nullable Instant publishedAt) {
        this(version, channel, publishedAt, null);
    }

    /** Published version text. */
    @NotNull
    public String getVersion() {
        return version;
    }

    /** Declared release maturity. */
    @NotNull
    public UpdateChannel getChannel() {
        return channel;
    }

    /** Publisher-provided release time. */
    @Nullable
    public Instant getPublishedAt() {
        return publishedAt;
    }

    /** Link to the human-readable changelog for this release, when published. */
    @Nullable
    public String getChangelogUrl() {
        return changelogUrl;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ReleaseSummary)) {
            return false;
        }
        ReleaseSummary that = (ReleaseSummary) other;
        return version.equals(that.version) && channel == that.channel
            && Objects.equals(publishedAt, that.publishedAt) && Objects.equals(changelogUrl, that.changelogUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, channel, publishedAt, changelogUrl);
    }

    @Override
    public String toString() {
        return "ReleaseSummary(version=" + version + ", channel=" + channel + ", publishedAt=" + publishedAt
            + ", changelogUrl=" + changelogUrl + ")";
    }
}
