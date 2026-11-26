package org.pnfolder.pnlibrary.api.updates.state;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;

/** Initiator recorded in update history; identity is audit metadata, not an authorization token. */
public final class UpdateActor {
    /** Internal scheduled work, not an administrator command. */
    @NotNull
    public static final UpdateActor SYSTEM = new UpdateActor(Kind.SYSTEM, "system", PnLibraryIdentity.NAME);
    /** Calls that did not supply an initiator. */
    @NotNull
    public static final UpdateActor UNKNOWN = new UpdateActor(Kind.UNKNOWN, "unknown", "Unknown");

    private final Kind kind;
    private final String id;
    private final String name;

    /**
     * Creates an actor.
     *
     * @param kind origin of the operation
     * @param id stable platform identity, such as a player UUID or console ID
     * @param name display name captured when the operation was requested
     */
    public UpdateActor(@NotNull Kind kind, @NotNull String id, @NotNull String name) {
        requireText(id, "Invalid update actor ID");
        requireText(name, "Invalid update actor name");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
    }

    private static void requireText(String value, String message) {
        if (value.trim().isEmpty() || value.length() > 256) {
            throw new IllegalArgumentException(message);
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                throw new IllegalArgumentException(message);
            }
        }
    }

    /** Origin of the operation. */
    @NotNull
    public Kind getKind() {
        return kind;
    }

    /** Stable platform identity, such as a player UUID or console ID. */
    @NotNull
    public String getId() {
        return id;
    }

    /** Display name captured when the operation was requested. */
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UpdateActor)) {
            return false;
        }
        UpdateActor that = (UpdateActor) other;
        return kind == that.kind && id.equals(that.id) && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, id, name);
    }

    @Override
    public String toString() {
        return "UpdateActor(kind=" + kind + ", id=" + id + ", name=" + name + ")";
    }

    /** Origin of an update operation. */
    public enum Kind {
        /** Internal scheduled work. */
        SYSTEM,
        /** Server or proxy console. */
        CONSOLE,
        /** Authenticated platform player. */
        PLAYER,
        /** Another integration invoking the service. */
        INTEGRATION,
        /** A caller which supplied no identity. */
        UNKNOWN
    }
}
