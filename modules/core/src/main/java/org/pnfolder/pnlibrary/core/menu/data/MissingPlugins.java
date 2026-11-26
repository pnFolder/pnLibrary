package org.pnfolder.pnlibrary.core.menu.data;

import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * One group of missing plugins, as the menu shows it. The code is what {@code /pn deps install <code>} takes, the
 * owner is the plugin that asks for them, installable says whether the library can download them (otherwise they are
 * put in by hand), the lines hold one line per plugin (name, version, required or not), the links are the addresses
 * to get them from.
 */
public final class MissingPlugins {
    private final String code;
    private final String owner;
    private final boolean installable;
    private final List<String> lines;
    private final List<String> links;

    public MissingPlugins(@NotNull String code, @NotNull String owner, boolean installable,
                          @NotNull List<String> lines, @NotNull List<String> links) {
        this.code = code;
        this.owner = owner;
        this.installable = installable;
        this.lines = lines;
        this.links = links;
    }

    public @NotNull String getCode() {
        return code;
    }

    public @NotNull String getOwner() {
        return owner;
    }

    public boolean isInstallable() {
        return installable;
    }

    public @NotNull List<String> getLines() {
        return lines;
    }

    public @NotNull List<String> getLinks() {
        return links;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof MissingPlugins)) {
            return false;
        }
        MissingPlugins that = (MissingPlugins) other;
        return code.equals(that.code) && owner.equals(that.owner) && installable == that.installable
            && lines.equals(that.lines) && links.equals(that.links);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, owner, installable, lines, links);
    }

    @Override
    public String toString() {
        return "MissingPlugins(code=" + code + ", owner=" + owner + ", installable=" + installable + ")";
    }
}
