package org.pnfolder.pnlibrary.velocity.remote;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.ProxyVersion;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.remote.PlatformInfo;
import org.pnfolder.pnlibrary.api.remote.ProductInfo;
import org.pnfolder.pnlibrary.api.remote.RemotePolicyContext;
import org.pnfolder.pnlibrary.api.remote.ServerInfo;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;

import java.util.Collections;
import java.util.Map;

/** Creates remote-policy contexts from Velocity runtime metadata. */
public final class VelocityRemotePolicyContextFactory {
    private VelocityRemotePolicyContextFactory() {
    }

    /** Builds a context for {@code plugin} on {@code server} without extra policy values. */
    @NotNull
    public static RemotePolicyContext create(
            @NotNull Object plugin, @NotNull PluginContainer container, @NotNull ProxyServer server) {
        return create(plugin, container, server, Collections.<String, String>emptyMap());
    }

    /** Builds a context for {@code plugin} on {@code server} and appends the caller-defined policy {@code values}. */
    @NotNull
    public static RemotePolicyContext create(
            @NotNull Object plugin,
            @NotNull PluginContainer container,
            @NotNull ProxyServer server,
            @NotNull Map<String, String> values) {
        PluginDescription descriptor = container.getDescription();
        ProxyVersion proxy = server.getVersion();
        ProductInfo product = new ProductInfo(descriptor.getId(), descriptor.getName().orElse(descriptor.getId()),
                descriptor.getVersion().orElse("unknown"));
        PlatformInfo platform = new PlatformInfo(PlatformType.VELOCITY, proxy.getName(), proxy.getVersion());
        ServerInfo runtime = new ServerInfo(proxy.getVersion(), MinecraftVersion.parseInfo(null));
        return RemotePolicyContext.builder()
                .product(product)
                .platform(platform)
                .server(runtime)
                .values(values)
                .nativeHandle(plugin)
                .nativeHandle(server)
                .nativeHandle(server.getPluginManager())
                .build();
    }
}
