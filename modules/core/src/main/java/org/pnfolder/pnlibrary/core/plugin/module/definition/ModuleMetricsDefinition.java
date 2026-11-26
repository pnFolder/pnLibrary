package org.pnfolder.pnlibrary.core.plugin.module.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProvider;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProviderConfiguration;

/** Metrics settings declared by a module: providers, enabled flag and extra configurers. */
public final class ModuleMetricsDefinition {
    private final List<MetricsProviderConfiguration> providers;
    private final boolean enabled;
    private final List<Consumer<PluginMetrics>> configurers;

    public ModuleMetricsDefinition() {
        this(Collections.emptyList(), false, Collections.emptyList());
    }

    public ModuleMetricsDefinition(@NotNull List<MetricsProviderConfiguration> providers, boolean enabled,
                                   @NotNull List<Consumer<PluginMetrics>> configurers) {
        if (providers.size() > 2) {
            throw new IllegalArgumentException("At most two metrics providers are supported");
        }
        Set<MetricsProvider> kinds = new HashSet<>();
        for (MetricsProviderConfiguration provider : providers) {
            kinds.add(provider.getProvider());
        }
        if (kinds.size() != providers.size()) {
            throw new IllegalArgumentException(
                    "Each metrics provider may be configured only once; combine bStats and FastStats in one metrics block");
        }
        this.providers = Collections.unmodifiableList(new ArrayList<>(providers));
        this.enabled = enabled;
        this.configurers = Collections.unmodifiableList(new ArrayList<>(configurers));
    }

    public @NotNull List<MetricsProviderConfiguration> getProviders() {
        return providers;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public @NotNull List<Consumer<PluginMetrics>> getConfigurers() {
        return configurers;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ModuleMetricsDefinition)) {
            return false;
        }
        ModuleMetricsDefinition that = (ModuleMetricsDefinition) other;
        return enabled == that.enabled && providers.equals(that.providers) && configurers.equals(that.configurers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providers, enabled, configurers);
    }

    @Override
    public String toString() {
        return "ModuleMetricsDefinition{providers=" + providers + ", enabled=" + enabled + ", configurers=" + configurers + '}';
    }
}
