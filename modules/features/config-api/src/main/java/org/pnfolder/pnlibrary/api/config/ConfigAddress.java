package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/**
 * Address of a plugin, a module or a file. The shorter the text, the more files it covers:
 * {@code quests} covers the whole plugin, {@code quests/main} one module, {@code quests/main:quests.yml} one file.
 */
public final class ConfigAddress {
    private static final ConfigAddress EVERYWHERE = new ConfigAddress(null, null, null, null);

    private final PluginId owner;
    private final ModuleId module;
    private final ConfigRoot root;
    private final String path;

    private ConfigAddress(PluginId owner, ModuleId module, ConfigRoot root, String path) {
        this.owner = owner;
        this.module = module;
        this.root = root;
        this.path = path;
    }

    /** How much an address covers. */
    public enum Level { ALL, PLUGIN, MODULE, FILE }

    /** Plugin of the address. The address of everything ({@link #isAll()}) has none and fails here. */
    @NotNull
    public PluginId getPlugin() {
        if (owner == null) {
            throw new IllegalStateException("The address of every file has no plugin");
        }
        return owner;
    }

    /** Module, or {@code null} when the address covers the whole plugin. */
    @Nullable
    public ModuleId getModule() {
        return module;
    }

    /** Root of the path, {@code null} unless the address names a file. */
    @Nullable
    public ConfigRoot getRoot() {
        return root;
    }

    /** Path inside the root, {@code null} unless the address names a file. */
    @Nullable
    public String getPath() {
        return path;
    }

    /** {@code true} for {@link #everywhere()}: every file of every plugin. */
    public boolean isAll() {
        return owner == null;
    }

    /** Everything, a plugin, a module or a file. */
    @NotNull
    public Level getLevel() {
        if (isAll()) {
            return Level.ALL;
        }
        if (module == null) {
            return Level.PLUGIN;
        }
        return path == null ? Level.MODULE : Level.FILE;
    }

    /** Whether the file {@code id} lies inside this address. */
    public boolean matches(@NotNull ConfigId id) {
        return isAll() || (owner.equals(id.getPlugin()) && (module == null || module.equals(id.getModule()))
                && (path == null || (root == id.getRoot() && path.equals(id.getPath()))));
    }

    /** Whether some file can lie inside both this address and {@code other}. */
    public boolean overlaps(@NotNull ConfigAddress other) {
        if (isAll() || other.isAll()) {
            return true;
        }
        if (!owner.equals(other.owner)) {
            return false;
        }
        if (module != null && other.module != null && !module.equals(other.module)) {
            return false;
        }
        return !(path != null && other.path != null && (root != other.root || !path.equals(other.path)));
    }

    /**
     * This plugin's address narrowed to one module: {@code ConfigAddress.plugin("shop").inModule("main")}.
     * Only an address of a whole plugin can be narrowed.
     */
    @NotNull
    public ConfigAddress inModule(@NotNull ModuleId module) {
        if (getLevel() != Level.PLUGIN) {
            throw new IllegalArgumentException(
                    "Only the address of a whole plugin can be narrowed to a module, but this one is " + this);
        }
        return new ConfigAddress(getPlugin(), module, null, null);
    }

    /** Same as {@link #inModule(ModuleId)} with the module written as text. */
    @NotNull
    public ConfigAddress inModule(@NotNull String module) {
        return inModule(ModuleId.of(module));
    }

    /** Narrows a module's address to one file counted from the plugin folder. */
    @NotNull
    public ConfigAddress inFile(@NotNull String path) {
        return inFile(path, ConfigRoot.PLUGIN);
    }

