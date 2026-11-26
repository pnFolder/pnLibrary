package org.pnfolder.pnlibrary.api.commands.error;

import java.util.Collections;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/** The command was typed wrongly. */
public class BadUsage extends CommandError {
    private final String usage;

    /** @param usage generated hint, such as {@code /shop buy <item>} */
    public BadUsage(@NotNull String usage) {
        this.usage = usage;
    }

    /** Generated hint, such as {@code /shop buy <item>}. */
    @NotNull
    public final String getUsage() {
        return usage;
    }

    @NotNull
    @Override
    public Map<String, Object> placeholders() {
        return Collections.<String, Object>singletonMap("usage", usage);
    }
}
