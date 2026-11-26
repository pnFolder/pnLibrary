package org.pnfolder.pnlibrary.bukkit.remote;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.remote.context.PlatformInfo;
import org.pnfolder.pnlibrary.api.remote.context.ProductInfo;
import org.pnfolder.pnlibrary.api.remote.context.RemotePolicyContext;
import org.pnfolder.pnlibrary.api.remote.context.ServerInfo;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;

/** Creates remote-policy contexts from Bukkit runtime metadata. */
public final class BukkitRemotePolicyContextFactory {
    private BukkitRemotePolicyContextFactory() {
    }

    /** Builds a context for {@code plugin} without extra policy values. */
    @NotNull
    public static RemotePolicyContext create(@NotNull Plugin plugin) {
        return create(plugin, Collections.emptyMap());
    }

    /**
     * Builds a context for {@code plugin} and appends caller-defined policy {@code values}.
     *
     * @param plugin Bukkit plugin that owns the policy evaluation
     * @param values additional non-sensitive values exposed to policy rules
     */
    @NotNull
    public static RemotePolicyContext create(@NotNull Plugin plugin, @NotNull Map<String, String> values) {
        Server server = plugin.getServer();
        String version = server.getVersion();
        return RemotePolicyContext.builder()
            .product(new ProductInfo(plugin.getName().toLowerCase(Locale.ROOT), plugin.getName(),
                plugin.getDescription().getVersion()))
            .platform(new PlatformInfo(PlatformType.BUKKIT, server.getName(), version))
            .server(new ServerInfo(version, MinecraftVersion.parseInfo(version)))
            .values(values)
            .nativeHandle(plugin)
            .nativeHandle(server)
            .nativeHandle(server.getPluginManager())
            .build();
    }
}
