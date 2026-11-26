package org.pnfolder.pnlibrary.core.link.remote;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/** The answer to a {@link RemoteRequest}. */
public final class RemoteResult {
    private final UUID id;
    private final String origin;
    private final boolean ok;
    private final String text;
    private final Instant sentAt;

    /**
     * @param id the id of the request answered
     * @param origin who asked, so the answer finds its way back
     * @param ok whether the action was carried out
     * @param text what happened, for the person who asked
     * @param sentAt when the answer was made
     */
    public RemoteResult(@NotNull UUID id, @NotNull String origin, boolean ok, @NotNull String text, @NotNull Instant sentAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.ok = ok;
        this.text = Objects.requireNonNull(text, "text");
        this.sentAt = Objects.requireNonNull(sentAt, "sentAt");
    }

    public @NotNull UUID getId() {
        return id;
    }

    public @NotNull String getOrigin() {
        return origin;
    }

    public boolean getOk() {
        return ok;
    }

    public @NotNull String getText() {
        return text;
    }

    public @NotNull Instant getSentAt() {
        return sentAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RemoteResult)) {
            return false;
        }
        RemoteResult r = (RemoteResult) other;
        return ok == r.ok && id.equals(r.id) && origin.equals(r.origin) && text.equals(r.text) && sentAt.equals(r.sentAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, origin, ok, text, sentAt);
    }

    @Override
    public String toString() {
        return "RemoteResult(id=" + id + ", origin=" + origin + ", ok=" + ok + ", text=" + text + ", sentAt=" + sentAt + ")";
    }
}
