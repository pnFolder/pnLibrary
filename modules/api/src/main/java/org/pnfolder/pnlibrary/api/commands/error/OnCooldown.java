package org.pnfolder.pnlibrary.api.commands.error;

import java.util.Objects;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/** The sender used the command again too soon. */
public class OnCooldown extends CommandError {
    private final Duration remaining;

    /** @param remaining how long to wait */
    public OnCooldown(@NotNull Duration remaining) {
        this.remaining = Objects.requireNonNull(remaining, "remaining");
    }

    /** How long to wait. */
    @NotNull
    public final Duration getRemaining() {
        return remaining;
    }

    @NotNull
    @Override
    public Map<String, Object> placeholders() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("seconds", (remaining.toMillis() + 999) / 1000);
        values.put("remaining", remaining);
        return values;
    }
}
