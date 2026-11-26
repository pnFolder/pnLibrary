package org.pnfolder.pnlibrary.api.plugin.metadata;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;

/** Immutable runtime metadata resolved when a plugin context is registered. */
public final class PluginMetadata {
    private final ModuleId id;
    private final String name;
    private final String version;
    private final String authors;
    private final PlatformType platform;
    private final String platformImplementation;
    private final String javaVersion;
    private final int javaFeature;
    private final String description;

    /**
     * Creates the metadata.
     *
     * @param id normalized pnLibrary plugin identity
     * @param name display name reported by the platform or overridden by {@link PluginMetadataBuilder}
     * @param version plugin version string, or {@code unknown} when unavailable
     * @param authors display-ready author list, or {@code unknown} when unavailable
     * @param platform normalized server platform family
     * @param platformImplementation concrete platform implementation and version description
     * @param javaVersion complete JVM version string from the running process
     * @param javaFeature JVM feature release, for example {@code 21}
     * @param description one or two sentences on what the plugin or module is for; {@code null} when not given
     */
    public PluginMetadata(@NotNull ModuleId id, @NotNull String name, @NotNull String version,
                          @NotNull String authors, @NotNull PlatformType platform,
                          @NotNull String platformImplementation, @NotNull String javaVersion, int javaFeature,
                          @Nullable String description) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.version = Objects.requireNonNull(version, "version");
        this.authors = Objects.requireNonNull(authors, "authors");
        this.platform = Objects.requireNonNull(platform, "platform");
        this.platformImplementation = Objects.requireNonNull(platformImplementation, "platformImplementation");
        this.javaVersion = Objects.requireNonNull(javaVersion, "javaVersion");
        this.javaFeature = javaFeature;
        this.description = description;
    }

    /** Creates metadata with no description. */
    public PluginMetadata(@NotNull ModuleId id, @NotNull String name, @NotNull String version,
                          @NotNull String authors, @NotNull PlatformType platform,
                          @NotNull String platformImplementation, @NotNull String javaVersion, int javaFeature) {
        this(id, name, version, authors, platform, platformImplementation, javaVersion, javaFeature, null);
    }

    /** Normalized pnLibrary plugin identity. */
    @NotNull
    public ModuleId getId() {
        return id;
    }

    /** Display name reported by the platform or overridden by {@link PluginMetadataBuilder}. */
    @NotNull
    public String getName() {
        return name;
    }

    /** Plugin version string, or {@code unknown} when unavailable. */
    @NotNull
    public String getVersion() {
        return version;
    }

    /** Display-ready author list, or {@code unknown} when unavailable. */
    @NotNull
    public String getAuthors() {
        return authors;
    }

    /** Normalized server platform family. */
    @NotNull
    public PlatformType getPlatform() {
        return platform;
    }

    /** Concrete platform implementation and version description. */
    @NotNull
    public String getPlatformImplementation() {
        return platformImplementation;
    }

    /** Complete JVM version string from the running process. */
    @NotNull
    public String getJavaVersion() {
        return javaVersion;
    }

    /** JVM feature release, for example {@code 21}. */
    public int getJavaFeature() {
        return javaFeature;
    }

    /** One or two sentences on what the plugin or module is for, read by administrators in the menu. */
    @Nullable
    public String getDescription() {
        return description;
    }
}
