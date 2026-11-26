package org.pnfolder.pnlibrary.bukkit.server;

import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersionRange;

/**
 * Read-only information about a Minecraft game server.
 *
 * <p>This Bukkit-only model is intentionally absent from the cross-platform API.
 */
public final class ServerInfo {
    private final String name;
    private final String version;
    private final MinecraftVersion minecraftVersion;
    private final String rawMinecraftVersion;

    /**
     * @param name server implementation name, for example {@code Paper} or {@code Purpur}
     * @param version complete implementation version reported by the server
     * @param minecraftVersion parsed, comparable Minecraft release
     * @param rawMinecraftVersion unmodified Minecraft version string
     */
    public ServerInfo(@NotNull String name, @NotNull String version, @NotNull MinecraftVersion minecraftVersion,
                      @NotNull String rawMinecraftVersion) {
        this.name = Objects.requireNonNull(name, "name");
        this.version = Objects.requireNonNull(version, "version");
        this.minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        this.rawMinecraftVersion = Objects.requireNonNull(rawMinecraftVersion, "rawMinecraftVersion");
    }

    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public String getVersion() {
        return version;
    }

    @NotNull
    public MinecraftVersion getMinecraftVersion() {
        return minecraftVersion;
    }

    @NotNull
    public String getRawMinecraftVersion() {
        return rawMinecraftVersion;
    }

    @NotNull
    public String getDisplayName() {
        return version.trim().isEmpty() ? name : name + " " + version;
    }

    /** Returns whether the server runs {@code minimum} or a newer known release. */
    public boolean isMinecraftAtLeast(@NotNull MinecraftVersion minimum) {
        return minecraftVersion.isAtLeast(minimum);
    }

    /** Returns whether the server Minecraft version belongs to {@code range}. */
    public boolean supports(@NotNull MinecraftVersionRange range) {
        return range.contains(minecraftVersion);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ServerInfo)) {
            return false;
        }
        ServerInfo that = (ServerInfo) other;
        return name.equals(that.name) && version.equals(that.version) && minecraftVersion == that.minecraftVersion
            && rawMinecraftVersion.equals(that.rawMinecraftVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, version, minecraftVersion, rawMinecraftVersion);
    }

    @Override
    public String toString() {
        return "ServerInfo(name=" + name + ", version=" + version + ", minecraftVersion=" + minecraftVersion
            + ", rawMinecraftVersion=" + rawMinecraftVersion + ")";
    }
}
