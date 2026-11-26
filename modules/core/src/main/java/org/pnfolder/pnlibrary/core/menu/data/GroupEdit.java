package org.pnfolder.pnlibrary.core.menu.data;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** One group change; the server and the plugin are set only for operations that need them. */
public final class GroupEdit {
    private final GroupOp op;
    private final String group;
    private final String server;
    private final String plugin;

    public GroupEdit(@NotNull GroupOp op, @NotNull String group, @Nullable String server, @Nullable String plugin) {
        this.op = op;
        this.group = group;
        this.server = server;
        this.plugin = plugin;
    }

    public GroupEdit(@NotNull GroupOp op, @NotNull String group) {
        this(op, group, null, null);
    }

    public GroupEdit(@NotNull GroupOp op, @NotNull String group, @Nullable String server) {
        this(op, group, server, null);
    }

    public @NotNull GroupOp getOp() {
        return op;
    }

    public @NotNull String getGroup() {
        return group;
    }

    public @Nullable String getServer() {
        return server;
    }

    public @Nullable String getPlugin() {
        return plugin;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof GroupEdit)) {
            return false;
        }
        GroupEdit that = (GroupEdit) other;
        return op == that.op && group.equals(that.group) && Objects.equals(server, that.server)
            && Objects.equals(plugin, that.plugin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(op, group, server, plugin);
    }

    @Override
    public String toString() {
        return "GroupEdit(op=" + op + ", group=" + group + ", server=" + server + ", plugin=" + plugin + ")";
    }
}
