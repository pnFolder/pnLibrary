package org.pnfolder.pnlibrary.loader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where and how the light JAR fetches its libraries. The values live here, in code; change {@link #DEFAULT} and
 * rebuild. A repository for one library only is set where the library is listed, in
 * {@code platforms/loader/build.gradle.kts}.
 *
 * <p>For each library Libby looks in the library folder first (see {@link #directoryFor(Path)}) and downloads
 * nothing if the file is there. By default it is {@code libraries/pnlibrary} in the server folder, shared by every
 * plugin and kept when a plugin's own folder is deleted.
 * Otherwise it tries the repositories in this order, taking the first that answers: the library's own, then
 * {@link #repositories()}, then Maven Central, then {@link #fallbacks()}. Whatever answers, the file must match the
 * SHA-256 recorded at build time, so a mirror cannot swap a library.
 */
public final class LibrarySettings {
    /** The settings every platform uses. */
    public static final LibrarySettings DEFAULT = builder()
            // .repository("https://example.com/maven/")   // your own mirror, tried before Maven Central
            .fallback("https://maven-central.storage-download.googleapis.com/maven2/")
            .build();

    private final String directory;
    private final boolean inServerFolder;
    private final List<String> repositories;
    private final List<String> fallbacks;
    private final boolean mavenCentral;
    private final int attempts;

    private LibrarySettings(Builder builder) {
        this.directory = builder.directory;
        this.inServerFolder = builder.inServerFolder;
        this.repositories = Collections.unmodifiableList(new ArrayList<>(builder.repositories));
        this.fallbacks = Collections.unmodifiableList(new ArrayList<>(builder.fallbacks));
        this.mavenCentral = builder.mavenCentral;
        this.attempts = builder.attempts;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Folder for downloaded JARs, as an absolute path. {@code dataFolder} is the plugin's own folder, for example
     * {@code plugins/pnLibrary}; the server folder is the parent of its {@code plugins} folder.
     */
    public String directoryFor(Path dataFolder) {
        Path plugin = dataFolder.toAbsolutePath().normalize();
        Path plugins = plugin.getParent();
        Path server = plugins == null ? null : plugins.getParent();
        Path base = inServerFolder && server != null ? server : plugin;
        return base.resolve(directory).normalize().toString();
    }

    /** Own repositories for every library, tried before Maven Central, in this order. */
    public List<String> repositories() {
        return repositories;
    }

    /** Mirrors tried after Maven Central. */
    public List<String> fallbacks() {
        return fallbacks;
    }

    /** Whether Maven Central is used at all. When false only {@link #repositories()} are. */
    public boolean mavenCentral() {
        return mavenCentral;
    }

    /** Tries per library; Libby gives each download only a few seconds, so one slow answer should not be final. */
    public int attempts() {
        return attempts;
    }

    public static final class Builder {
        private String directory = "libraries/pnlibrary";
        private boolean inServerFolder = true;
        private final List<String> repositories = new ArrayList<>();
        private final List<String> fallbacks = new ArrayList<>();
        private boolean mavenCentral = true;
        private int attempts = 3;

        private Builder() {
        }

        /** Stores JARs in this folder of the server (next to {@code plugins}); the default is {@code libraries/pnlibrary}. */
        public Builder serverDirectory(String directory) {
            this.directory = requireRelative(directory);
            this.inServerFolder = true;
            return this;
        }

        /** Stores JARs in this folder inside the plugin's own data folder, for example {@code lib}. */
        public Builder pluginDirectory(String directory) {
            this.directory = requireRelative(directory);
            this.inServerFolder = false;
            return this;
        }

        /** Adds an own repository, tried before Maven Central. Call again for more; the order is kept. */
        public Builder repository(String url) {
            repositories.add(requireUrl(url));
            return this;
        }

        /** Adds a mirror tried after Maven Central. */
        public Builder fallback(String url) {
            fallbacks.add(requireUrl(url));
            return this;
        }

        /** {@code false} to download only from {@link #repository(String)} entries; {@code true} by default. */
        public Builder mavenCentral(boolean mavenCentral) {
            this.mavenCentral = mavenCentral;
            return this;
        }

        /** Tries per library, at least 1; 3 by default. */
        public Builder attempts(int attempts) {
            if (attempts < 1) {
                throw new IllegalArgumentException("attempts must be at least 1");
            }
            this.attempts = attempts;
            return this;
        }

        public LibrarySettings build() {
            if (!mavenCentral && repositories.isEmpty()) {
                throw new IllegalStateException("Without Maven Central at least one repository is needed");
            }
            return new LibrarySettings(this);
        }

        private static String requireRelative(String directory) {
            if (directory == null || directory.trim().isEmpty() || directory.contains("..")
                    || directory.startsWith("/") || directory.startsWith("\\") || directory.contains(":")) {
                throw new IllegalArgumentException("Folder must be a relative path without '..': " + directory);
            }
            return directory.trim();
        }

        private static String requireUrl(String url) {
            if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
                throw new IllegalArgumentException("Repository must be an http(s) URL: " + url);
            }
            return url;
        }
    }
}
