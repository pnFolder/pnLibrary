package org.pnfolder.pnlibrary.api.remote;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersionInfo;

/** Server and Minecraft protocol/version information exposed to a policy. */
public final class ServerInfo {
    private final String version;
    private final MinecraftVersionInfo minecraft;

    /**
     * Creates the information.
     *
     * @param version raw server-reported version string
     * @param minecraft parsed Minecraft version details
     */
    public ServerInfo(@NotNull String version, @NotNull MinecraftVersionInfo minecraft) {
        this.version = version;
        this.minecraft = minecraft;
    }

    /** Raw server-reported version string. */
    @NotNull
    public String getVersion() {
        return version;
    }

    /** Parsed Minecraft version details. */
    @NotNull
    public MinecraftVersionInfo getMinecraft() {
        return minecraft;
    }

    /** Parsed comparable Minecraft version. */
    @NotNull
    public MinecraftVersion getMinecraftVersion() {
        return minecraft.getParsed();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ServerInfo)) {
            return false;
        }
        ServerInfo that = (ServerInfo) other;
        return version.equals(that.version) && minecraft.equals(that.minecraft);
    }

    @Override
    public int hashCode() {
        return 31 * version.hashCode() + minecraft.hashCode();
    }

    @Override
    public String toString() {
        return "ServerInfo(version=" + version + ", minecraft=" + minecraft + ")";
    }
}
