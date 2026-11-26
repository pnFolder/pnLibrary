package org.pnfolder.pnlibrary.api.downloads;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Immutable status of one declared download. */
public final class DownloadSnapshot {
    private final String key;
    private final DownloadState state;
    private final String message;

    /**
     * @param key application-defined download identifier
     * @param state current lifecycle state
     * @param message optional diagnostic or blocking explanation
     */
    public DownloadSnapshot(@NotNull String key, @NotNull DownloadState state, @Nullable String message) {
        this.key = Objects.requireNonNull(key, "key");
        this.state = Objects.requireNonNull(state, "state");
        this.message = message;
    }

    /** A snapshot without a message. */
    public DownloadSnapshot(@NotNull String key, @NotNull DownloadState state) {
        this(key, state, null);
    }

    /** The application-defined download identifier. */
    public @NotNull String getKey() {
        return key;
    }

    /** The current lifecycle state. */
    public @NotNull DownloadState getState() {
        return state;
    }

    /** The optional diagnostic or blocking explanation. */
    public @Nullable String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DownloadSnapshot)) {
            return false;
        }
        DownloadSnapshot that = (DownloadSnapshot) other;
        return key.equals(that.key) && state == that.state && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, state, message);
    }

    @Override
    public String toString() {
        return "DownloadSnapshot(key=" + key + ", state=" + state + ", message=" + message + ")";
    }
}
