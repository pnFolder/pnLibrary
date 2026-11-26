package org.pnfolder.pnlibrary.api.libraries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.platform.PlatformType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * A Maven library a module asks pnLibrary to download, verify and load, with every setting of that download.
 *
 * <pre>{@code
 * ModuleLibrary.builder("com.zaxxer", "HikariCP", "5.1.0")
 *     .source(s -> s.link("https://cdn.example.org/HikariCP-5.1.0.jar").sha256("3c0e...").name("CDN")) // 1st place to try
 *     .source(s -> s.repository("https://mirror.example.org/maven/").sha256("9f2a..."))                // 2nd place
 *     .scope(LibraryScope.ISOLATED)                                                                    // own class loader
 *     .build();
 * }</pre>
 *
 * <p>The one list of {@link #getSources() sources} says where the jar comes from; each place has its own address, sum
 * and name. They are tried in order, then Maven Central unless {@link Builder#mavenCentral} turns it off; the first
 * that gives a jar passing its check wins. Transitive dependencies are not resolved: list every jar the library needs.
 */
public final class ModuleLibrary {
    /** Maven Central, the repository used last by default. */
    public static final String CENTRAL = "https://repo1.maven.org/maven2/";

    private static final LibrarySource CENTRAL_SOURCE = LibrarySource.builder().repository(CENTRAL).name("Maven Central").build();
    private static final Pattern PART = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9_.+-]+");

    private final String group;
    private final String artifact;
    private final String version;
    private final String classifier;
    private final List<LibrarySource> sources;
    private final boolean mavenCentral;
    private final LibraryScope scope;
    private final Set<PlatformType> platforms;
    private final boolean required;
    private final String title;
    private final String description;

    private ModuleLibrary(Builder builder) {
        this.group = builder.group;
        this.artifact = builder.artifact;
        this.version = builder.version;
        this.classifier = builder.classifier;
        this.sources = Collections.unmodifiableList(new ArrayList<>(builder.sources));
        this.mavenCentral = builder.mavenCentral;
        this.scope = builder.scope;
        this.platforms = Collections.unmodifiableSet(new LinkedHashSet<>(builder.platforms));
        this.required = builder.required;
        this.title = builder.title != null ? builder.title : builder.artifact;
        this.description = builder.description;
    }

    /** Starts a library {@code group:artifact:version}. */
    @NotNull
    public static Builder builder(@NotNull String group, @NotNull String artifact, @NotNull String version) {
        return new Builder(group, artifact, version);
    }

    /** A library from its {@code group:artifact:version} text with default settings. */
    @NotNull
    public static ModuleLibrary of(@NotNull String coordinates) {
        String[] parts = coordinates.trim().split(":", -1);
        if (parts.length != 3 && parts.length != 4) {
            throw new IllegalArgumentException("Expected group:artifact:version, got: " + coordinates);
        }
        Builder builder = builder(parts[0], parts[1], parts[2]);
        if (parts.length == 4) {
            builder.classifier(parts[3]);
        }
        return builder.build();
    }

    /** Maven group id. */
    @NotNull
    public String getGroup() {
        return group;
    }

    /** Maven artifact id. */
    @NotNull
    public String getArtifact() {
        return artifact;
    }

    /** Maven version. */
    @NotNull
    public String getVersion() {
        return version;
    }

    /** Optional classifier such as {@code shaded}. */
    @Nullable
    public String getClassifier() {
        return classifier;
    }

    /** The places the jar can come from, tried in the given order. */
    @NotNull
    public List<LibrarySource> getSources() {
        return sources;
    }

    /** Whether Maven Central is tried after the sources. */
    public boolean isMavenCentral() {
        return mavenCentral;
    }

    /** Which class loader the library joins; the plugin's own by default. */
    @NotNull
    public LibraryScope getScope() {
        return scope;
    }

    /**
     * The platforms the library is loaded on; empty means every platform. The platform is detected by pnLibrary: on
     * another platform the library is skipped, not an error.
     */
    @NotNull
    public Set<PlatformType> getPlatforms() {
        return platforms;
    }

    /** {@code true}: the module fails to start when the library cannot be loaded; {@code false}: it starts without it. */
    public boolean isRequired() {
        return required;
    }

    /** Short name players and administrators read, such as {@code Hikari connection pool}; the artifact id when absent. */
    @NotNull
    public String getTitle() {
        return title;
    }

    /** One or two sentences on what the library is for, shown in {@code /pn modules} and the menu; {@code null} when absent. */
    @Nullable
    public String getDescription() {
        return description;
    }

    /** {@code true} when the library lives in a class loader of its own. */
    public boolean isIsolated() {
        return scope == LibraryScope.ISOLATED;
    }

    /** Whether the library is loaded on {@code platform}; always when no platforms were listed, or the platform is unknown. */
    public boolean appliesTo(@Nullable PlatformType platform) {
        return platforms.isEmpty() || platform == null || platforms.contains(platform);
    }

    /** {@code group:artifact:version}, or {@code group:artifact:version:classifier}. */
    @NotNull
    public String getCoordinates() {
        String base = group + ":" + artifact + ":" + version;
        return classifier == null ? base : base + ":" + classifier;
    }

    /** Every place to try, in order: the sources, then Maven Central when it is on. */
    @NotNull
    public List<LibrarySource> getAllSources() {
        List<LibrarySource> all = new ArrayList<>(sources);
        if (mavenCentral) {
            all.add(CENTRAL_SOURCE);
        }
        return all;
    }

    @NotNull
    @Override
    public String toString() {
        return getCoordinates();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ModuleLibrary && ((ModuleLibrary) other).getCoordinates().equals(getCoordinates())
                && ((ModuleLibrary) other).scope == scope;
    }

    @Override
    public int hashCode() {
        return getCoordinates().hashCode() * 31 + scope.hashCode();
    }

    /** Builder; {@link #build} checks every value. */
    public static final class Builder {
        private final String group;
        private final String artifact;
        private final String version;
        private String classifier;
        private final List<LibrarySource> sources = new ArrayList<>();
        private boolean mavenCentral = true;
        private LibraryScope scope = LibraryScope.PLUGIN;
        private final Set<PlatformType> platforms = new LinkedHashSet<>();
        private boolean required = true;
        private String title;
        private String description;

        private Builder(String group, String artifact, String version) {
            this.group = group;
            this.artifact = artifact;
            this.version = version;
        }

        /** Sets the short name shown to people instead of the artifact id. */
        @NotNull
        public Builder title(@NotNull String value) {
            this.title = LibrarySource.blankToNull(value);
            return this;
        }

        /** Says what the library is for, in a sentence or two; players and administrators read it in the menu. */
        @NotNull
        public Builder description(@NotNull String value) {
            this.description = LibrarySource.blankToNull(value);
            return this;
        }

        /** Adds an alternative; call it as many times as you like, the first added is tried first. */
        @NotNull
        public Builder source(@NotNull LibrarySource source) {
            sources.add(source);
            return this;
        }

        /** Adds an alternative made in place: {@code .source(s -> s.link("https://...").sha256("..."))}. */
        @NotNull
        public Builder source(@NotNull Consumer<LibrarySource.Builder> configure) {
            LibrarySource.Builder builder = LibrarySource.builder();
            configure.accept(builder);
            return source(builder.build());
        }

        /** Sets the classifier of the jar, such as {@code shaded}. */
        @NotNull
        public Builder classifier(@NotNull String value) {
            this.classifier = LibrarySource.blankToNull(value);
            return this;
        }

        /** Chooses which class loader the library joins; {@link LibraryScope#PLUGIN} by default. */
        @NotNull
        public Builder scope(@NotNull LibraryScope value) {
            this.scope = value;
            return this;
        }

        /** Loads the library only on these platforms; without it every platform loads it. */
        @NotNull
        public Builder platforms(@NotNull PlatformType... types) {
            for (PlatformType type : types) {
                if (type == PlatformType.UNIVERSAL) {
                    throw new IllegalArgumentException("UNIVERSAL is not a runtime platform");
                }
                platforms.add(type);
            }
            return this;
        }

        /** Turns Maven Central on or off after the sources; on by default. */
        @NotNull
        public Builder mavenCentral(boolean enabled) {
            this.mavenCentral = enabled;
            return this;
        }

        /** Lets the module start even when this library cannot be loaded. */
        @NotNull
        public Builder optional() {
            this.required = false;
            return this;
        }

        /** Validates the library and creates it. */
        @NotNull
        public ModuleLibrary build() {
            if (!PART.matcher(group).matches() || !PART.matcher(artifact).matches()) {
                throw new IllegalArgumentException("Invalid library name: " + group + ":" + artifact);
            }
            if (!VERSION.matcher(version).matches()) {
                throw new IllegalArgumentException("Invalid library version: " + version);
            }
            if (classifier != null && !PART.matcher(classifier).matches()) {
                throw new IllegalArgumentException("Invalid classifier: " + classifier);
            }
            return new ModuleLibrary(this);
        }
    }
}
