package org.pnfolder.pnlibrary.api.cooldowns.builder;

import java.time.Duration;
import java.util.Objects;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;

/** What a {@code Cooldown.Builder.onStarted} handler is told when a cooldown begins. */
public final class CooldownStarted {
    private final Audience who;
    private final String action;
    private final Duration duration;

    /**
     * @param who the one it started for
     * @param action the name of the cooldown
     * @param duration how long it lasts for this one, after tiers
     */
    public CooldownStarted(@NotNull Audience who, @NotNull String action, @NotNull Duration duration) {
        this.who = Objects.requireNonNull(who, "who");
        this.action = Objects.requireNonNull(action, "action");
        this.duration = Objects.requireNonNull(duration, "duration");
    }

    /** The one it started for. */
    public @NotNull Audience getWho() {
        return who;
    }

    /** The name of the cooldown. */
    public @NotNull String getAction() {
        return action;
    }

    /** How long it lasts for this one, after tiers. */
    public @NotNull Duration getDuration() {
        return duration;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CooldownStarted)) {
            return false;
        }
        CooldownStarted that = (CooldownStarted) other;
        return who.equals(that.who) && action.equals(that.action) && duration.equals(that.duration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(who, action, duration);
    }

    @Override
    public String toString() {
        return "CooldownStarted(who=" + who + ", action=" + action + ", duration=" + duration + ")";
    }
}
