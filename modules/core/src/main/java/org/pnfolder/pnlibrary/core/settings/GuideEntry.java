package org.pnfolder.pnlibrary.core.settings;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** One line of the command guide. Build it with {@link #of} and the {@code orAdmin}, {@code scope} and {@code note} copies. */
public final class GuideEntry {
    private final String usage;
    private final String permission;
    private final String description;
    private final boolean orAdmin;
    private final GuideScope scope;
    private final String note;

    private GuideEntry(String usage, String permission, String description, boolean orAdmin, GuideScope scope,
                       String note) {
        this.usage = usage;
        this.permission = permission;
        this.description = description;
        this.orAdmin = orAdmin;
        this.scope = scope;
        this.note = note;
    }

    /**
     * An entry that exists everywhere and is opened by {@code permission} alone.
     *
     * @param usage the command as it is typed, or the name of a right that has no command
     * @param permission the permission node, or null when the entry needs none
     * @param description what it does, one short sentence
     */
    public static @NotNull GuideEntry of(@NotNull String usage, @Nullable String permission,
                                         @NotNull String description) {
        return new GuideEntry(usage, permission, description, false, GuideScope.EVERYWHERE, null);
    }

    /** The same entry that {@code pnlibrary.admin} opens too. */
    public @NotNull GuideEntry orAdmin() {
        return new GuideEntry(usage, permission, description, true, scope, note);
    }

    /** The same entry that exists only in {@code scope}. */
    public @NotNull GuideEntry scope(@NotNull GuideScope value) {
        return new GuideEntry(usage, permission, description, orAdmin, value, note);
    }

    /** The same entry with one more thing the administrator should know. */
    public @NotNull GuideEntry note(@NotNull String value) {
        return new GuideEntry(usage, permission, description, orAdmin, scope, value);
    }

    /** The command as it is typed. */
    public @NotNull String getUsage() {
        return usage;
    }

    /** The permission node, or null when the entry needs none. */
    public @Nullable String getPermission() {
        return permission;
    }

    /** What it does. */
    public @NotNull String getDescription() {
        return description;
    }

    /** Whether {@code pnlibrary.admin} opens the command too. */
    public boolean getOrAdmin() {
        return orAdmin;
    }

    /** Where the command exists. */
    public @NotNull GuideScope getScope() {
        return scope;
    }

    /** One more thing the administrator should know, or null. */
    public @Nullable String getNote() {
        return note;
    }

    /** Whether the permission is a pattern such as {@code pnlibrary.currency.<валюта>.list}, not one node of plugin.yml. */
    public boolean isPattern() {
        return permission != null && permission.indexOf('<') >= 0;
    }
}
