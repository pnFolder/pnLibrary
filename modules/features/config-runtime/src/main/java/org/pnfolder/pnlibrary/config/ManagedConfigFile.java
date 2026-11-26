package org.pnfolder.pnlibrary.config;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.config.yaml.database.DatabasePath;

/** A file registered through a configuration scope. */
public final class ManagedConfigFile {
    private final PluginId pluginId;
    private final Path directory;
    private final String path;
    private final List<String> secretPaths;
    private final List<DatabasePath> databases;

    /**
     * Creates a file without databases.
     *
     * @param pluginId plugin that registered the file
     * @param directory data folder of the plugin
     * @param path path relative to {@code directory}, with {@code /} separators
     * @param secretPaths dotted paths of the {@code @ConfigSecret} fields
     */
    public ManagedConfigFile(@NotNull PluginId pluginId, @NotNull Path directory, @NotNull String path,
                             @NotNull List<String> secretPaths) {
        this(pluginId, directory, path, secretPaths, Collections.emptyList());
    }

    /**
     * Creates a file.
     *
     * @param pluginId plugin that registered the file
     * @param directory data folder of the plugin
     * @param path path relative to {@code directory}, with {@code /} separators
     * @param secretPaths dotted paths of the {@code @ConfigSecret} fields
     * @param databases sections marked {@code @BackupDatabase}
     */
    public ManagedConfigFile(@NotNull PluginId pluginId, @NotNull Path directory, @NotNull String path,
                             @NotNull List<String> secretPaths, @NotNull List<DatabasePath> databases) {
        this.pluginId = Objects.requireNonNull(pluginId, "pluginId");
        this.directory = Objects.requireNonNull(directory, "directory");
        this.path = Objects.requireNonNull(path, "path");
        this.secretPaths = Objects.requireNonNull(secretPaths, "secretPaths");
        this.databases = Objects.requireNonNull(databases, "databases");
    }

    /** Plugin that registered the file. */
    @NotNull
    public PluginId getPluginId() {
        return pluginId;
    }

    /** Data folder of the plugin. */
    @NotNull
    public Path getDirectory() {
        return directory;
    }

    /** Path relative to {@link #getDirectory()}, with {@code /} separators. */
    @NotNull
    public String getPath() {
        return path;
    }

    /** Dotted paths of the {@code @ConfigSecret} fields. */
    @NotNull
    public List<String> getSecretPaths() {
        return secretPaths;
    }

    /** Sections marked {@code @BackupDatabase}. */
    @NotNull
    public List<DatabasePath> getDatabases() {
        return databases;
    }

    @Override
    public String toString() {
        return "ManagedConfigFile(pluginId=" + pluginId + ", directory=" + directory + ", path=" + path + ")";
    }
}
