package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

import java.util.regex.Pattern;

/** Globally unique currency identity composed of its owning plugin and local name. */
public final class CurrencyKey {
    private static final Pattern NAME = Pattern.compile("[a-z0-9_.-]+");

    private final PluginId owner;
    private final String name;

    /**
     * Creates a key; {@code name} is a normalized lowercase local name using letters, digits, {@code _}, {@code .},
     * or {@code -}.
     */
    public CurrencyKey(@NotNull PluginId owner, @NotNull String name) {
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid currency name: " + name);
        }
        this.owner = owner;
        this.name = name;
    }

    /** Plugin that controls the registration lifecycle. */
    @NotNull
    public PluginId getOwner() {
        return owner;
    }

    /** Normalized lowercase local name. */
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CurrencyKey)) {
            return false;
        }
        CurrencyKey that = (CurrencyKey) other;
        return owner.equals(that.owner) && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return 31 * owner.hashCode() + name.hashCode();
    }

    /** Returns the canonical {@code owner:name} reference accepted by {@link CurrencyService#get(String)}. */
    @Override
    public String toString() {
        return owner.getValue() + ":" + name;
    }
}
