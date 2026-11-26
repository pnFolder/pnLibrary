package org.pnfolder.pnlibrary.core.plugin.module;

import org.pnfolder.pnlibrary.core.plugin.module.definition.ModuleDefinitionBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProvider;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProviderConfiguration;

class ModuleDefinitionMetricsTest {

    @Test
    void separateProviderDeclarationsRetainBothBackendsInEitherOrder() {
        ModuleDefinitionBuilder bStatsFirst = new ModuleDefinitionBuilder();
        bStatsFirst.metrics(it -> it.bStats(10));
        bStatsFirst.metrics(it -> it.fastStats("test-token"));

        ModuleDefinitionBuilder fastStatsFirst = new ModuleDefinitionBuilder();
        fastStatsFirst.metrics(it -> it.fastStats("test-token"));
        fastStatsFirst.metrics(it -> it.bStats(10));

        for (ModuleDefinitionBuilder definition : Arrays.asList(bStatsFirst, fastStatsFirst)) {
            Set<MetricsProvider> providers = EnumSet.noneOf(MetricsProvider.class);
            for (MetricsProviderConfiguration configuration : definition.getMetrics().getProviders()) {
                providers.add(configuration.getProvider());
            }
            assertEquals(EnumSet.of(MetricsProvider.BSTATS, MetricsProvider.FASTSTATS), providers);
            assertEquals(10, bStats(definition).getProjectId());
            assertEquals(true, definition.getMetrics().isEnabled());
        }
    }

    @Test
    void duplicateProviderDeclarationFailsWithoutChangingExistingSetup() {
        ModuleDefinitionBuilder definition = new ModuleDefinitionBuilder();
        definition.metrics(it -> it.bStats(10));

        assertThrows(IllegalArgumentException.class, () -> definition.metrics(it -> it.bStats(20)));
        assertEquals(1, definition.getMetrics().getProviders().size());
        assertEquals(10, definition.getMetrics().getProviders().get(0).getProjectId());
    }

    private static MetricsProviderConfiguration bStats(ModuleDefinitionBuilder definition) {
        for (MetricsProviderConfiguration configuration : definition.getMetrics().getProviders()) {
            if (configuration.getProvider() == MetricsProvider.BSTATS) {
                return configuration;
            }
        }
        throw new AssertionError("no bStats provider");
    }
}
