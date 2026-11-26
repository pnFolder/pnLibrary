package org.pnfolder.pnlibrary.core.plugin.module.definition;

import org.pnfolder.pnlibrary.core.plugin.module.UpdateRequestBinding;
import org.pnfolder.pnlibrary.core.plugin.module.minecraft.MinecraftSupportRule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.compat.declaration.MinecraftVersions;
import org.pnfolder.pnlibrary.api.compat.declaration.NewerAction;
import org.pnfolder.pnlibrary.api.compat.declaration.UnsupportedAction;
import org.pnfolder.pnlibrary.api.dependencies.PluginDependency;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticContainer;
import org.pnfolder.pnlibrary.api.downloads.FileDownloads;
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsBuilder;
import org.pnfolder.pnlibrary.api.plugin.PluginBuilder;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMetadataBuilder;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginOptions;
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest;

/**
 * Mutable definition assembled by the public {@link PluginBuilder} DSL.
 *
 * <p>The registry consumes this object only after the caller has finished configuring a module. Keeping the definition
 * separate makes registration orchestration and user-facing configuration two distinct responsibilities.
 */
public final class ModuleDefinitionBuilder implements PluginBuilder {
    private RemotePolicySettings remotePolicy;
    private String metadataName;
    private String metadataVersion;
    private String metadataAuthors;
    private String metadataDescription;
    private ModuleMetricsDefinition metrics = new ModuleMetricsDefinition();
    private Path diagnosticsDirectory;
    private DiagnosticContainer diagnosticContainer;
    private PluginUpdateRequest updateRequest;
    private ProductDescriptor productDescriptor;
    private final List<PluginDependency> dependencies = new ArrayList<>();
    private FileDownloads downloadsRequest;
    private boolean placeholderApiEnabled = true;
    private final MinecraftSupportRule minecraftRule = new MinecraftSupportRule();
    private final Map<ModuleLibrary, BooleanSupplier> libraries = new LinkedHashMap<>();
    private final List<Consumer<ModuleLibrary.Builder>> libraryDefaults = new ArrayList<>();

    public @Nullable RemotePolicySettings getRemotePolicy() {
        return remotePolicy;
    }

    public @Nullable String getMetadataName() {
        return metadataName;
    }

    public @Nullable String getMetadataVersion() {
        return metadataVersion;
    }

    public @Nullable String getMetadataAuthors() {
        return metadataAuthors;
    }

    public @Nullable String getMetadataDescription() {
        return metadataDescription;
    }

    public @NotNull ModuleMetricsDefinition getMetrics() {
        return metrics;
    }

    public @Nullable Path getDiagnosticsDirectory() {
        return diagnosticsDirectory;
    }

    public @Nullable DiagnosticContainer getDiagnosticContainer() {
        return diagnosticContainer;
    }

    public @Nullable PluginUpdateRequest getUpdateRequest() {
        return updateRequest;
    }

    public @Nullable ProductDescriptor getProductDescriptor() {
        return productDescriptor;
    }

    public @NotNull List<PluginDependency> getDependencies() {
        return dependencies;
    }

    public @Nullable FileDownloads getDownloadsRequest() {
        return downloadsRequest;
    }

    public boolean isPlaceholderApiEnabled() {
        return placeholderApiEnabled;
    }

    public @NotNull MinecraftSupportRule getMinecraftRule() {
        return minecraftRule;
    }

    public @NotNull Map<ModuleLibrary, BooleanSupplier> getLibraries() {
        return libraries;
    }

    @Override
    public void supportedMinecraft(@NotNull MinecraftVersions versions) {
        minecraftRule.add(versions);
    }

    @Override
    public void onUnsupportedMinecraft(@NotNull UnsupportedAction action) {
        minecraftRule.setUnsupportedAction(action);
    }

    @Override
    public void onNewerMinecraft(@NotNull NewerAction action) {
        minecraftRule.setNewerAction(action);
    }

    @Override
    public @NotNull PluginBuilder library(@NotNull ModuleLibrary library) {
        return library(library, () -> true);
    }

    @Override
    public @NotNull PluginBuilder library(@NotNull ModuleLibrary library, @NotNull BooleanSupplier enabled) {
        libraries.put(library, enabled);
        return this;
    }

