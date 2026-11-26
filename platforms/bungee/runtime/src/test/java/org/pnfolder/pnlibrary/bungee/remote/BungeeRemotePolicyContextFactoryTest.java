package org.pnfolder.pnlibrary.bungee.remote;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginDescription;
import net.md_5.bungee.api.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.remote.context.RemotePolicyContext;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Checks the remote-policy context built from BungeeCord metadata. */
class BungeeRemotePolicyContextFactoryTest {
    private final PluginManager manager = mock(PluginManager.class);
    private final ProxyServer proxy = mock(ProxyServer.class);
    private final Plugin plugin = mock(Plugin.class);

    BungeeRemotePolicyContextFactoryTest() {
        when(proxy.getName()).thenReturn("BungeeCord");
        when(proxy.getVersion()).thenReturn("1.21-test");
        when(proxy.getPluginManager()).thenReturn(manager);
        PluginDescription description = new PluginDescription();
        description.setName("pnShop");
        description.setVersion("2.0");
        when(plugin.getProxy()).thenReturn(proxy);
        when(plugin.getDescription()).thenReturn(description);
    }

    @Test
    void theContextDescribesThePluginAndTheProxy() {
        RemotePolicyContext context = BungeeRemotePolicyContextFactory.create(plugin);

        assertEquals("pnshop", context.getProduct().getId());
        assertEquals("pnShop", context.getProduct().getName());
        assertEquals("2.0", context.getProduct().getVersion());
        assertEquals(PlatformType.BUNGEECORD, context.getPlatform().getType());
        assertEquals("BungeeCord", context.getPlatform().getName());
        assertEquals("1.21-test", context.getPlatform().getVersion());
    }

    @Test
    void givenValuesArePassedOnAndNativeHandlesAreReachable() {
        RemotePolicyContext context = BungeeRemotePolicyContextFactory.create(plugin,
                Collections.singletonMap("channel", "beta"));

        assertEquals("beta", context.getValues().get("channel"));
        assertSame(plugin, context.nativeHandle(Plugin.class));
        assertSame(proxy, context.nativeHandle(ProxyServer.class));
        assertSame(manager, context.nativeHandle(PluginManager.class));
    }
}
