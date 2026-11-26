package org.pnfolder.pnlibrary.bungee.remote;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginDescription;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.remote.PlatformInfo;
import org.pnfolder.pnlibrary.api.remote.ProductInfo;
import org.pnfolder.pnlibrary.api.remote.RemotePolicyContext;
import org.pnfolder.pnlibrary.api.remote.ServerInfo;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/** Creates remote-policy contexts from BungeeCord metadata. */
public final class BungeeRemotePolicyContextFactory {
    private BungeeRemotePolicyContextFactory() {
    }

    /** Builds the context of {@code plugin} without extra policy values. */
    @NotNull
    public static RemotePolicyContext create(@NotNull Plugin plugin) {
        return create(plugin, Collections.<String, String>emptyMap());
    }

    /** Builds the context of {@code plugin} and appends the caller-defined policy {@code values}. */
    @NotNull
    public static RemotePolicyContext create(@NotNull Plugin plugin, @NotNull Map<String, String> values) {
        ProxyServer proxy = plugin.getProxy();
        PluginDescription description = plugin.getDescription();
        String version = proxy.getVersion();
        return RemotePolicyContext.builder()
                .product(new ProductInfo(description.getName().toLowerCase(Locale.ROOT), description.getName(),
                        description.getVersion()))
                .platform(new PlatformInfo(PlatformType.BUNGEECORD, proxy.getName(), version))
                .server(new ServerInfo(version, MinecraftVersion.parseInfo(null)))
                .values(values)
                .nativeHandle(plugin)
                .nativeHandle(proxy)
                .nativeHandle(proxy.getPluginManager())
                .build();
    }
}
