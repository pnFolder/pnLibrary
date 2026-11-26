package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;

/**
 * The folder a configuration path is counted from.
 *
 * <p>A path never leaves its root. Files in {@link #PLUGINS} and {@link #SERVER} belong to somebody else, so they get
 * strict defaults: a missing file is never created, a backup is always made before a rewrite and unknown keys are
 * kept. {@link #EXTERNAL} is the plugin's own choice of an absolute path and has no such limits.
 */
public enum ConfigRoot {
    /** The data folder of the plugin: {@code plugins/Name/}. This is the default. */
    PLUGIN(""),

    /** The {@code plugins/} folder, for example a file of another plugin. */
    PLUGINS("@plugins/"),

    /** The server root, next to {@code server.properties}. */
    SERVER("@server/"),

    /**
     * Any place on the machine, given by an absolute path: another disk, a mounted or network share, a folder the
     * plugin learned from its own settings. The plugin opens it itself, so it is treated like its own file
     * (it may be created and rewritten). The path in the {@link ConfigId} is the absolute path with {@code /}
     * separators.
     */
    EXTERNAL("@external/");

    private final String prefix;

    ConfigRoot(String prefix) {
        this.prefix = prefix;
    }

    /** Text put in front of the path in a {@link ConfigId}, empty for {@link #PLUGIN}. */
    @NotNull
    public String getPrefix() {
        return prefix;
    }
}
