package org.pnfolder.pnlibrary.core.menu.data;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** One plugin line of the plugin list. */
public final class PluginRow {
    private final String name;
    private final String version;
    private final int modules;

    public PluginRow(@NotNull String name, @Nullable String version, int modules) {
        this.name = Objects.requireNonNull(name, "name");
        this.version = version;
        this.modules = modules;
    }

    public @NotNull String getName() {
        return name;
    }

    public @Nullable String getVersion() {
        return version;
    }

    public int getModules() {
        return modules;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PluginRow)) {
            return false;
        }
        PluginRow that = (PluginRow) other;
        return name.equals(that.name) && Objects.equals(version, that.version) && modules == that.modules;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, version, modules);
    }

    @Override
    public String toString() {
        return "PluginRow(name=" + name + ", version=" + version + ", modules=" + modules + ")";
    }
}
