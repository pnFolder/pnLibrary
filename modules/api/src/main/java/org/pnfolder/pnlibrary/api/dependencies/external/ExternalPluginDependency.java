package org.pnfolder.pnlibrary.api.dependencies.external;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.dependencies.PluginDependency;
import org.pnfolder.pnlibrary.api.dependencies.VersionConstraint;
import org.pnfolder.pnlibrary.api.downloads.DownloadPolicy;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.updates.UpdateValidation;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

/** Required third-party plugin: manual-only, or with an exact verified artifact. */
public final class ExternalPluginDependency implements PluginDependency {
    private final String plugin;
    private final String title;
    private final String description;
    private final VersionConstraint versions;
    private final URI downloadPage;
    private final List<ExternalArtifact> artifacts;
    private final ExternalArtifact artifact;
    private final boolean verifyPluginId;
    private final boolean verifyVersion;
    private final boolean required;
    private final DownloadPolicy downloadPolicy;

    private ExternalPluginDependency(Builder builder) {
        this.plugin = builder.plugin;
        this.title = builder.title != null ? builder.title : builder.plugin;
        this.description = builder.description;
        this.versions = new VersionConstraint(builder.minimumVersion, builder.maximumInclusive, builder.maximumExclusive);
        this.downloadPage = builder.downloadPage;
        this.artifacts = UpdateValidation.frozen(builder.allArtifacts());
        this.artifact = ExternalArtifact.general(artifacts);
        this.verifyPluginId = builder.verifyPluginId;
        this.verifyVersion = builder.verifyVersion;
        this.required = builder.required;
        this.downloadPolicy = builder.downloadPolicy != null ? builder.downloadPolicy
            : artifacts.isEmpty() ? DownloadPolicy.MANUAL : DownloadPolicy.AUTOMATIC;
    }

    /** Starts a dependency for {@code plugin} with the supplied minimum version. */
    @NotNull
    public static Builder builder(@NotNull String plugin, @NotNull String minimumVersion) {
        if (plugin.trim().isEmpty()) {
            throw new IllegalArgumentException("External plugin name must not be blank");
        }
        return new Builder(plugin.trim(), SemanticVersion.parse(minimumVersion));
    }

    /** Returns this dependency as its external representation. */
    @NotNull
    @Override
    public ExternalPluginDependency getExternal() {
        return this;
    }

    /** Third-party plugin name expected from the native platform. */
    @NotNull
    public String getPlugin() {
        return plugin;
    }

    /** Short name for people, such as {@code Vault economy}; the plugin name when not given. */
    @NotNull
    public String getTitle() {
        return title;
    }

    /** One or two sentences on why the plugin is needed, shown to the administrator when it is missing. */
    @Nullable
    public String getDescription() {
        return description;
    }

    /** Oldest accepted plugin version. */
    @NotNull
    public SemanticVersion getMinimumVersion() {
        return versions.getMinimum();
    }

    /** Accepted semantic-version interval. */
    @NotNull
    @Override
    public VersionConstraint getVersions() {
        return versions;
    }

    /** Administrator-facing download page, when declared. */
    @Nullable
    public URI getDownloadPage() {
        return downloadPage;
    }

    /** Every downloadable file, specific variants first and the general one last; empty when only a page is given. */
    @NotNull
    public List<ExternalArtifact> getArtifacts() {
        return artifacts;
    }

    /** The general downloadable file (or the first variant), when automatic installation is possible. */
    @Nullable
    public ExternalArtifact getArtifact() {
        return artifact;
    }

    /** The file meant for this server: the first variant that fits, or null when none does. */
    @Nullable
    public ExternalArtifact artifactFor(@NotNull PlatformType platform, @Nullable String minecraft) {
        for (ExternalArtifact file : artifacts) {
            if (file.fits(platform, minecraft)) {
                return file;
            }
        }
        return null;
    }

    /** Whether staged JAR metadata must match {@link #getPlugin()}. */
    public boolean getVerifyPluginId() {
        return verifyPluginId;
    }

    /** Whether staged JAR metadata must satisfy {@link #getVersions()}. */
    public boolean getVerifyVersion() {
        return verifyVersion;
    }

    /** Whether absence blocks the declaring product. */
    @Override
    public boolean getRequired() {
        return required;
    }

    /** Administrator/automatic download policy. */
    @NotNull
    @Override
    public DownloadPolicy getDownloadPolicy() {
        return downloadPolicy;
    }

    /** Fluent builder for an {@link ExternalPluginDependency}. */
    public static final class Builder {
        private final String plugin;
        private final SemanticVersion minimumVersion;
        private URI downloadPage;
        private ExternalArtifact artifact;
        private boolean required = true;
        private SemanticVersion maximumInclusive;
        private SemanticVersion maximumExclusive;
        private DownloadPolicy downloadPolicy;
        private final List<URI> mirrors = new ArrayList<URI>();
        private boolean verifyPluginId = true;
        private boolean verifyVersion = true;
        private final List<ExternalArtifact> variants = new ArrayList<ExternalArtifact>();
        private String title;
        private Long generalMaxBytes;
        private String description;

