package org.pnfolder.pnlibrary.spi.platform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.platform.PlatformType;

/** Stable identity information for a native platform adapter. */
public interface PlatformIdentity {
    /** Platform family this adapter serves. */
    @NotNull
    PlatformType getType();

    /** Stable platform identifier. */
    @NotNull
    default String getId() {
        return getType().getId();
    }

    /** Human-readable platform name. */
    @NotNull
    default String getImplementationName() {
        return getType().getDisplayName();
    }

    /** Whether the platform is a proxy such as BungeeCord or Velocity. */
    default boolean isProxy() {
        return getType().isProxy();
    }

    /** Whether the platform is a game server such as Bukkit. */
    default boolean isServer() {
        return getType().isServer();
    }

    /** Minecraft version of the game server such as {@code 1.21.4}; null on a proxy or when it cannot be read. */
    @Nullable
    default String getMinecraftVersion() {
        return null;
    }

    /** Stable suffix used by the installed distribution artifact. */
    @NotNull
    default String getArtifactId() {
        switch (getType()) {
            case BUKKIT:
                return "bukkit";
            case BUNGEECORD:
                return "bungee";
            case VELOCITY:
                return "velocity";
            default:
                throw new IllegalStateException("UNIVERSAL is a release artifact, not a plugin identity");
        }
    }

    /** Native descriptor file used when validating downloaded plugin JARs. */
    @NotNull
    default String getDescriptorFileName() {
        switch (getType()) {
            case BUKKIT:
                return "plugin.yml";
            case BUNGEECORD:
                return "bungee.yml";
            case VELOCITY:
                return "velocity-plugin.json";
            default:
                throw new IllegalStateException("UNIVERSAL is a release artifact, not a plugin identity");
        }
    }
}
