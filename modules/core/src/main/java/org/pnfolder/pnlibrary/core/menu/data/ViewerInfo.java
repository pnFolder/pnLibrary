package org.pnfolder.pnlibrary.core.menu.data;

import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/** Name and permissions of the player who looks at the menu. */
public final class ViewerInfo {
    private final String name;
    private final Set<String> permissions;

    public ViewerInfo(@NotNull String name, @NotNull Set<String> permissions) {
        this.name = Objects.requireNonNull(name, "name");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
    }

    public @NotNull String getName() {
        return name;
    }

    public @NotNull Set<String> getPermissions() {
        return permissions;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ViewerInfo)) {
            return false;
        }
        ViewerInfo that = (ViewerInfo) other;
        return name.equals(that.name) && permissions.equals(that.permissions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, permissions);
    }

    @Override
    public String toString() {
        return "ViewerInfo(name=" + name + ", permissions=" + permissions + ")";
    }
}
