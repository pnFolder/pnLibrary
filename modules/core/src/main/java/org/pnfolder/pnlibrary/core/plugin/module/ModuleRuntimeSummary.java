package org.pnfolder.pnlibrary.core.plugin.module;

import java.util.Locale;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProvider;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProviderConfiguration;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMetadata;
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration;

/** Formats runtime facts of a module into short lines for the startup summary. */
public final class ModuleRuntimeSummary {
    private ModuleRuntimeSummary() {
    }

    public static @NotNull String platform(@NotNull PluginMetadata metadata) {
        String name = metadata.getPlatform().getDisplayName();
        if (metadata.getPlatformImplementation().equalsIgnoreCase(name)) {
            return name;
        }
        return name + " · " + metadata.getPlatformImplementation();
    }

    public static @Nullable String metrics(@NotNull List<MetricsProviderConfiguration> providers, boolean enabled) {
        if (providers.isEmpty()) {
            return null;
        }
        StringBuilder labels = new StringBuilder();
        for (MetricsProviderConfiguration provider : providers) {
            if (labels.length() > 0) {
                labels.append(", ");
            }
            labels.append(providerLabel(provider));
        }
        return (enabled ? "enabled" : "disabled") + " · " + labels;
    }

    private static String providerLabel(MetricsProviderConfiguration configuration) {
        if (configuration.getProvider() == MetricsProvider.BSTATS) {
            Integer projectId = configuration.getProjectId();
            return "bStats" + (projectId != null ? " #" + projectId : "");
        }
        return "FastStats";
    }

    public static @Nullable String updates(@Nullable UpdateRegistration registration) {
        if (registration == null) {
            return null;
        }
        return registration.getSnapshot().getChannel().name() + " · " + registration.getRepository()
                + " · Java " + registration.getSnapshot().getRequiredJava() + "+";
    }

    public static @NotNull String placeholderApi(boolean enabled, @Nullable String state) {
        if (!enabled) {
            return "disabled";
        }
        return state == null ? "unavailable" : state.toLowerCase(Locale.ROOT);
    }
}
