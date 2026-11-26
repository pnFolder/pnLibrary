package org.pnfolder.pnlibrary.updates.engine.transaction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Exact persisted version/API metadata and the digest of its preserved bytes. */
public final class JournalVersion {
    private final String version;
    private final int minimumApi;
    private final int maximumApi;
    private String sha256;

    public JournalVersion(@NotNull String version, int minimumApi, int maximumApi) {
        this(version, minimumApi, maximumApi, null);
    }

    public JournalVersion(@NotNull String version, int minimumApi, int maximumApi, @Nullable String sha256) {
        this.version = Objects.requireNonNull(version, "version");
        this.minimumApi = minimumApi;
        this.maximumApi = maximumApi;
        this.sha256 = sha256;
    }

    @NotNull
    public String getVersion() {
        return version;
    }

    public int getMinimumApi() {
        return minimumApi;
    }

    public int getMaximumApi() {
        return maximumApi;
    }

    @Nullable
    public String getSha256() {
        return sha256;
    }

    public void setSha256(@Nullable String sha256) {
        this.sha256 = sha256;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JournalVersion)) {
            return false;
        }
        JournalVersion that = (JournalVersion) other;
        return minimumApi == that.minimumApi && maximumApi == that.maximumApi && version.equals(that.version)
            && Objects.equals(sha256, that.sha256);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, minimumApi, maximumApi, sha256);
    }

    @Override
    public String toString() {
        return "JournalVersion(version=" + version + ", minimumApi=" + minimumApi + ", maximumApi=" + maximumApi
            + ", sha256=" + sha256 + ")";
    }
}
