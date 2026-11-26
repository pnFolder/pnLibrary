package org.pnfolder.pnlibrary.core.plugin.module;

import java.util.Arrays;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.LoggingService;
import org.pnfolder.pnlibrary.api.logging.MessageBox;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterRegistry;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.lifecycle.LifecycleRow;
import org.pnfolder.pnlibrary.api.plugin.lifecycle.PluginLifecycle;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMetadata;

/** Prints the startup and shutdown summary of a module and registers its placeholders on enable. */
public final class ModuleLifecycleReporter implements PluginLifecycle {
    private final Object owner;
    private final ModuleId moduleId;
    private final PluginMetadata metadata;
    private final ModuleResources resources;
    private final LoggingService logging;
    private final PlaceholderAdapterRegistry placeholders;
    private final boolean placeholderApiEnabled;
    private final BooleanSupplier closed;

    public ModuleLifecycleReporter(Object owner, ModuleId moduleId, PluginMetadata metadata, ModuleResources resources,
                                   LoggingService logging, PlaceholderAdapterRegistry placeholders, boolean placeholderApiEnabled,
                                   BooleanSupplier closed) {
        this.owner = owner;
        this.moduleId = moduleId;
        this.metadata = metadata;
        this.resources = resources;
        this.logging = logging;
        this.placeholders = placeholders;
        this.placeholderApiEnabled = placeholderApiEnabled;
        this.closed = closed;
    }

    @Override
    public @NotNull PluginMetadata getMetadata() {
        return metadata;
    }

    @Override
    public @NotNull MessageBox enabled() {
        String placeholderState = null;
        if (placeholders.get("placeholderapi") != null) {
            placeholderState = placeholders.get("placeholderapi").isAvailable() ? "available" : "unavailable";
        }
        return new HidingLifecycleBox(logging.box(owner, metadata.getName(), metadata.getVersion()), Arrays.asList(
                ok(LifecycleRow.IDENTIFIER, "Identifier", moduleId.getValue()),
                ok(LifecycleRow.PLATFORM, "Platform", ModuleRuntimeSummary.platform(metadata)),
                status(LifecycleRow.METRICS, "Metrics", ModuleRuntimeSummary.metrics(
                        resources.getMetrics().getProviderConfigurations(), resources.getMetrics().isEnabled())),
                status(LifecycleRow.UPDATES, "Updates", ModuleRuntimeSummary.updates(resources.getUpdates())),
                status(LifecycleRow.DIAGNOSTICS, "Diagnostics", resources.getDiagnostics() == null ? null : "enabled"),
                status(LifecycleRow.PLACEHOLDER_API, "PlaceholderAPI",
                        ModuleRuntimeSummary.placeholderApi(placeholderApiEnabled, placeholderState))));
    }

    @Override
    public @NotNull MessageBox disabled() {
        boolean isClosed = closed.getAsBoolean();
        String updates = resources.getUpdates() == null ? null : isClosed ? "stopped" : "registered";
        return new HidingLifecycleBox(logging.shutdownBox(owner, metadata.getName(), metadata.getVersion()), Arrays.asList(
                status(LifecycleRow.RESOURCES, "Resources", isClosed ? "released" : "close pending"),
                status(LifecycleRow.UPDATES, "Updates", updates),
                status(LifecycleRow.METRICS, "Metrics", shutdownMetrics(isClosed))));
    }

    private @Nullable String shutdownMetrics(boolean isClosed) {
        if (resources.getMetrics().getProviderConfigurations().isEmpty()) {
            return null;
        }
        if (isClosed) {
            return "stopped";
        }
        return ModuleRuntimeSummary.metrics(resources.getMetrics().getProviderConfigurations(), resources.getMetrics().isEnabled());
    }

    private static HidingLifecycleBox.AutoRow ok(LifecycleRow row, final String label, final String detail) {
        return new HidingLifecycleBox.AutoRow(row, false, box -> box.ok(label, detail));
    }

    private static HidingLifecycleBox.AutoRow status(LifecycleRow row, final String label, @Nullable final String detail) {
        if (detail == null) {
            return new HidingLifecycleBox.AutoRow(row, true, box -> box.skip(label, "not configured"));
        }
        return new HidingLifecycleBox.AutoRow(row, false, box -> box.ok(label, detail));
    }
}
