package org.pnfolder.pnlibrary.core.menu;

import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.spi.platform.PluginSnapshot;

/** Name, version and authors of the plugin that owns something. */
public final class OwnerInfo {
    private final String name;
    private final String version;
    private final List<String> authors;

    public OwnerInfo(@NotNull String name, @Nullable String version, @NotNull List<String> authors) {
        this.name = Objects.requireNonNull(name, "name");
        this.version = version;
        this.authors = Objects.requireNonNull(authors, "authors");
    }

    /** The owner a platform described as {@code plugin}; null stays null. */
    public static @Nullable OwnerInfo of(@Nullable PluginSnapshot plugin) {
        return plugin == null ? null : new OwnerInfo(plugin.getName(), plugin.getVersion(), plugin.getAuthors());
    }

    public @NotNull String getName() {
        return name;
    }

    public @Nullable String getVersion() {
        return version;
    }

    public @NotNull List<String> getAuthors() {
        return authors;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OwnerInfo)) {
            return false;
        }
        OwnerInfo that = (OwnerInfo) other;
        return name.equals(that.name) && Objects.equals(version, that.version) && authors.equals(that.authors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, version, authors);
    }

    @Override
    public String toString() {
        return "OwnerInfo(name=" + name + ", version=" + version + ", authors=" + authors + ")";
    }
}
