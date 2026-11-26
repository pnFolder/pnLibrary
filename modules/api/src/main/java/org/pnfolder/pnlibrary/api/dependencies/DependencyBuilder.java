package org.pnfolder.pnlibrary.api.dependencies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency;
import org.pnfolder.pnlibrary.api.dependencies.external.ManagedProductDependency;
import org.pnfolder.pnlibrary.api.downloads.DownloadPolicy;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

/** Mutable Java-friendly collector for all dependencies of one module. */
public final class DependencyBuilder {
    private final List<PluginDependency> dependencies = new ArrayList<>();

    /** Declares a dependency on another pnLibrary product. */
    @NotNull
    public DependencyBuilder product(@NotNull String id, @NotNull Consumer<ProductBuilder> configure) {
        ProductBuilder builder = new ProductBuilder(ProductId.of(id));
        configure.accept(builder);
        ManagedProductDependency dependency = builder.build();
        for (PluginDependency existing : dependencies) {
            ManagedProductDependency managed = existing.getManaged();
            if (managed != null && managed.getProduct().equals(dependency.getProduct())) {
                throw new IllegalArgumentException("Duplicate product dependency: " + dependency.getProduct());
            }
        }
        dependencies.add(dependency);
        return this;
    }

    /** Declares a dependency on an external plugin without a version bound. */
    @NotNull
    public DependencyBuilder plugin(@NotNull String name, @NotNull Consumer<ExternalBuilder> configure) {
        ExternalBuilder builder = new ExternalBuilder(name);
        configure.accept(builder);
        ExternalPluginDependency dependency = builder.build();
        for (PluginDependency existing : dependencies) {
            ExternalPluginDependency external = existing.getExternal();
            if (external != null && external.getPlugin().equalsIgnoreCase(dependency.getPlugin())) {
                throw new IllegalArgumentException("Duplicate plugin dependency: " + dependency.getPlugin());
            }
        }
        dependencies.add(dependency);
        return this;
    }

    /** Declares a dependency on an external plugin with a minimum version. */
    @NotNull
    public DependencyBuilder plugin(@NotNull String name, @NotNull String minimumVersion,
                                    @NotNull Consumer<ExternalBuilder> configure) {
        return plugin(name, builder -> {
            builder.minimumVersion(minimumVersion);
            configure.accept(builder);
        });
    }

    /** Returns an immutable snapshot of all validated dependencies. */
    @NotNull
    public List<PluginDependency> build() {
        return Collections.unmodifiableList(new ArrayList<>(dependencies));
    }

    /** Fluent builder for one managed product dependency. */
    public static final class ProductBuilder {
        private final ProductId id;
        private SemanticVersion minimum;
        private SemanticVersion maximumInclusive;
        private SemanticVersion maximumExclusive;
        private String repositoryOwner;
        private String repositoryName;
        private boolean required = true;
        private DownloadPolicy policy = DownloadPolicy.MANUAL;

        private ProductBuilder(ProductId id) {
            this.id = id;
        }

        /** Sets the lowest accepted product version, inclusive. */
        @NotNull
        public ProductBuilder minimumVersion(@NotNull String value) {
            this.minimum = SemanticVersion.parse(value);
            return this;
        }

        /** Sets the highest accepted product version, inclusive. */
        @NotNull
        public ProductBuilder maximumVersion(@NotNull String value) {
            this.maximumInclusive = SemanticVersion.parse(value);
            return this;
        }

        /** Sets the upper version bound, exclusive. */
        @NotNull
        public ProductBuilder maximumVersionExclusive(@NotNull String value) {
            this.maximumExclusive = SemanticVersion.parse(value);
            return this;
        }

        /** Sets the GitHub repository the product is downloaded from. */
        @NotNull
        public ProductBuilder github(@NotNull String owner, @NotNull String repository) {
            this.repositoryOwner = owner;
            this.repositoryName = repository;
            return this;
        }

        /** Sets whether the plugin refuses to start without this product; {@code true} by default. */
        @NotNull
        public ProductBuilder required(boolean value) {
            this.required = value;
            return this;
        }

        /** Sets how a missing or outdated product is downloaded. */
        @NotNull
        public ProductBuilder downloadPolicy(@NotNull DownloadPolicy value) {
            this.policy = value;
            return this;
        }

        private ManagedProductDependency build() {
            if (minimum == null) {
                throw new IllegalArgumentException("Minimum product version is required");
            }
            if (repositoryOwner == null) {
                throw new IllegalArgumentException("GitHub repository owner is required");
            }
            if (repositoryName == null) {
                throw new IllegalArgumentException("GitHub repository name is required");
            }
            VersionConstraint versions = new VersionConstraint(minimum, maximumInclusive, maximumExclusive);
            return new ManagedProductDependency(id, versions, repositoryOwner, repositoryName, required, policy);
        }
    }

