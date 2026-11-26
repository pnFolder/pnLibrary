package org.pnfolder.pnlibrary.api.updates.request;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.updates.backup.BackupExport;
import org.pnfolder.pnlibrary.api.updates.backup.BackupKind;
import org.pnfolder.pnlibrary.api.updates.backup.UpdateConfigurationBackup;
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.PnLibraryApi;

/**
 * Validated GitHub release and artifact-selection policy for one plugin.
 *
 * <p>The release channel and automatic download are not set here: an administrator chooses them in pnLibrary. The
 * release catalogue ({@code releases.json}) names the platform and Java range of every file, so a plugin with one jar
 * needs no artifact rule:
 *
 * <pre>{@code
 * PluginUpdateRequest request = PluginUpdateRequest.builder().repository("example", "ExamplePlugin").build();
 * }</pre>
 *
 * <p>Declare {@link Builder#artifact(String, int)} only when one repository ships builds for different Java versions
 * and the plugin wants to refuse to register on an unsupported one; the compatible artifact with the highest
 * {@link PluginUpdateArtifact#getMinimumJava()} wins.
 */
public final class PluginUpdateRequest {
    private static final Pattern REPOSITORY_PART = Pattern.compile("[A-Za-z0-9_.-]+");

    private final String repositoryOwner;
    private final String repositoryName;
    private final CatalogSource catalog;
    private final List<PluginUpdateArtifact> artifacts;
    private final ApiVersionRange supportedApi;
    private final UpdateConfigurationBackup configurationBackup;
    private final List<BackupExport> exports;

    private PluginUpdateRequest(Builder builder) {
        this.repositoryOwner = builder.repositoryOwner;
        this.repositoryName = builder.repositoryName;
        this.catalog = new CatalogSource(repositoryOwner, repositoryName, builder.branch, builder.catalogPath,
            builder.refType);
        this.artifacts = Collections.unmodifiableList(new ArrayList<PluginUpdateArtifact>(builder.artifacts));
        this.supportedApi = builder.supportedApi;
        this.configurationBackup = builder.backups.files();
        this.exports = builder.backups.exports();
    }

    /** Creates a new {@link Builder}. */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /** GitHub repository owner. */
    @NotNull
    public String getRepositoryOwner() {
        return repositoryOwner;
    }

    /** GitHub repository name. */
    @NotNull
    public String getRepositoryName() {
        return repositoryName;
    }

    /** Repository, branch and folder of the release catalog; {@code main} and {@code .pnlibrary} by default. */
    @NotNull
    public CatalogSource getCatalog() {
        return catalog;
    }

    /** Release channel until an administrator picks another one in pnLibrary; {@link UpdateChannel#STABLE}. */
    @NotNull
    public UpdateChannel getChannel() {
        return UpdateChannel.STABLE;
    }

    /** Whether a newly found compatible release is downloaded until an administrator changes it; {@code true}. */
    public boolean getAutomaticDownload() {
        return true;
    }

    /** Immutable artifact-selection rules in declaration order. */
    @NotNull
    public List<PluginUpdateArtifact> getArtifacts() {
        return artifacts;
    }

    /** Inclusive pnLibrary API generations supported by this product. */
    @NotNull
    public ApiVersionRange getSupportedApi() {
        return supportedApi;
    }

    /** Explicit opt-in configuration backup; absent by default. */
    @Nullable
    public UpdateConfigurationBackup getConfigurationBackup() {
        return configurationBackup;
    }

    /** Copies the plugin makes itself before an update; kept with the backup set and restored by hand. */
    @NotNull
    public List<BackupExport> getExports() {
        return exports;
    }

    /** Returns the most specific artifact compatible with {@code javaFeature}, or {@code null}. */
    @Nullable
    public PluginUpdateArtifact artifactFor(int javaFeature) {
        return best(javaFeature, null);
    }

    /** Returns the most specific artifact compatible with both runtime and platform. */
    @Nullable
    public PluginUpdateArtifact artifactFor(int javaFeature, @NotNull PlatformType platform) {
        return best(javaFeature, platform);
    }

    private PluginUpdateArtifact best(int javaFeature, PlatformType platform) {
        PluginUpdateArtifact best = null;
        for (PluginUpdateArtifact artifact : artifacts) {
            PlatformType required = artifact.getPlatform();
            boolean fits = artifact.supports(javaFeature) && (platform == null || required == null || required == platform);
            if (fits && (best == null || best.getMinimumJava() < artifact.getMinimumJava())) {
                best = artifact;
            }
        }
        return best;
    }

    /** Mutable Java-friendly builder for {@link PluginUpdateRequest}. */
    public static final class Builder {
        private String repositoryOwner = "";
        private String repositoryName = "";
        private String branch = CatalogSource.DEFAULT_BRANCH;
        private String catalogPath = CatalogSource.DEFAULT_PATH;
        private CatalogSource.RefType refType = CatalogSource.RefType.BRANCH;
        private final List<PluginUpdateArtifact> artifacts = new ArrayList<PluginUpdateArtifact>();
        private ApiVersionRange supportedApi = new ApiVersionRange(PnLibraryApi.VERSION, PnLibraryApi.VERSION);
        private final BackupDeclarations backups = new BackupDeclarations();

        private Builder() {
        }

        /** Adds a copy {@code export} writes before updating, such as a MySQL dump; a rollback does not restore it. */
        @NotNull
        public Builder backupExport(@NotNull BackupKind kind, @NotNull String name, @NotNull String fileName,
                                    @NotNull BackupExport.Writer export) {
            backups.addExport(kind, name, fileName, export);
            return this;
        }