        private Builder(String plugin, SemanticVersion minimumVersion) {
            this.plugin = plugin;
            this.minimumVersion = minimumVersion;
        }

        /** Sets the largest accepted size in bytes of the general file; variants have their own. */
        @NotNull
        public Builder maxBytes(long value) {
            this.generalMaxBytes = value;
            return this;
        }

        /** Sets the short name people read in the notice about the missing plugin. */
        @NotNull
        public Builder title(@NotNull String value) {
            this.title = value.trim().isEmpty() ? null : value.trim();
            return this;
        }

        /** Says why the plugin is needed, in a sentence or two. */
        @NotNull
        public Builder description(@NotNull String value) {
            this.description = value.trim().isEmpty() ? null : value.trim();
            return this;
        }

        /** Sets the page where the administrator can download the plugin by hand. */
        @NotNull
        public Builder downloadPage(@NotNull String url) {
            downloadPage = UpdateValidation.requireHttpsUri(url, "Download page must use HTTPS");
            return this;
        }

        /** Sets a direct download with the SHA-256 checksum the file must match. */
        @NotNull
        public Builder artifact(@NotNull String url, @NotNull String sha256) {
            this.artifact = new ExternalArtifact(URI.create(url), sha256);
            return this;
        }

        /** Sets a direct download URL without an integrity check. */
        @NotNull
        public Builder url(@NotNull String value) {
            this.artifact = new ExternalArtifact(URI.create(value), null);
            return this;
        }

        /**
         * Adds a file for some servers only: other {@code platforms} (empty means all) and/or Minecraft versions from
         * {@code minecraftFrom} (inclusive) up to {@code minecraftBefore} (exclusive). The first variant that fits is
         * downloaded and checked against its own {@code sha256}.
         */
        @NotNull
        public Builder variant(@NotNull String url, @Nullable String sha256, @NotNull Set<PlatformType> platforms,
                               @Nullable String minecraftFrom, @Nullable String minecraftBefore,
                               @NotNull List<String> mirrors, @Nullable String label, @Nullable Long maxBytes) {
            variants.add(ExternalArtifact.variant(url, sha256, platforms, minecraftFrom, minecraftBefore, mirrors,
                label, maxBytes));
            return this;
        }

        /** Adds another HTTPS address of the same file; tried when the one before does not answer. */
        @NotNull
        public Builder mirror(@NotNull String value) {
            mirrors.add(UpdateValidation.requireHttpsUri(value, "Mirror must use HTTPS"));
            return this;
        }

        /** Sets whether the plugin refuses to start without this dependency. */
        @NotNull
        public Builder required(boolean value) {
            this.required = value;
            return this;
        }

        /** Sets the highest accepted plugin version, inclusive. */
        @NotNull
        public Builder maximumVersion(@NotNull String value) {
            this.maximumInclusive = SemanticVersion.parse(value);
            return this;
        }

        /** Sets the upper version bound, exclusive. */
        @NotNull
        public Builder maximumVersionExclusive(@NotNull String value) {
            this.maximumExclusive = SemanticVersion.parse(value);
            return this;
        }

        /** Sets how a missing or outdated plugin is downloaded. */
        @NotNull
        public Builder downloadPolicy(@NotNull DownloadPolicy value) {
            this.downloadPolicy = value;
            return this;
        }

        /** Enables or disables staged plugin-ID verification. */
        @NotNull
        public Builder verifyPluginId(boolean value) {
            this.verifyPluginId = value;
            return this;
        }

        /** Enables or disables staged plugin-version verification. */
        @NotNull
        public Builder verifyVersion(boolean value) {
            this.verifyVersion = value;
            return this;
        }

        private List<ExternalArtifact> allArtifacts() {
            List<ExternalArtifact> all = new ArrayList<ExternalArtifact>(variants);
            if (artifact != null) {
                all.add(artifact);
            }
            return all;
        }

        /** Validates and creates the immutable dependency. */
        @NotNull
        public ExternalPluginDependency build() {
            boolean hasFile = !allArtifacts().isEmpty();
            if (downloadPage == null && !hasFile) {
                throw new IllegalArgumentException("External dependency requires a download page or artifact");
            }
            if (!hasFile && downloadPolicy != null && downloadPolicy != DownloadPolicy.MANUAL) {
                throw new IllegalArgumentException("A download page cannot be installed automatically");
            }
            if (artifact != null) {
                artifact = artifact.withMirrorsAndLimit(mirrors, generalMaxBytes);
            }
            if (allArtifacts().size() > 16) {
                throw new IllegalStateException("Too many files for one dependency");
            }
            return new ExternalPluginDependency(this);
        }
    }
}
