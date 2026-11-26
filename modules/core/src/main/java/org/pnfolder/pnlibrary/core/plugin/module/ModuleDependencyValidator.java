package org.pnfolder.pnlibrary.core.plugin.module;

import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.dependencies.PluginDependency;
import org.pnfolder.pnlibrary.api.downloads.DownloadPolicy;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership;

/** Checks that a module's declared plugin dependencies are present and downloadable. */
public final class ModuleDependencyValidator {
    private final PlatformOwnership platform;
    private final BooleanSupplier downloadsAvailable;

    public ModuleDependencyValidator(@NotNull PlatformOwnership platform, @NotNull BooleanSupplier downloadsAvailable) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.downloadsAvailable = Objects.requireNonNull(downloadsAvailable, "downloadsAvailable");
    }

    public void validate(@NotNull List<PluginDependency> dependencies, @NotNull Map<String, ProductDescriptor> registeredProducts) {
        List<String> problems = new ArrayList<>();
        collectManagedProblems(dependencies, registeredProducts, problems);
        collectExternalProblems(dependencies, problems);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Unsatisfied pnLibrary component dependencies: " + String.join("; ", problems));
        }
    }

    private static void collectManagedProblems(List<PluginDependency> dependencies, Map<String, ProductDescriptor> registered,
                                               List<String> problems) {
        for (PluginDependency declared : dependencies) {
            org.pnfolder.pnlibrary.api.dependencies.external.ManagedProductDependency dependency = declared.getManaged();
            if (dependency == null || !dependency.getRequired()) {
                continue;
            }
            ProductDescriptor installed = registered.get(dependency.getProduct().getValue());
            if (installed == null) {
                problems.add(dependency.getProduct() + " >= " + dependency.getMinimumVersion() + " is missing ("
                        + dependency.getRepositoryOwner() + "/" + dependency.getRepositoryName() + ")");
            } else if (installed.getVersion().compareTo(dependency.getMinimumVersion()) < 0) {
                problems.add(dependency.getProduct() + " " + installed.getVersion() + " is installed, "
                        + dependency.getMinimumVersion() + " is required");
            }
        }
    }

    private void collectExternalProblems(List<PluginDependency> dependencies, List<String> problems) {
        Map<String, String> installedPlugins = installedPlugins();
        for (PluginDependency declared : dependencies) {
            org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency dependency = declared.getExternal();
            if (dependency == null || !dependency.getRequired()) {
                continue;
            }
            String installedVersion = installedPlugins.get(dependency.getPlugin().toLowerCase(Locale.ROOT));
            boolean automaticDownloadPossible = downloadsAvailable.getAsBoolean()
                    && dependency.getDownloadPolicy() != DownloadPolicy.MANUAL && dependency.getArtifact() != null;
            if (installedVersion == null) {
                if (!automaticDownloadPossible) {
                    String page = dependency.getDownloadPage() != null ? dependency.getDownloadPage().toString() : "no download page";
                    problems.add(dependency.getPlugin() + " >= " + dependency.getMinimumVersion() + " is missing (" + page + ")");
                }
                continue;
            }
            SemanticVersion parsed = SemanticVersion.tryParse(installedVersion);
            boolean supported = parsed != null && dependency.getVersions().accepts(parsed);
            if (!supported && !automaticDownloadPossible) {
                problems.add(dependency.getPlugin() + " " + installedVersion + " is installed, "
                        + dependency.getMinimumVersion() + " is required");
            }
        }
    }

    private Map<String, String> installedPlugins() {
        Map<String, String> lowered = new HashMap<>();
        Map<String, String> installed = Failures.runCatchingNonFatal(platform::installedPlugins).getOrNull();
        if (installed == null) {
            return Collections.emptyMap();
        }
        for (Map.Entry<String, String> entry : installed.entrySet()) {
            lowered.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        return lowered;
    }
}
