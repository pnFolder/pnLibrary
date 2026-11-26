package org.pnfolder.pnlibrary.core.commands.cooldowns;

import java.time.Duration;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** One cooldown running now: who keeps it ({@code plugin/module}), its action and the time left. */
public final class RunningCooldown {
    private final String owner;
    private final String action;
    private final Duration remaining;

    public RunningCooldown(@NotNull String owner, @NotNull String action, @NotNull Duration remaining) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.action = Objects.requireNonNull(action, "action");
        this.remaining = Objects.requireNonNull(remaining, "remaining");
    }

    @NotNull
    public String getOwner() {
        return owner;
    }

    @NotNull
    public String getAction() {
        return action;
    }

    @NotNull
    public Duration getRemaining() {
        return remaining;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RunningCooldown)) {
            return false;
        }
        RunningCooldown that = (RunningCooldown) other;
        return owner.equals(that.owner) && action.equals(that.action) && remaining.equals(that.remaining);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, action, remaining);
    }

    @Override
    public String toString() {
        return "RunningCooldown(owner=" + owner + ", action=" + action + ", remaining=" + remaining + ")";
    }
}
