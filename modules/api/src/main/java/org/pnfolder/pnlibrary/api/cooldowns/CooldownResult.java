package org.pnfolder.pnlibrary.api.cooldowns;

import java.time.Duration;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.error.CommandException;
import org.pnfolder.pnlibrary.api.commands.error.OnCooldown;

/** Result of an atomic cooldown acquisition attempt. Create it with {@link #allowed()} or {@link #denied(Duration)}. */
public final class CooldownResult {
    private static final CooldownResult ALLOWED = new CooldownResult(true, Duration.ZERO);

    private final boolean allowed;
    private final Duration remaining;

    private CooldownResult(boolean allowed, Duration remaining) {
        this.allowed = allowed;
        this.remaining = remaining;
    }

    /** Whether the caller acquired a new cooldown. */
    public boolean getAllowed() {
        return allowed;
    }

    /** The time left on the existing cooldown when denied, otherwise zero. */
    public @NotNull Duration getRemaining() {
        return remaining;
    }

    /** Whether the caller must wait; the opposite of {@link #getAllowed()}. */
    public boolean isDenied() {
        return !allowed;
    }

    /** The wait in whole seconds, rounded up, so a player never reads "0 seconds" while still held back. */
    public long getRemainingSeconds() {
        return (remaining.toMillis() + 999) / 1000;
    }

    /**
     * Returns this result when it is allowed, and throws {@link OnCooldown} (as a {@link CommandException}) with the
     * time left when it is denied.
     */
    public @NotNull CooldownResult orThrow() {
        if (isDenied()) {
            throw new CommandException(new OnCooldown(remaining));
        }
        return this;
    }

    /** Renders as {@code allowed} or {@code denied: <remaining>}. */
    @Override
    public String toString() {
        return allowed ? "allowed" : "denied: " + remaining;
    }

    /** Returns the shared result meaning no cooldown is active. */
    public static @NotNull CooldownResult allowed() {
        return ALLOWED;
    }

    /** Returns a denied result with the time left on the active cooldown. */
    public static @NotNull CooldownResult denied(@NotNull Duration remaining) {
        return new CooldownResult(false, remaining);
    }
}
