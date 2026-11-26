package org.pnfolder.pnlibrary.api.cooldowns.builder;

import java.time.Duration;
import java.util.Objects;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;

/** What a {@code Cooldown.Builder.onDenied} handler is told when someone is held back. */
public final class CooldownDenied {
    private final Audience who;
    private final String action;
    private final Duration remaining;

    /**
     * @param who the one who has to wait
     * @param action the name of the cooldown
     * @param remaining how long is left
     */
    public CooldownDenied(@NotNull Audience who, @NotNull String action, @NotNull Duration remaining) {
        this.who = Objects.requireNonNull(who, "who");
        this.action = Objects.requireNonNull(action, "action");
        this.remaining = Objects.requireNonNull(remaining, "remaining");
    }

    /** The one who has to wait. */
    public @NotNull Audience getWho() {
        return who;
    }

    /** The name of the cooldown. */
    public @NotNull String getAction() {
        return action;
    }

    /** How long is left. */
    public @NotNull Duration getRemaining() {
        return remaining;
    }

    /** The wait in whole seconds, rounded up, so nobody reads "0 seconds" while still held back. */
    public long getRemainingSeconds() {
        return (remaining.toMillis() + 999) / 1000;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CooldownDenied)) {
            return false;
        }
        CooldownDenied that = (CooldownDenied) other;
        return who.equals(that.who) && action.equals(that.action) && remaining.equals(that.remaining);
    }

    @Override
    public int hashCode() {
        return Objects.hash(who, action, remaining);
    }

    @Override
    public String toString() {
        return "CooldownDenied(who=" + who + ", action=" + action + ", remaining=" + remaining + ")";
    }
}
