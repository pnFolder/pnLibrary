package org.pnfolder.pnlibrary.api.dependencies;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency;
import org.pnfolder.pnlibrary.api.dependencies.external.ManagedProductDependency;
import org.pnfolder.pnlibrary.api.downloads.DownloadPolicy;

/** Short factories for the unified dependency DSL. */
public final class Dependencies {
    private Dependencies() {
    }

    /** Declares a required pnLibrary-managed product dependency downloaded manually from a GitHub repository. */
    @NotNull
    public static PluginDependency managed(@NotNull String component, @NotNull String minimumVersion,
                                           @NotNull String repositoryOwner, @NotNull String repositoryName) {
        return managed(component, minimumVersion, repositoryOwner, repositoryName, true);
    }

    /** Declares a pnLibrary-managed product dependency downloaded manually from a GitHub repository. */
    @NotNull
    public static PluginDependency managed(@NotNull String component, @NotNull String minimumVersion,
                                           @NotNull String repositoryOwner, @NotNull String repositoryName,
                                           boolean required) {
        return managed(component, minimumVersion, repositoryOwner, repositoryName, required, DownloadPolicy.MANUAL);
    }

    /** Declares a pnLibrary-managed product dependency downloaded from a GitHub repository. */
    @NotNull
    public static PluginDependency managed(@NotNull String component, @NotNull String minimumVersion,
                                           @NotNull String repositoryOwner, @NotNull String repositoryName,
                                           boolean required, @NotNull DownloadPolicy downloadPolicy) {
        return new ManagedProductDependency(component, minimumVersion, repositoryOwner, repositoryName, required,
            downloadPolicy);
    }

    /** Declares a required external plugin dependency with a manual download page. */
    @NotNull
    public static PluginDependency plugin(@NotNull String plugin, @NotNull String minimumVersion,
                                          @NotNull String downloadPage) {
        return plugin(plugin, minimumVersion, downloadPage, true);
    }

    /** Declares an external plugin dependency with a manual download page. */
    @NotNull
    public static PluginDependency plugin(@NotNull String plugin, @NotNull String minimumVersion,
                                          @NotNull String downloadPage, boolean required) {
        return plugin(plugin, minimumVersion, downloadPage, required, DownloadPolicy.MANUAL);
    }

    /** Declares an external plugin dependency with a manual download page. */
    @NotNull
    public static PluginDependency plugin(@NotNull String plugin, @NotNull String minimumVersion,
                                          @NotNull String downloadPage, boolean required,
                                          @NotNull DownloadPolicy downloadPolicy) {
        return ExternalPluginDependency.builder(plugin, minimumVersion)
            .downloadPage(downloadPage)
            .required(required)
            .downloadPolicy(downloadPolicy)
            .build();
    }

    /** Declares a required external plugin dependency with a direct download checked by its SHA-256. */
    @NotNull
    public static PluginDependency plugin(@NotNull String plugin, @NotNull String minimumVersion,
                                          @NotNull String downloadUrl, @NotNull String sha256) {
        return plugin(plugin, minimumVersion, downloadUrl, sha256, true);
    }

    /** Declares an external plugin dependency with a direct download checked by its SHA-256. */
    @NotNull
    public static PluginDependency plugin(@NotNull String plugin, @NotNull String minimumVersion,
                                          @NotNull String downloadUrl, @NotNull String sha256, boolean required) {
        return plugin(plugin, minimumVersion, downloadUrl, sha256, required, DownloadPolicy.AUTOMATIC);
    }

    /** Declares an external plugin dependency with a direct download checked by its SHA-256. */
    @NotNull
    public static PluginDependency plugin(@NotNull String plugin, @NotNull String minimumVersion,
                                          @NotNull String downloadUrl, @NotNull String sha256, boolean required,
                                          @NotNull DownloadPolicy downloadPolicy) {
        return ExternalPluginDependency.builder(plugin, minimumVersion)
            .artifact(downloadUrl, sha256)
            .required(required)
            .downloadPolicy(downloadPolicy)
            .build();
    }

    /** Declares a required external plugin with several download addresses and no checksum; see the longer form. */
    @NotNull
    public static PluginDependency plugin(@NotNull String plugin, @NotNull String minimumVersion,
                                          @NotNull List<String> sources) {
        return plugin(plugin, minimumVersion, sources, true);
    }

    /** Declares an external plugin with several download addresses and no checksum; see the longer form. */
    @NotNull
    public static PluginDependency plugin(@NotNull String plugin, @NotNull String minimumVersion,
                                          @NotNull List<String> sources, boolean required) {
        return plugin(plugin, minimumVersion, sources, required, DownloadPolicy.AUTOMATIC);
    }

    /**
     * Declares an external plugin with several download addresses and no checksum. The first address is tried first,
     * the others are mirrors tried in order when the one before does not answer; the file is taken as it came.
     */
    @NotNull
    public static PluginDependency plugin(@NotNull String plugin, @NotNull String minimumVersion,
                                          @NotNull List<String> sources, boolean required,
                                          @NotNull DownloadPolicy downloadPolicy) {
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("At least one download address is required");
        }
        ExternalPluginDependency.Builder builder = ExternalPluginDependency.builder(plugin, minimumVersion)
            .url(sources.get(0));
        for (String mirror : sources.subList(1, sources.size())) {
            builder.mirror(mirror);
        }
        return builder.required(required).downloadPolicy(downloadPolicy).build();
    }
}
