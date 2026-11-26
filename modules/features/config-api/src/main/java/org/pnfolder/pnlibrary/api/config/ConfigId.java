package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

import java.util.Objects;

/**
 * Address of one configuration file: plugin, module, root and path.
 *
 * <p>Text form: {@code plugin/module:path}. A path outside the plugin folder starts with its root:
 * {@code quests/main:@plugins/Essentials/config.yml}.
 */
public final class ConfigId {
    private final PluginId plugin;
    private final ModuleId module;
    private final ConfigRoot root;
    private final String path;

    /** Creates an identifier; {@code path} is counted from {@code root} and does not start with {@code @}. */
    public ConfigId(@NotNull PluginId plugin, @NotNull ModuleId module, @NotNull ConfigRoot root, @NotNull String path) {
        if (ConfigText.isBlank(path)) {
            throw new IllegalArgumentException("Configuration path must not be blank");
        }
        if (path.startsWith("@")) {
            throw new IllegalArgumentException("Configuration path must not start with '@'; use the root parameter");
        }
        this.plugin = plugin;
        this.module = module;
        this.root = root;
        this.path = path;
    }

    /** Plugin that owns the file. */
    @NotNull
    public PluginId getPlugin() {
        return plugin;
    }

    /** Module that owns the file. */
    @NotNull
    public ModuleId getModule() {
        return module;
    }

    /** Folder the path is counted from. */
    @NotNull
    public ConfigRoot getRoot() {
        return root;
    }

    /** Path inside the root. */
    @NotNull
    public String getPath() {
        return path;
    }

    /** File name without folders: {@code daily.yml} for {@code rewards/daily.yml}. */
    @NotNull
    public String getFileName() {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /** Folders in front of the file name, empty when the file lies in the root. */
    @NotNull
    public String getDirectory() {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    /** The address of the module that owns this file. */
    @NotNull
    public ConfigAddress getModuleAddress() {
        return ConfigAddress.module(plugin, module);
    }

    /** The address of this exact file. */
    @NotNull
    public ConfigAddress getAddress() {
        return ConfigAddress.file(this);
    }

    /** Builds an identifier from plain strings, with the plugin folder as root. */
    @NotNull
    public static ConfigId of(@NotNull String plugin, @NotNull String module, @NotNull String path) {
        return of(plugin, module, path, ConfigRoot.PLUGIN);
    }

    /** Builds an identifier from plain strings. */
    @NotNull
    public static ConfigId of(@NotNull String plugin, @NotNull String module, @NotNull String path,
                              @NotNull ConfigRoot root) {
        return new ConfigId(PluginId.of(plugin), ModuleId.of(module), root, path.replace('\\', '/'));
    }

    /** Reads {@code plugin/module:path}, with an optional {@code @plugins/} or {@code @server/} in front of the path. */
    @NotNull
    public static ConfigId parse(@NotNull String text) {
        ConfigAddress address = ConfigAddress.parse(text);
        if (address.getLevel() != ConfigAddress.Level.FILE) {
            throw new IllegalArgumentException("'" + text + "' is not a file address: expected plugin/module:path");
        }
        return new ConfigId(address.getPlugin(), Objects.requireNonNull(address.getModule()),
                Objects.requireNonNull(address.getRoot()), Objects.requireNonNull(address.getPath()));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConfigId)) {
            return false;
        }
        ConfigId that = (ConfigId) other;
        return plugin.equals(that.plugin) && module.equals(that.module) && root == that.root && path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(plugin, module, root, path);
    }

    /** Text form {@code plugin/module:path}. */
    @Override
    public String toString() {
        return plugin.getValue() + "/" + module.getValue() + ":" + root.getPrefix() + path;
    }
}
