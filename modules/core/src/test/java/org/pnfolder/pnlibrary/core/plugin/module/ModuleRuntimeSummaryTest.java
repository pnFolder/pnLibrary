package org.pnfolder.pnlibrary.core.plugin.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProviderConfiguration;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMetadata;

class ModuleRuntimeSummaryTest {
    private final PluginMetadata metadata = new PluginMetadata(
        ModuleId.of("demo"), "Demo", "1.0.0", "pnFolder", PlatformType.BUKKIT, "Paper", "21", 21);

    @Test
    void describesTheConcretePlatformWithoutLosingItsFamily() {
        assertEquals("Bukkit · Paper", ModuleRuntimeSummary.platform(metadata));
        assertEquals("Velocity", ModuleRuntimeSummary.platform(copyFor(PlatformType.VELOCITY, "Velocity")));
    }

    @Test
    void omitsServicesThatWereNotConfigured() {
        assertNull(ModuleRuntimeSummary.metrics(Collections.emptyList(), false));
        MetricsProviderConfiguration bStats = MetricsProviderConfiguration.bStats(123);
        MetricsProviderConfiguration fastStats = MetricsProviderConfiguration.fastStats("token");
        assertEquals("disabled · bStats #123", ModuleRuntimeSummary.metrics(Collections.singletonList(bStats), false));
        assertEquals("enabled · bStats #123, FastStats", ModuleRuntimeSummary.metrics(Arrays.asList(bStats, fastStats), true));
        assertEquals("disabled", ModuleRuntimeSummary.placeholderApi(false, "ready"));
        assertEquals("unavailable", ModuleRuntimeSummary.placeholderApi(true, null));
    }

    private PluginMetadata copyFor(PlatformType platform, String implementation) {
        return new PluginMetadata(metadata.getId(), metadata.getName(), metadata.getVersion(), metadata.getAuthors(), platform,
            implementation, metadata.getJavaVersion(), metadata.getJavaFeature());
    }
}
