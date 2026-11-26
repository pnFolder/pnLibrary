package org.pnfolder.pnlibrary.core.link.remote;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A request for an action sent to the server called {@code target}. */
public final class RemoteRequest {
    private final UUID id;
    private final String target;
    private final String origin;
    private final String actor;
    private final RemoteAction action;
    private final String product;
    private final Instant sentAt;

    /**
     * @param id unique id, which also stops a recorded request from being played again
     * @param target the server that should do it, or {@link LinkNames#PROXY}
     * @param origin the server that asked; the proxy fills it in from the connection the request came on
     * @param actor the name of the person who asked, for the update history
     * @param action what to do
     * @param product the plugin, for actions that need one
     * @param sentAt when the request was made
     */
    public RemoteRequest(@NotNull UUID id, @NotNull String target, @NotNull String origin, @NotNull String actor,
                         @NotNull RemoteAction action, @Nullable String product, @NotNull Instant sentAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.target = Objects.requireNonNull(target, "target");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.action = Objects.requireNonNull(action, "action");
        this.product = product;
        this.sentAt = Objects.requireNonNull(sentAt, "sentAt");
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

    public @NotNull String getActor() {
        return actor;
    }

    public @NotNull RemoteAction getAction() {
        return action;
    }

    public @Nullable String getProduct() {
        return product;
    }

    public @NotNull Instant getSentAt() {
        return sentAt;
    }

    /** The same request with another origin. */
    public @NotNull RemoteRequest withOrigin(@NotNull String newOrigin) {
        return new RemoteRequest(id, target, newOrigin, actor, action, product, sentAt);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RemoteRequest)) {
            return false;
        }
        RemoteRequest r = (RemoteRequest) other;
        return id.equals(r.id) && target.equals(r.target) && origin.equals(r.origin) && actor.equals(r.actor)
            && action == r.action && Objects.equals(product, r.product) && sentAt.equals(r.sentAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, target, origin, actor, action, product, sentAt);
    }

    @Override
    public String toString() {
        return "RemoteRequest(id=" + id + ", target=" + target + ", origin=" + origin + ", actor=" + actor
            + ", action=" + action + ", product=" + product + ", sentAt=" + sentAt + ")";
    }
}