        /** Preserves only {@code files} relative to the component's own {@code dataDirectory} before updating. */
        @NotNull
        public Builder configurationBackup(@NotNull Path dataDirectory, @NotNull String... files) {
            return backup(BackupKind.CONFIGURATION, "", dataDirectory, files);
        }

        /**
         * Preserves {@code files} of the component's own {@code dataDirectory} and labels them with a {@code kind}
         * and a short {@code name}. Every call must name the same {@code dataDirectory}; a file may be declared once.
         */
        @NotNull
        public Builder backup(@NotNull BackupKind kind, @NotNull String name, @NotNull Path dataDirectory,
                              @NotNull String... files) {
            backups.addFiles(kind, name, dataDirectory, files);
            return this;
        }

        /** Sets the GitHub repository (owner and name) releases are read from. */
        @NotNull
        public Builder repository(@NotNull String owner, @NotNull String name) {
            if (!REPOSITORY_PART.matcher(owner).matches() || !REPOSITORY_PART.matcher(name).matches()) {
                throw new IllegalArgumentException("Invalid GitHub repository: " + owner + "/" + name);
            }
            this.repositoryOwner = owner;
            this.repositoryName = name;
            return this;
        }

        /** Sets the branch that holds {@code releases.json}; {@code main} by default. */
        @NotNull
        public Builder branch(@NotNull String name) {
            return reference(name, CatalogSource.RefType.BRANCH);
        }

        /** Reads the catalog as it was at tag {@code name}, for plugins that publish releases from tags. */
        @NotNull
        public Builder tag(@NotNull String name) {
            return reference(name, CatalogSource.RefType.TAG);
        }

        /** Reads the catalog as it was at commit {@code hash} (7 to 40 hexadecimal characters). */
        @NotNull
        public Builder commit(@NotNull String hash) {
            return reference(hash, CatalogSource.RefType.COMMIT);
        }

        private Builder reference(String name, CatalogSource.RefType type) {
            new CatalogSource("o", "r", name, CatalogSource.DEFAULT_PATH, type);
            this.branch = name;
            this.refType = type;
            return this;
        }

        /**
         * Sets the folder of the catalog inside the repository, for example {@code updates} to read
         * {@code updates/releases.json}. The default is {@code .pnlibrary}.
         */
        @NotNull
        public Builder catalogPath(@NotNull String path) {
            String clean = path.replaceAll("^/+|/+$", "");
            new CatalogSource("o", "r", CatalogSource.DEFAULT_BRANCH, clean);
            this.catalogPath = clean;
            return this;
        }

        /** Sets the range of pnLibrary API versions the plugin build supports. */
        @NotNull
        public Builder supportedApi(int minimum, int maximum) {
            this.supportedApi = new ApiVersionRange(minimum, maximum);
            return this;
        }

        /** Selects the release file with exactly this name, for any Java version from 8. */
        @NotNull
        public Builder exactArtifact(@NotNull String name) {
            return exactArtifact(name, 8, null);
        }

        /** Selects the release file with exactly this name, from the given Java version. */
        @NotNull
        public Builder exactArtifact(@NotNull String name, int minimumJava) {
            return exactArtifact(name, minimumJava, null);
        }

        /** Selects the release file with exactly this name, for the given Java range. */
        @NotNull
        public Builder exactArtifact(@NotNull String name, int minimumJava, @Nullable Integer maximumJava) {
            if (name.trim().isEmpty()) {
                throw new IllegalArgumentException("Artifact name must not be blank");
            }
            return artifact("^" + Pattern.quote(name) + "$", minimumJava, maximumJava);
        }

        /** Selects release files whose name matches {@code regex}, from the given Java version. */
        @NotNull
        public Builder artifact(@NotNull String regex, int minimumJava) {
            return artifact(regex, minimumJava, null);
        }

        /** Selects release files whose name matches {@code regex}, for the given Java range. */
        @NotNull
        public Builder artifact(@NotNull String regex, int minimumJava, @Nullable Integer maximumJava) {
            return addArtifact(regex, null, minimumJava, maximumJava);
        }

        /** Selects release files matching {@code regex} on one {@code platform}, from the given Java version. */
        @NotNull
        public Builder artifact(@NotNull String regex, @NotNull PlatformType platform, int minimumJava) {
            return addArtifact(regex, platform, minimumJava, null);
        }

        /** Selects release files matching {@code regex} on one {@code platform}, for the given Java range. */
        @NotNull
        public Builder artifact(@NotNull String regex, @NotNull PlatformType platform, int minimumJava,
                                @Nullable Integer maximumJava) {
            return addArtifact(regex, platform, minimumJava, maximumJava);
        }

        private Builder addArtifact(String regex, PlatformType platform, int minimumJava, Integer maximumJava) {
            Pattern.compile(regex);
            if (minimumJava < 8) {
                throw new IllegalArgumentException("Minimum Java must be at least 8");
            }
            if (maximumJava != null && maximumJava < minimumJava) {
                throw new IllegalArgumentException("Maximum Java must be >= minimum Java");
            }
            artifacts.add(new PluginUpdateArtifact(regex, minimumJava, maximumJava, platform));
            return this;
        }

        /** Validates the complete policy and creates its immutable request. */
        @NotNull
        public PluginUpdateRequest build() {
            if (repositoryOwner.trim().isEmpty() || repositoryName.trim().isEmpty()) {
                throw new IllegalArgumentException("Repository is required");
            }
            return new PluginUpdateRequest(this);
        }
    }
}
