package org.pnfolder.pnlibrary.api.scenario.requirements.builtin;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.audience.AudienceExtensions;
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext;
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Met when the player has the listed permissions: all of them, or any one with {@code any}.
 *
 * <pre>{@code
 * type: permission
 * permission: shop.use          # or: permissions: [shop.use, shop.vip]
 * }</pre>
 */
public final class PermissionRequirement implements Requirement {
    private final String permission;
    private final List<String> permissions;
    private final boolean any;

    /** Creates it with every setting at its default. */
    public PermissionRequirement() {
        this("", Collections.<String>emptyList(), false);
    }

    /** Creates it with the first 1 setting; the rest keep their defaults. */
    public PermissionRequirement(@NotNull String permission) {
        this(permission, Collections.<String>emptyList(), false);
    }

    /** Creates it with the first 2 settings; the rest keep their defaults. */
    public PermissionRequirement(@NotNull String permission, @NotNull List<String> permissions) {
        this(permission, permissions, false);
    }

    /** Creates it with every setting given. */
    public PermissionRequirement(@NotNull String permission, @NotNull List<String> permissions, boolean any) {
        this.permission = permission;
        this.permissions = permissions;
        this.any = any;
    }

    /** One permission node, may be blank when {@code permissions} is used. */
    @NotNull
    public String getPermission() {
        return permission;
    }

    /** More permission nodes. */
    @NotNull
    public List<String> getPermissions() {
        return permissions;
    }

    /** Whether one permission is enough instead of all of them. */
    public boolean isAny() {
        return any;
    }

    @NotNull
    @Override
    public Verdict test(@NotNull RequirementContext context) {
        List<String> nodes = new ArrayList<>();
        if (!permission.trim().isEmpty()) {
            nodes.add(permission);
        }
        for (String node : permissions) {
            if (!node.trim().isEmpty()) {
                nodes.add(node);
            }
        }
        if (nodes.isEmpty()) {
            context.getLogger().warning("A permission requirement lists no permission and is never met");
            return new Verdict.Unmet("no permission is configured");
        }
        List<String> missing = new ArrayList<>();
        for (String node : nodes) {
            if (!AudienceExtensions.hasPermission(context.getPlayer(), node)) {
                missing.add(node);
            }
        }
        boolean met = any ? missing.size() < nodes.size() : missing.isEmpty();
        return Verdict.of(met, "missing permission " + String.join(", ", missing));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PermissionRequirement)) {
            return false;
        }
        PermissionRequirement that = (PermissionRequirement) other;
        return Objects.equals(permission, that.permission)
                && Objects.equals(permissions, that.permissions)
                && any == that.any;
    }

    @Override
    public int hashCode() {
        return Objects.hash(permission, permissions, any);
    }

    @NotNull
    @Override
    public String toString() {
        return "PermissionRequirement(permission=" + permission + ", permissions=" + permissions + ", any=" + any + ")";
    }
}
