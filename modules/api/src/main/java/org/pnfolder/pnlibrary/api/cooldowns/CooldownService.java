package org.pnfolder.pnlibrary.api.cooldowns;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Plugin-scoped, thread-safe cooldown storage based on monotonic elapsed time.
 *
 * <p>Action names are trimmed and compared case-insensitively. Durations must not be
 * negative; a zero duration removes or immediately expires an entry. Methods that read
 * or create cooldowns fail after {@link #close()}, while cleanup operations remain idempotent. Expired entries are
 * dropped as the service is used, so a subject that is never asked again does not stay in memory.
 */
@ApiStatus.NonExtendable
public interface CooldownService extends AutoCloseable {
    /** Atomically creates a cooldown unless an unexpired entry already exists. */
    @NotNull CooldownResult acquire(@NotNull UUID subject, @NotNull String action, @NotNull Duration duration);

    /** Returns whether {@code subject} currently has an active cooldown for {@code action}. */
    boolean has(@NotNull UUID subject, @NotNull String action);

    /** Returns the non-negative remaining duration, or {@link Duration#ZERO} when inactive. */
    @NotNull Duration remaining(@NotNull UUID subject, @NotNull String action);

    /** Replaces the deadline; a zero {@code duration} removes the entry. */
    void set(@NotNull UUID subject, @NotNull String action, @NotNull Duration duration);

    /** Extends from the current deadline, or from now when no active entry exists. */
    @NotNull Duration extend(@NotNull UUID subject, @NotNull String action, @NotNull Duration duration);

    /** Removes one entry and reports whether an entry was present. */
    boolean reset(@NotNull UUID subject, @NotNull String action);

    /** Removes every entry owned by this service. */
    void clear();

    /** Removes every entry of {@code subject}, such as when a player leaves; returns how many were removed. */
    int clear(@NotNull UUID subject);

    /** The active cooldowns of {@code subject} by action with the time left, longest first; expired ones are not listed. */
    @NotNull Map<String, Duration> active(@NotNull UUID subject);

    /**
     * A named cooldown of fixed {@code duration} for {@code action}. A player holding {@code bypassPermission} is
     * never held back; the console never is either.
     */
    @NotNull Cooldown cooldown(@NotNull String action, @NotNull Duration duration, @Nullable String bypassPermission);

    /** A named cooldown of fixed {@code duration} for {@code action} that nobody can bypass but the console. */
    default @NotNull Cooldown cooldown(@NotNull String action, @NotNull Duration duration) {
        return cooldown(action, duration, null);
    }

    /** A named cooldown with everything {@link Cooldown.Builder} offers: tiers, your own reactions, one shared timer. */
    @NotNull Cooldown cooldown(@NotNull String action, @NotNull Consumer<Cooldown.Builder> configure);

    /** Number of active entries after opportunistic expiry cleanup. */
    int getSize();
}
