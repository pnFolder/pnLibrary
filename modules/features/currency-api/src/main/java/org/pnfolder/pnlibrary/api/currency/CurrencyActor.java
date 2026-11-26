package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

import java.util.UUID;

/** Auditable principal that initiated a currency transaction. */
public final class CurrencyActor {
    private final CurrencyActorType type;
    private final String id;

    /** Creates an actor; {@code id} is a stable nonblank identifier within its category. */
    public CurrencyActor(@NotNull CurrencyActorType type, @NotNull String id) {
        if (CurrencyText.isBlank(id)) {
            throw new IllegalArgumentException("Currency actor ID must not be blank");
        }
        this.type = type;
        this.id = id;
    }

    /** Category used for filtering and reporting. */
    @NotNull
    public CurrencyActorType getType() {
        return type;
    }

    /** Stable nonblank identifier within the category. */
    @NotNull
    public String getId() {
        return id;
    }

    /** Creates a player actor from its stable UUID. */
    @NotNull
    public static CurrencyActor player(@NotNull UUID id) {
        return new CurrencyActor(CurrencyActorType.PLAYER, id.toString());
    }

    /** Creates an actor for a registered plugin. */
    @NotNull
    public static CurrencyActor plugin(@NotNull PluginId id) {
        return new CurrencyActor(CurrencyActorType.PLUGIN, id.getValue());
    }

    /** Creates an actor for a named application service. */
    @NotNull
    public static CurrencyActor service(@NotNull String id) {
        return new CurrencyActor(CurrencyActorType.SERVICE, id);
    }

    /** Creates the canonical current-server actor. */
    @NotNull
    public static CurrencyActor server() {
        return new CurrencyActor(CurrencyActorType.SERVER, "server");
    }

    /** Creates an internal system actor with a caller-defined identifier. */
    @NotNull
    public static CurrencyActor system(@NotNull String id) {
        return new CurrencyActor(CurrencyActorType.SYSTEM, id);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CurrencyActor)) {
            return false;
        }
        CurrencyActor that = (CurrencyActor) other;
        return type == that.type && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return 31 * type.hashCode() + id.hashCode();
    }

    @Override
    public String toString() {
        return "CurrencyActor(type=" + type + ", id=" + id + ")";
    }
}
