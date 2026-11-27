package org.pnfolder.pnlibrary.spi.platform;

import org.pnfolder.pnlibrary.spi.platform.logging.PlatformLogSink;
import org.pnfolder.pnlibrary.spi.platform.logging.PlatformLogging;
import org.pnfolder.pnlibrary.spi.platform.plugin.PlatformPlugins;
import org.pnfolder.pnlibrary.spi.platform.plugin.PluginSnapshot;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.logging.LogLevel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformPluginsTest {
    private static final class NativePlugin {
        final String name;
        final String version;

        NativePlugin(String name, String version) {
            this.name = name;
            this.version = version;
        }
    }

    private final NativePlugin alpha = new NativePlugin("Alpha", "1.0");
    private final NativePlugin beta = new NativePlugin("Beta", "2.1");
    private final List<NativePlugin> installed = new ArrayList<>(Arrays.asList(alpha, beta));
    private final PlatformPlugins<NativePlugin> plugins = new PlatformPlugins<>(
            "Test",
            () -> installed,
            owner -> owner instanceof NativePlugin ? (NativePlugin) owner : null,
            plugin -> new PluginSnapshot(plugin.name, plugin.name, plugin.version));

    @Test
    void snapshotsAndVersionsDescribeEveryInstalledPlugin() {
        List<String> ids = new ArrayList<>();
        for (PluginSnapshot snapshot : plugins.snapshots()) {
            ids.add(snapshot.getId());
        }
        assertEquals(Arrays.asList("Alpha", "Beta"), ids);
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("Alpha", "1.0");
        expected.put("Beta", "2.1");
        assertEquals(expected, plugins.versions());
    }

    @Test
    void metadataOfAnUnsupportedOwnerIsLoggedOnceAndNull() {
        List<LogLevel> logged = new ArrayList<>();
        PlatformLogging logging = new PlatformLogging() {
            @NotNull
            @Override
            public PlatformLogSink getLogHandler() {
                return (owner, level, message, error) -> logged.add(level);
            }
        };

        assertEquals("2.1", plugins.metadata(beta, logging).getVersion());
        assertNull(plugins.metadata("not a plugin", logging));
        assertEquals(Collections.singletonList(LogLevel.WARNING), logged);
    }

    @Test
    void requiredOwnerMustBeANativePluginThatIsInstalled() {
        assertSame(alpha, plugins.requireInstalled(alpha, "Remote policy owner"));

        IllegalArgumentException foreign = assertThrows(IllegalArgumentException.class,
                () -> plugins.requireInstalled("text", "Remote policy owner"));
        assertEquals("Remote policy owner must be a Test plugin; received java.lang.String", foreign.getMessage());

        installed.remove(alpha);
        IllegalArgumentException removed = assertThrows(IllegalArgumentException.class,
                () -> plugins.requireInstalled(alpha, "Disabled owner"));
        assertTrue(removed.getMessage().startsWith("Disabled owner is not installed on Test"));
    }
}
