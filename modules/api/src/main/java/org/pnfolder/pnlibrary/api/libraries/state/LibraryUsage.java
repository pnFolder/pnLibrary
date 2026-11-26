package org.pnfolder.pnlibrary.api.libraries.state;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

import java.util.Objects;

/** Who registered a library: a plugin for all its modules, or one module. */
public final class LibraryUsage {
    private final ModuleLibrary library;
    private final PluginId plugin;
    private final ModuleId module;
    private final LibraryStatus status;

    /** A usage whose status is unknown. */
    public LibraryUsage(@NotNull ModuleLibrary library, @NotNull PluginId plugin, @Nullable ModuleId module) {
        this(library, plugin, module, null);
    }

    /**
     * Creates a usage.
     *
     * @param library the library
     * @param plugin the plugin that owns it
     * @param module the module that registered it, or {@code null} when the whole plugin did
     * @param status where it stands (loaded, switched off, failed) and what the administrator chose, or {@code null} when unknown
     */
    public LibraryUsage(@NotNull ModuleLibrary library, @NotNull PluginId plugin, @Nullable ModuleId module,
                        @Nullable LibraryStatus status) {
        this.library = Objects.requireNonNull(library);
        this.plugin = Objects.requireNonNull(plugin);
        this.module = module;
        this.status = status;
    }

    /** The library. */
    @NotNull
    public ModuleLibrary getLibrary() {
        return library;
    }

    /** The plugin that owns it. */
    @NotNull
    public PluginId getPlugin() {
        return plugin;
    }

    /** The module that registered it, or {@code null} when the whole plugin did. */
    @Nullable
    public ModuleId getModule() {
        return module;
    }

    /** Where it stands, or {@code null} when unknown. */
    @Nullable
    public LibraryStatus getStatus() {
        return status;
    }

    /** {@code plugin} or {@code plugin:module}, the text used in commands. */
    @NotNull
    public String getOwner() {
        return module == null ? plugin.getValue() : plugin.getValue() + ":" + module.getValue();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LibraryUsage)) {
            return false;
        }
        LibraryUsage that = (LibraryUsage) other;
        return library.equals(that.library) && plugin.equals(that.plugin) && Objects.equals(module, that.module)
                && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(library, plugin, module, status);
    }

    @NotNull
    @Override
    public String toString() {
        return "LibraryUsage(library=" + library + ", plugin=" + plugin + ", module=" + module + ", status=" + status + ")";
    }
}
