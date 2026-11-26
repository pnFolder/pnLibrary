package org.pnfolder.pnlibrary.api.plugin;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.compat.declaration.MinecraftSupportDeclaration;
import org.pnfolder.pnlibrary.api.dependencies.DependencyBuilder;
import org.pnfolder.pnlibrary.api.dependencies.PluginDependency;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticContainer;
import org.pnfolder.pnlibrary.api.downloads.FileDownloads;
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsBuilder;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMetadataBuilder;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginOptions;
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest;

/**
 * Declarative setup evaluated by {@code PluginRegistry.register} before capabilities become visible.
 *
 * <p>Builder calls only describe the context. pnLibrary creates integrations after the registration callback returns
 * and rolls back partial resources if any integration fails to initialize.
 */
public interface PluginBuilder extends MinecraftSupportDeclaration {
    /** Declares one dependency of the plugin. */
    @NotNull
    PluginBuilder depends(@NotNull PluginDependency dependency);

    /** Declares several dependencies of the plugin. */
    @NotNull
    default PluginBuilder depends(@NotNull PluginDependency... dependencies) {
        for (PluginDependency dependency : dependencies) {
            depends(dependency);
        }
        return this;
    }

    /**
     * Asks pnLibrary to download, verify and load a Maven library for this module before the module starts, so the
     * plugin needs no {@code libraries:} block in {@code plugin.yml}. A required library that cannot be loaded makes
     * {@code registerModule} throw; an optional one only logs a warning. The same library declared twice is loaded
     * once.
     *
     * <pre>{@code
     * registration.registerModule(MODULE_ID, setup -> setup.library("org.xerial:sqlite-jdbc:3.46.0.0"));
     * }</pre>
     */
    @NotNull
    PluginBuilder library(@NotNull ModuleLibrary library);

    /** Declares several libraries; see {@link #library(ModuleLibrary)}. */
    @NotNull
    default PluginBuilder libraries(@NotNull ModuleLibrary... libraries) {
        for (ModuleLibrary library : libraries) {
            library(library);
        }
        return this;
    }

    /**
     * Declares a library that is loaded only when {@code enabled} answers {@code true} at the moment the module
     * starts, for example when the settings choose SQLite. A library that is not needed is not downloaded and not
     * listed.
     */
    @NotNull
    PluginBuilder library(@NotNull ModuleLibrary library, @NotNull BooleanSupplier enabled);

    /**
     * Settings every library declared afterwards by coordinates or inline starts from, such as your own mirror, Maven
     * Central off or an isolated class loader. The library's own settings come after and win. A ready
     * {@link ModuleLibrary} is not changed.
     */
    @NotNull
    PluginBuilder libraryDefaults(@NotNull Consumer<ModuleLibrary.Builder> configure);

    /** Declares a library by {@code group:artifact:version} (and an optional classifier) with the defaults. */
    @NotNull
    default PluginBuilder library(@NotNull String coordinates) {
        final String[] parts = coordinates.trim().split(":", -1);
        if (parts.length != 3 && parts.length != 4) {
            throw new IllegalArgumentException("Expected group:artifact:version, got: " + coordinates);
        }
        return library(parts[0], parts[1], parts[2], new Consumer<ModuleLibrary.Builder>() {
            @Override
            public void accept(ModuleLibrary.Builder builder) {
                if (parts.length == 4) {
                    builder.classifier(parts[3]);
                }
            }
        });
    }

    /** Builds a library inline: {@code setup.library("org.xerial", "sqlite-jdbc", "3.46.0.0", it -> it.title("SQLite"))}. */
    @NotNull
    PluginBuilder library(@NotNull String group, @NotNull String artifact, @NotNull String version,
                          @NotNull Consumer<ModuleLibrary.Builder> configure);

    /** Declares the component identity, API range, and dependencies used by the graph updater. */
    @NotNull
    PluginBuilder product(@NotNull ProductDescriptor descriptor);

    /** Overrides native metadata only when a plugin needs custom display values. */
    @NotNull
    PluginBuilder metadata(@NotNull Consumer<PluginMetadataBuilder> configure);

    /** Changes optional integration switches. Unspecified options keep safe defaults. */
    @NotNull
    PluginBuilder options(@NotNull Consumer<PluginOptions> configure);

    /**
     * Adds provider-neutral metrics backends without replacing earlier declarations.
     *
     * <p>Each provider may be declared only once. Chart callbacks accumulate and run for all enabled backends. The
     * latest call selects the initial session enabled state.
     */
    @NotNull
    PluginBuilder metrics(@NotNull Consumer<MetricsBuilder> configure);

    /**
     * Registers a diagnostics contributor rooted at {@code dataDirectory}.
     *
     * <p>The resulting handle is exposed as {@link ModuleContext#getDiagnostics()} and closed with the context.
     */
    @NotNull
    PluginBuilder diagnostics(@NotNull Path dataDirectory, @NotNull DiagnosticContainer container);

    /** Registers one update-check definition for this plugin. */
    @NotNull
    PluginBuilder updates(@NotNull PluginUpdateRequest request);

    /** Builds the update request inline instead of requiring a temporary variable. */
    @NotNull
    default PluginBuilder updates(@NotNull Consumer<PluginUpdateRequest.Builder> configure) {
        PluginUpdateRequest.Builder builder = PluginUpdateRequest.builder();
        configure.accept(builder);
        return updates(builder.build());
    }

    /** Declares dependencies with the dependency DSL. */
    @NotNull
    default PluginBuilder dependencies(@NotNull Consumer<DependencyBuilder> configure) {
        DependencyBuilder builder = new DependencyBuilder();
        configure.accept(builder);
        for (PluginDependency dependency : builder.build()) {
            depends(dependency);
        }
        return this;
    }

    /** Inline GitHub updater configuration with the repository already filled in. */
    @NotNull
    default PluginBuilder updates(@NotNull String repositoryOwner, @NotNull String repositoryName,
                                  @NotNull Consumer<PluginUpdateRequest.Builder> configure) {
        PluginUpdateRequest.Builder builder = PluginUpdateRequest.builder().repository(repositoryOwner, repositoryName);
        configure.accept(builder);
        return updates(builder.build());
    }

    /** Registers auxiliary file deliveries. Plugins belong in {@link #depends}, this product's release in {@link #updates}. */
    @NotNull
    PluginBuilder downloads(@NotNull FileDownloads request);

    /** Attaches one validated reusable remote policy declaration. */
    @NotNull
    PluginBuilder remotePolicy(@NotNull RemotePolicySettings policy);

    /** Builds direct-download declarations inline. */
    @NotNull
    default PluginBuilder downloads(@NotNull Consumer<FileDownloads.Builder> configure) {
        FileDownloads.Builder builder = FileDownloads.builder();
        configure.accept(builder);
        return downloads(builder.build());
    }

    /** Builds direct-download declarations rooted at this plugin's data directory. */
    @NotNull
    default PluginBuilder downloads(@NotNull Path dataDirectory, @NotNull Consumer<FileDownloads.Builder> configure) {
        FileDownloads.Builder builder = FileDownloads.builder().dataDirectory(dataDirectory);
        configure.accept(builder);
        return downloads(builder.build());
    }
}
