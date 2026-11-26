package org.pnfolder.pnlibrary.api.commands.error;

import java.util.Collections;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/** The sender may not use the command or subcommand. */
public class NoPermission extends CommandError {
    private final String permission;

    /** @param permission missing permission; empty when an availability rule hid the command instead */
    public NoPermission(@NotNull String permission) {
        this.permission = permission;
    }

    /** Missing permission; empty when an availability rule hid the command instead. */
    @NotNull
    public final String getPermission() {
        return permission;
    }

    @NotNull
    @Override
    public Map<String, Object> placeholders() {
        return Collections.<String, Object>singletonMap("permission", permission);
    }
}