    /** Fluent builder for one third-party native-plugin dependency. */
    public static final class ExternalBuilder {
        private final String name;
        private String minimum;
        private final List<Consumer<ExternalPluginDependency.Builder>> settings =
            new ArrayList<>();

        private ExternalBuilder(String name) {
            if (name.trim().isEmpty()) {
                throw new IllegalArgumentException("External plugin name must not be blank");
            }
            this.name = name;
        }

        /** Sets the lowest accepted plugin version, inclusive. */
        @NotNull
        public ExternalBuilder minimumVersion(@NotNull String value) {
            SemanticVersion.parse(value);
            this.minimum = value;
            return this;
        }

        /** Sets the highest accepted plugin version, inclusive. */
        @NotNull
        public ExternalBuilder maximumVersion(@NotNull String value) {
            SemanticVersion.parse(value);
            return add(builder -> builder.maximumVersion(value));
        }

        /** Sets the upper version bound, exclusive. */
        @NotNull
        public ExternalBuilder maximumVersionExclusive(@NotNull String value) {
            SemanticVersion.parse(value);
            return add(builder -> builder.maximumVersionExclusive(value));
        }

        /** Sets the page where the administrator can download the plugin by hand. */
        @NotNull
        public ExternalBuilder downloadPage(@NotNull String url) {
            return add(builder -> builder.downloadPage(url));
        }

        /** Sets a direct download with the SHA-256 checksum the file must match. */
        @NotNull
        public ExternalBuilder artifact(@NotNull String url, @NotNull String sha256) {
            return add(builder -> builder.artifact(url, sha256));
        }

        /** Sets a direct download URL without an integrity check. */
        @NotNull
        public ExternalBuilder url(@NotNull String value) {
            return add(builder -> builder.url(value));
        }

        /** Sets the short name people read in the notice about the missing plugin. */
        @NotNull
        public ExternalBuilder title(@NotNull String value) {
            return add(builder -> builder.title(value));
        }

        /** Says why the plugin is needed, in a sentence or two. */
        @NotNull
        public ExternalBuilder description(@NotNull String value) {
            return add(builder -> builder.description(value));
        }

        /** Adds another HTTPS address of the general file. */
        @NotNull
        public ExternalBuilder mirror(@NotNull String value) {
            return add(builder -> builder.mirror(value));
        }

        /** Adds a file for some servers only; see {@link ExternalPluginDependency.Builder#variant}. */
        @NotNull
        public ExternalBuilder variant(@NotNull String url, @Nullable String sha256,
                                       @NotNull Set<PlatformType> platforms, @Nullable String minecraftFrom,
                                       @Nullable String minecraftBefore, @NotNull List<String> mirrors,
                                       @Nullable String label, @Nullable Long maxBytes) {
            return add(builder -> builder.variant(url, sha256, platforms, minecraftFrom, minecraftBefore, mirrors,
                label, maxBytes));
        }

        /** Sets the largest accepted size in bytes of the general file. */
        @NotNull
        public ExternalBuilder maxBytes(long value) {
            return add(builder -> builder.maxBytes(value));
        }

        /** Sets whether the plugin refuses to start without this dependency; {@code true} by default. */
        @NotNull
        public ExternalBuilder required(boolean value) {
            return add(builder -> builder.required(value));
        }

        /** Enables or disables staged plugin-ID verification. */
        @NotNull
        public ExternalBuilder verifyPluginId(boolean value) {
            return add(builder -> builder.verifyPluginId(value));
        }

        /** Enables or disables staged plugin-version verification. */
        @NotNull
        public ExternalBuilder verifyVersion(boolean value) {
            return add(builder -> builder.verifyVersion(value));
        }

        /** Sets how a missing or outdated plugin is downloaded. */
        @NotNull
        public ExternalBuilder downloadPolicy(@NotNull DownloadPolicy value) {
            return add(builder -> builder.downloadPolicy(value));
        }

        private ExternalBuilder add(Consumer<ExternalPluginDependency.Builder> step) {
            settings.add(step);
            return this;
        }

        private ExternalPluginDependency build() {
            if (minimum == null) {
                throw new IllegalArgumentException("Minimum plugin version is required");
            }
            ExternalPluginDependency.Builder builder = ExternalPluginDependency.builder(name, minimum);
            for (Consumer<ExternalPluginDependency.Builder> step : settings) {
                step.accept(builder);
            }
            return builder.build();
        }
    }
}
