package org.pnfolder.pnlibrary.velocity.remote;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.ProxyVersion;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.remote.context.RemotePolicyContext;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Checks the remote-policy context built from Velocity metadata. */
class VelocityRemotePolicyContextFactoryTest {
    private final ProxyServer server = mock(ProxyServer.class);

    VelocityRemotePolicyContextFactoryTest() {
        when(server.getVersion()).thenReturn(new ProxyVersion("Velocity", "Velocity Team", "3.4-test"));
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
    }

    private static PluginContainer container(Optional<String> name, Optional<String> version) {
        PluginDescription description = mock(PluginDescription.class);
        when(description.getId()).thenReturn("pnshop");
        when(description.getName()).thenReturn(name);
        when(description.getVersion()).thenReturn(version);
        PluginContainer container = mock(PluginContainer.class);
        when(container.getDescription()).thenReturn(description);
        return container;
    }

    @Test
    void theContextDescribesThePluginAndTheProxy() {
        RemotePolicyContext context = VelocityRemotePolicyContextFactory.create(
                new Object(), container(Optional.of("pnShop"), Optional.of("2.0")), server);

        assertEquals("pnshop", context.getProduct().getId());
        assertEquals("pnShop", context.getProduct().getName());
        assertEquals("2.0", context.getProduct().getVersion());
        assertEquals(PlatformType.VELOCITY, context.getPlatform().getType());
        assertEquals("Velocity", context.getPlatform().getName());
        assertEquals("3.4-test", context.getPlatform().getVersion());
    }

    @Test
    void aPluginWithoutANameOrVersionStillHasAProduct() {
        RemotePolicyContext context = VelocityRemotePolicyContextFactory.create(
                new Object(), container(Optional.empty(), Optional.empty()), server);

        assertEquals("pnshop", context.getProduct().getName());
        assertEquals("unknown", context.getProduct().getVersion());
    }

    @Test
    void givenValuesArePassedOnAndNativeHandlesAreReachable() {
        Object plugin = new Object();
        RemotePolicyContext context = VelocityRemotePolicyContextFactory.create(plugin,
                container(Optional.empty(), Optional.empty()), server,
                Collections.singletonMap("channel", "beta"));

        assertEquals("beta", context.getValues().get("channel"));
        assertSame(plugin, context.nativeHandle(Object.class));
        assertSame(server, context.nativeHandle(ProxyServer.class));
    }
}
