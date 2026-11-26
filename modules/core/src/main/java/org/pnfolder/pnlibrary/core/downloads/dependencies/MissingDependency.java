package org.pnfolder.pnlibrary.core.downloads.dependencies;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** One plugin that is not installed (or too old): what it is, why it is needed, and the addresses it can be had from. */
public final class MissingDependency {
    private final String plugin;
    private final String minimum;
    private final URI page;
    private final List<URI> sources;
    private final String title;
    private final String description;
    private final boolean required;
    private final boolean checked;
    private final String variant;
    private final boolean noFileForServer;

    /** A required plugin with no description, whose file is not checked. */
    public MissingDependency(@NotNull String plugin, @NotNull String minimum, @Nullable URI page,
                             @NotNull List<URI> sources) {
        this(plugin, minimum, page, sources, plugin, null, true, false, null, false);
    }

    private MissingDependency(String plugin, String minimum, URI page, List<URI> sources, String title,
                              String description, boolean required, boolean checked, String variant,
                              boolean noFileForServer) {
        this.plugin = plugin;
        this.minimum = minimum;
        this.page = page;
        this.sources = new ArrayList<URI>(sources);
        this.title = title;
        this.description = description;
        this.required = required;
        this.checked = checked;
        this.variant = variant;
        this.noFileForServer = noFileForServer;
    }

    /** The same plugin under a human title. */
    public @NotNull MissingDependency withTitle(@NotNull String value) {
        return new MissingDependency(plugin, minimum, page, sources, value, description, required, checked, variant,
            noFileForServer);
    }

    /** The same plugin with a text saying what it is needed for. */
    public @NotNull MissingDependency withDescription(@Nullable String value) {
        return new MissingDependency(plugin, minimum, page, sources, title, value, required, checked, variant,
            noFileForServer);
    }

    /** The same plugin marked required or optional. */
    public @NotNull MissingDependency withRequired(boolean value) {
        return new MissingDependency(plugin, minimum, page, sources, title, description, value, checked, variant,
            noFileForServer);
    }

    /** The same plugin; {@code value} is true when the author gave a SHA-256 the file is checked against. */
    public @NotNull MissingDependency withChecked(boolean value) {
        return new MissingDependency(plugin, minimum, page, sources, title, description, required, value, variant,
            noFileForServer);
    }

    /** The same plugin with the name of the chosen file, when the plugin has several for different servers. */
    public @NotNull MissingDependency withVariant(@Nullable String value) {
        return new MissingDependency(plugin, minimum, page, sources, title, description, required, checked, value,
            noFileForServer);
    }

    /** The same plugin; {@code value} is true when it has files, but none is made for this platform or Minecraft. */
    public @NotNull MissingDependency withNoFileForServer(boolean value) {
        return new MissingDependency(plugin, minimum, page, sources, title, description, required, checked, variant,
            value);
    }

    /** The plugin name. */
    public @NotNull String getPlugin() {
        return plugin;
    }

    /** The lowest version that works. */
    public @NotNull String getMinimum() {
        return minimum;
    }

    /** The page to download it from by hand. */
    public @Nullable URI getPage() {
        return page;
    }

    /** Direct addresses of the file, the main one first. */
    public @NotNull List<URI> getSources() {
        return new ArrayList<URI>(sources);
    }

    /** The name shown to people; the plugin name unless a title was given. */
    public @NotNull String getTitle() {
        return title;
    }

    /** What the plugin is needed for. */
    public @Nullable String getDescription() {
        return description;
    }

    /** Whether the plugin that needs it cannot work without it. */
    public boolean getRequired() {
        return required;
    }

    /** Whether the author gave a SHA-256 the file is checked against; else the file is taken as downloaded. */
    public boolean getChecked() {
        return checked;
    }

    /** Name of the chosen file when the plugin has several for different servers. */
    public @Nullable String getVariant() {
        return variant;
    }

    /** Whether the plugin has files, but none is made for this platform or Minecraft version. */
    public boolean getNoFileForServer() {
        return noFileForServer;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof MissingDependency)) {
            return false;
        }
        MissingDependency that = (MissingDependency) other;
        return plugin.equals(that.plugin) && minimum.equals(that.minimum) && Objects.equals(page, that.page)
            && sources.equals(that.sources) && title.equals(that.title)
            && Objects.equals(description, that.description) && required == that.required
            && checked == that.checked && Objects.equals(variant, that.variant)
            && noFileForServer == that.noFileForServer;
    }

    @Override
    public int hashCode() {
        return Objects.hash(plugin, minimum, page, sources, title, description, required, checked, variant,
            noFileForServer);
    }

    @Override
    public String toString() {
        return "MissingDependency(plugin=" + plugin + ", minimum=" + minimum + ", page=" + page
            + ", sources=" + sources + ", title=" + title + ", required=" + required + ")";
    }
}
