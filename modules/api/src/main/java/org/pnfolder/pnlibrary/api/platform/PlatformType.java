package org.pnfolder.pnlibrary.api.platform;

import org.jetbrains.annotations.NotNull;

/**
 * A runtime family supported by pnLibrary.
 *
 * <p>This enum intentionally contains API families only. Concrete implementations and forks are discovered by the
 * installed runtime and never affect compatibility or distribution selection.
 */
public enum PlatformType {
    /** Bukkit-compatible server family, including Paper, Spigot, and Folia-compatible runtimes. */
    BUKKIT("bukkit", "Bukkit", false),

    /** BungeeCord-compatible proxy family, including compatible forks such as Waterfall. */
    BUNGEECORD("bungeecord", "BungeeCord", true),

    /** Velocity proxy family using its native scheduler, commands, and plugin container model. */
    VELOCITY("velocity", "Velocity", true),

    /**
     * Not a real runtime family: a release artifact built for every platform at once. Valid only as
     * {@code ArtifactDescriptor.platform}; never a plugin's own identity.
     */
    UNIVERSAL("universal", "Universal", false);

    private final String id;
    private final String displayName;
    private final boolean proxy;

    PlatformType(String id, String displayName, boolean proxy) {
        this.id = id;
        this.displayName = displayName;
        this.proxy = proxy;
    }

    /** Stable lowercase identifier used in logs and diagnostics. */
    @NotNull
    public String getId() {
        return id;
    }

    /** Human-readable family name. */
    @NotNull
    public String getDisplayName() {
        return displayName;
    }

    /** Whether this family runs as a Minecraft proxy. */
    public boolean isProxy() {
        return proxy;
    }

    /** Whether this family runs a Minecraft server rather than a proxy. */
    public boolean isServer() {
        return !proxy;
    }
}
