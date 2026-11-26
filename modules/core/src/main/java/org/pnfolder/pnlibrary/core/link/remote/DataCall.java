package org.pnfolder.pnlibrary.core.link.remote;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/** A question one server puts to another about its library, or a thing it asks it to do. */
public final class DataCall {
    private final UUID id;
    private final String target;
    private final String origin;
    private final String viewer;
    private final String method;
    private final String args;
    private final Instant sentAt;

    /**
     * @param id identifies the question and its answer
     * @param target the server that answers, or {@link LinkNames#PROXY}
     * @param origin the server that asks; the proxy fills it in from the connection the question came on
     * @param viewer the name of the person on whose behalf it is asked, for the update history
     * @param method what is asked
     * @param args the arguments, as JSON
     * @param sentAt when the question was made
     */
    public DataCall(@NotNull UUID id, @NotNull String target, @NotNull String origin, @NotNull String viewer,
                    @NotNull String method, @NotNull String args, @NotNull Instant sentAt) {
        this.id = id;
        this.target = target;
        this.origin = origin;
        this.viewer = viewer;
        this.method = method;
        this.args = args;
        this.sentAt = sentAt;
    }

    public @NotNull UUID getId() {
        return id;
    }

    public @NotNull String getTarget() {
        return target;
    }

    public @NotNull String getOrigin() {
        return origin;
    }

    public @NotNull String getViewer() {
        return viewer;
    }

    public @NotNull String getMethod() {
        return method;
    }

    public @NotNull String getArgs() {
        return args;
    }

    public @NotNull Instant getSentAt() {
        return sentAt;
    }

    /** The same question with another origin. */
    public @NotNull DataCall withOrigin(@NotNull String newOrigin) {
        return new DataCall(id, target, newOrigin, viewer, method, args, sentAt);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DataCall)) {
            return false;
        }
        DataCall c = (DataCall) other;
        return id.equals(c.id) && target.equals(c.target) && origin.equals(c.origin) && viewer.equals(c.viewer)
            && method.equals(c.method) && args.equals(c.args) && sentAt.equals(c.sentAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, target, origin, viewer, method, args, sentAt);
    }

    @Override
    public String toString() {
        return "DataCall(id=" + id + ", target=" + target + ", origin=" + origin + ", viewer=" + viewer
            + ", method=" + method + ", sentAt=" + sentAt + ")";
    }
}
