package org.pnfolder.pnlibrary.config;

import java.util.Objects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.config.ConfigOptions;
import org.pnfolder.pnlibrary.api.config.ConfigRoot;
import org.pnfolder.pnlibrary.api.config.MissingFilePolicy;
import org.pnfolder.pnlibrary.api.config.UnknownValuePolicy;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/**
 * Where the configuration files of one plugin may live, and what a path may be.
 *
 * <p>Every path a plugin gives is checked here: it must stay inside its root folder, must not leave it through a link, and
 * must name a YAML file. Kept apart from the service so the rules that keep a plugin out of other folders can be read
 * and tested on their own.
 */
final class ConfigPaths {
    private final Path directory;
    private final PluginId plugin;

    /**
     * Creates the rules for one plugin.
     *
     * @param directory the data folder of the plugin
     * @param plugin the plugin the files belong to, for messages
     */
    ConfigPaths(@NotNull Path directory, @NotNull PluginId plugin) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /** The folder paths of {@code root} are counted from. */
    @NotNull
    Path rootDirectory(@NotNull ConfigRoot root) {
        Path folder;
        switch (root) {
            case PLUGIN:
                folder = directory;
                break;
            case PLUGINS:
                folder = directory.getParent();
                break;
            case SERVER:
                Path parent = directory.getParent();
                folder = parent == null ? null : parent.getParent();
                break;
            default:
                throw new IllegalStateException("An external path is absolute and has no root folder");
        }
        if (folder == null) {
            throw new IllegalStateException("The " + root.name().toLowerCase(Locale.ROOT) + " folder is not known for " + plugin);
        }
        return folder;
    }

    /** Someone else's files get strict handling whatever the options ask: never created, always backed up, unknown keys kept. */
    @NotNull
    ConfigOptions effective(@NotNull ConfigOptions options) {
        if (options.getRoot() == ConfigRoot.PLUGIN || options.getRoot() == ConfigRoot.EXTERNAL) {
            return options;
        }
        UnknownValuePolicy unknown = options.getUnknownValues() == UnknownValuePolicy.REMOVE
                ? UnknownValuePolicy.PRESERVE : options.getUnknownValues();
        return options.toBuilder()
                .missingFile(MissingFilePolicy.FAIL)
                .backups(true)
                .unknownValues(unknown)
                .build();
    }

    /** An absolute path chosen by the plugin itself; only the file extension is checked. */
    @NotNull
    Path external(@NotNull String path, @NotNull Path relative) {
        if (!relative.isAbsolute()) {
            throw new IllegalArgumentException("An external configuration path must be absolute: " + path);
        }
        requireYaml(path);
        return relative;
    }

    /** The absolute file for {@code path} under the folder of {@code root}; rejects paths outside it, links out of it and other file extensions. */
    @NotNull
    Path checked(@NotNull String path, @NotNull Path relative, @NotNull ConfigRoot root) {
        Path base = rootDirectory(root);
        String label = root == ConfigRoot.PLUGIN ? "plugin directory" : root.name().toLowerCase(Locale.ROOT) + " folder";
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IllegalArgumentException("Configuration path must stay inside the " + label);
        }
        requireYaml(path);
        Path target = base.resolve(relative).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("Configuration path escapes the " + label);
        }
        if (Files.exists(target) && Files.exists(base) && !realPath(target).startsWith(realPath(base))) {
            throw new IllegalArgumentException("Configuration path leaves the " + label + " through a link");
        }
        return target;
    }

    private static Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static void requireYaml(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".yml") && !lower.endsWith(".yaml")) {
            throw new IllegalArgumentException("Configuration file must use .yml or .yaml");
        }
    }
}