    /**
     * This module's address narrowed to one file: {@code ConfigAddress.plugin("shop").inModule("main").inFile("rewards.yml")}.
     * {@code root} says where {@code path} is counted from.
     */
    @NotNull
    public ConfigAddress inFile(@NotNull String path, @NotNull ConfigRoot root) {
        if (getLevel() != Level.MODULE) {
            throw new IllegalArgumentException(
                    "Only the address of a module can be narrowed to a file, but this one is " + this);
        }
        String normalized = path.replace('\\', '/');
        String clean = normalized.startsWith(root.getPrefix()) ? normalized.substring(root.getPrefix().length()) : normalized;
        if (ConfigText.isBlank(clean)) {
            throw new IllegalArgumentException("The path of a file must not be blank");
        }
        return new ConfigAddress(getPlugin(), module, root, clean);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ConfigAddress && toString().equals(other.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    /** Text form: {@code *}, {@code plugin}, {@code plugin/module} or {@code plugin/module:path}. */
    @Override
    public String toString() {
        if (isAll()) {
            return "*";
        }
        StringBuilder text = new StringBuilder(owner.getValue());
        if (module != null) {
            text.append('/').append(module.getValue());
        }
        if (path != null) {
            text.append(':').append(root == null ? "" : root.getPrefix()).append(path);
        }
        return text.toString();
    }

    /** Address of every file of every plugin, written {@code *}. */
    @NotNull
    public static ConfigAddress everywhere() {
        return EVERYWHERE;
    }

    /** Address of a whole plugin. */
    @NotNull
    public static ConfigAddress plugin(@NotNull PluginId plugin) {
        return new ConfigAddress(plugin, null, null, null);
    }

    /** Address of a whole plugin written as its name. */
    @NotNull
    public static ConfigAddress plugin(@NotNull String plugin) {
        return plugin(PluginId.of(plugin));
    }

    /** Address of one module written as plugin and module names. */
    @NotNull
    public static ConfigAddress module(@NotNull String plugin, @NotNull String module) {
        return module(PluginId.of(plugin), ModuleId.of(module));
    }

    /** Address of one module. */
    @NotNull
    public static ConfigAddress module(@NotNull PluginId plugin, @NotNull ModuleId module) {
        return new ConfigAddress(plugin, module, null, null);
    }

    /** Address of one file. */
    @NotNull
    public static ConfigAddress file(@NotNull ConfigId id) {
        return new ConfigAddress(id.getPlugin(), id.getModule(), id.getRoot(), id.getPath());
    }

    /** Reads {@code *}, {@code plugin}, {@code plugin/module} or {@code plugin/module:path}. */
    @NotNull
    public static ConfigAddress parse(@NotNull String text) {
        String value = ConfigText.strip(text);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Configuration address must not be blank");
        }
        if (value.equals("*")) {
            return everywhere();
        }
        int colon = value.indexOf(':');
        String head = colon < 0 ? value : value.substring(0, colon);
        String[] parts = head.split("/", -1);
        if (parts.length > 2 || ConfigText.isBlank(parts[0]) || (parts.length == 2 && ConfigText.isBlank(parts[1]))) {
            throw new IllegalArgumentException(
                    "Invalid configuration address '" + text + "': expected plugin, plugin/module or plugin/module:path");
        }
        PluginId pluginId = PluginId.of(parts[0]);
        if (parts.length == 1) {
            if (colon >= 0) {
                throw new IllegalArgumentException(
                        "Invalid configuration address '" + text + "': a path needs a module (plugin/module:path)");
            }
            return new ConfigAddress(pluginId, null, null, null);
        }
        ModuleId moduleId = ModuleId.of(parts[1]);
        if (colon < 0) {
            return new ConfigAddress(pluginId, moduleId, null, null);
        }
        String path = value.substring(colon + 1).replace('\\', '/');
        ConfigRoot root = ConfigRoot.PLUGIN;
        for (ConfigRoot candidate : ConfigRoot.values()) {
            if (candidate != ConfigRoot.PLUGIN && path.startsWith(candidate.getPrefix())) {
                root = candidate;
                path = path.substring(candidate.getPrefix().length());
                break;
            }
        }
        if (ConfigText.isBlank(path)) {
            throw new IllegalArgumentException("Invalid configuration address '" + text + "': the path is empty");
        }
        return new ConfigAddress(pluginId, moduleId, root, path);
    }
}
