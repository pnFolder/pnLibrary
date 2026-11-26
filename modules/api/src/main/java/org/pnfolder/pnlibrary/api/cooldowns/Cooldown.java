package org.pnfolder.pnlibrary.api.cooldowns;

import java.time.Duration;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.cooldowns.builder.CooldownDenied;
import org.pnfolder.pnlibrary.api.cooldowns.builder.CooldownStarted;
import org.pnfolder.pnlibrary.api.cooldowns.builder.TierPick;

/**
 * One cooldown with its name, length and behavior fixed once, so the call site says what it asks and nothing else.
 * Get it from {@link CooldownService#cooldown(String, Consumer)}. The console has no cooldown, and neither has a
 * player who holds the bypass permission.
 *
 * <p>The simple way, with the library's own answer to a player who comes too soon:
 *
 * <pre>{@code
 * Cooldown home = cooldowns.cooldown("home", Duration.ofSeconds(30), "shop.home.nocooldown");
 * home.require(sender);   // too soon: the command stops and the player reads how long to wait
 * teleportHome(sender);
 * }</pre>
 *
 * <p>Your own message and your own actions, a shorter wait for donors, one timer for everybody:
 *
 * <pre>{@code
 * Cooldown kit = cooldowns.cooldown("kit", c -> c
 *     .duration(Duration.ofHours(1))
 *     .tier("shop.donor", Duration.ofMinutes(20))              // shorter for donors
 *     .tier(Arrays.asList("shop.newbie", "shop.trial"), 2.0)   // twice as long for either group
 *     .bypass("shop.admin", "shop.staff")                      // any one of them: no wait at all
 *     .onDenied(d -> d.getWho().sendActionBar(Component.text("Kit again in " + d.getRemainingSeconds() + " s"))));
 * kit.run(player, () -> giveKit(player));                      // runs only when allowed, otherwise your onDenied
 * }</pre>
 */
@ApiStatus.NonExtendable
public interface Cooldown {
    /** The action name this cooldown is kept under. */
    @NotNull String getAction();

    /** How long the cooldown lasts when no tier applies. */
    @NotNull Duration getDuration();

    /** How long the cooldown lasts for {@code who}: the tier {@code who} holds (see {@link TierPick}), otherwise {@link #getDuration()}. */
    @NotNull Duration durationFor(@NotNull Audience who);

    /** Starts the cooldown for {@code subject} unless it is still running; see {@link CooldownService#acquire}. */
    @NotNull CooldownResult acquire(@NotNull UUID subject);

    /**
     * Starts the cooldown for {@code who} unless it is still running. Always allowed for the console and for a player
     * with the bypass permission. Calls the {@code onStarted} handler when it starts; never calls {@code onDenied}.
     */
    @NotNull CooldownResult acquire(@NotNull Audience who);

    /**
     * Runs {@code action} and starts the cooldown, or, when {@code who} has to wait, calls the {@code onDenied}
     * handler (or the library's own answer) and skips {@code action}.
     */
    @NotNull CooldownResult run(@NotNull Audience who, @NotNull Runnable action);

    /**
     * Starts the cooldown for {@code who}, or stops the command with an
     * {@link org.pnfolder.pnlibrary.api.commands.error.OnCooldown} answer holding the time left.
     */
    default void require(@NotNull Audience who) {
        acquire(who).orThrow();
    }

    /** The time left for {@code who}, or {@link Duration#ZERO} when it is not running or {@code who} has no cooldown. */
    @NotNull Duration remaining(@NotNull Audience who);

    /** Whether {@code who} may act now; nothing is started. */
    default boolean isReady(@NotNull Audience who) {
        return remaining(who).isZero();
    }

    /** Ends the cooldown for {@code subject} and says whether it was running. */
    boolean reset(@NotNull UUID subject);

    /** Ends the cooldown for {@code who} and says whether it was running. */
    boolean reset(@NotNull Audience who);

    /** The settings of a {@link Cooldown}; every call returns the builder, and unset parts keep their defaults. */
    interface Builder {
        /** How long the cooldown lasts when no tier applies; required, and not negative. */
        @NotNull Builder duration(@NotNull Duration value);

        /** A player who holds any one of {@code permissions} never waits; the console never does either. Calls add up. */
        @NotNull Builder bypass(@NotNull String... permissions);

        /**
         * A player for whom {@code condition} is true never waits, for what a permission cannot say (a world, a
         * rank, a state). Calls add up.
         */
        @NotNull Builder bypassIf(@NotNull Predicate<Audience> condition);

        /** A player who holds {@code permission} waits {@code value} instead, longer or shorter than the duration. */
        @NotNull Builder tier(@NotNull String permission, @NotNull Duration value);

        /** A player who holds any one of {@code permissions} waits {@code value} instead. */
        @NotNull Builder tier(@NotNull Collection<String> permissions, @NotNull Duration value);

        /** A player who holds {@code permission} waits the duration times {@code factor}: {@code 0.5} halves the wait, {@code 2.0} doubles it. Not negative. */
        @NotNull Builder tier(@NotNull String permission, double factor);

        /** A player who holds any one of {@code permissions} waits the duration times {@code factor}. */
        @NotNull Builder tier(@NotNull Collection<String> permissions, double factor);

        /** Which tier applies when a player holds several: {@link TierPick#FIRST} by default. */
        @NotNull Builder pick(@NotNull TierPick pick);

        /** One timer for everybody instead of one per player: whoever uses it first holds everyone else back. */
        @NotNull Builder shared();

        /** What to do when someone is held back by {@link Cooldown#run}; replaces the library's own message. */
        @NotNull Builder onDenied(@NotNull Consumer<CooldownDenied> handler);

        /** What to do each time a cooldown starts. */
        @NotNull Builder onStarted(@NotNull Consumer<CooldownStarted> handler);
    }
}