    @Override
    public @NotNull PluginBuilder libraryDefaults(@NotNull Consumer<ModuleLibrary.Builder> configure) {
        libraryDefaults.add(configure);
        return this;
    }

    @Override
    public @NotNull PluginBuilder library(@NotNull String group, @NotNull String artifact, @NotNull String version,
                                          @NotNull Consumer<ModuleLibrary.Builder> configure) {
        ModuleLibrary.Builder builder = ModuleLibrary.builder(group, artifact, version);
        for (Consumer<ModuleLibrary.Builder> defaults : libraryDefaults) {
            defaults.accept(builder);
        }
        configure.accept(builder);
        return library(builder.build());
    }

    @Override
    public @NotNull PluginBuilder product(@NotNull ProductDescriptor descriptor) {
        if (productDescriptor != null) {
            throw new IllegalArgumentException("Component descriptor is already configured");
        }
        productDescriptor = descriptor;
        return this;
    }

    @Override
    public @NotNull PluginBuilder depends(@NotNull PluginDependency dependency) {
        if (dependency.getManaged() == null && dependency.getExternal() == null) {
            throw new IllegalArgumentException("Plugin dependency must provide a managed or external dependency");
        }
        for (PluginDependency existing : dependencies) {
            if (refersToSameProduct(existing, dependency)) {
                throw new IllegalArgumentException("Duplicate plugin dependency");
            }
        }
        dependencies.add(dependency);
        return this;
    }

    @Override
    public @NotNull PluginBuilder metadata(@NotNull Consumer<PluginMetadataBuilder> configure) {
        configure.accept(new ModuleMetadataDefinition(this));
        return this;
    }

    @Override
    public @NotNull PluginBuilder options(@NotNull Consumer<PluginOptions> configure) {
        configure.accept(new ModuleOptionsDefinition(this));
        return this;
    }

    @Override
    public @NotNull PluginBuilder metrics(@NotNull Consumer<MetricsBuilder> configure) {
        MetricsBuilder builder = new MetricsBuilder();
        configure.accept(builder);
        List<org.pnfolder.pnlibrary.api.metrics.provider.MetricsProviderConfiguration> providers =
                new ArrayList<>(metrics.getProviders());
        providers.addAll(builder.build());
        List<Consumer<org.pnfolder.pnlibrary.api.metrics.PluginMetrics>> configurers =
                new ArrayList<>(metrics.getConfigurers());
        configurers.add(builder::configure);
        metrics = new ModuleMetricsDefinition(providers, builder.isEnabled(), configurers);
        return this;
    }

    @Override
    public @NotNull PluginBuilder diagnostics(@NotNull Path dataDirectory, @NotNull DiagnosticContainer container) {
        diagnosticsDirectory = dataDirectory;
        diagnosticContainer = container;
        return this;
    }

    @Override
    public @NotNull PluginBuilder updates(@NotNull PluginUpdateRequest request) {
        updateRequest = request;
        return this;
    }

    @Override
    public @NotNull PluginBuilder downloads(@NotNull FileDownloads request) {
        if (downloadsRequest != null) {
            throw new IllegalArgumentException("Downloads are already configured");
        }
        downloadsRequest = request;
        return this;
    }

    @Override
    public @NotNull PluginBuilder remotePolicy(@NotNull RemotePolicySettings policy) {
        remotePolicy = policy;
        return this;
    }

    public void bindUpdatesTo(@Nullable ProductDescriptor descriptor) {
        updateRequest = UpdateRequestBinding.bind(updateRequest, descriptor);
    }

    private static boolean refersToSameProduct(PluginDependency first, PluginDependency other) {
        if (first.getManaged() != null && other.getManaged() != null
                && first.getManaged().getProduct().equals(other.getManaged().getProduct())) {
            return true;
        }
        return first.getExternal() != null && other.getExternal() != null
                && first.getExternal().getPlugin().equalsIgnoreCase(other.getExternal().getPlugin());
    }

    void setPlaceholderApiEnabled(boolean enabled) {
        this.placeholderApiEnabled = enabled;
    }

    public void setMetadataName(String value) {
        this.metadataName = value;
    }

    public void setMetadataVersion(String value) {
        this.metadataVersion = value;
    }

    public void setMetadataAuthors(String value) {
        this.metadataAuthors = value;
    }

    void setMetadataDescription(String value) {
        this.metadataDescription = value;
    }
}
