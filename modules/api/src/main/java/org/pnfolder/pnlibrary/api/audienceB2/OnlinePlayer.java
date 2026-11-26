package org.pnfolder.pnlibrary.api.audienceB2;

import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audienceB2.delivery.Delivery;
import org.pnfolder.pnlibrary.api.world.ItemMatcher;
import org.pnfolder.pnlibrary.api.world.Particle;
import org.pnfolder.pnlibrary.api.world.Position;
import org.pnfolder.pnlibrary.api.world.StatusEffect;

/**
 * A player who is online. It is an Adventure {@link Audience}, so messages, titles and sounds are Adventure's own
 * methods; name, nickname, language and permissions come from its pointers.
 *
 * <p>What a platform cannot do, the player does not do: a player seen from a proxy gets no effects and has no
 * position. Actions answer {@link Delivery#unsupported()}, reads answer {@code null}. Bukkit overrides all of them.
 *
 * <pre>{@code
 * OnlinePlayer player = library.audiences().player(uuid);
 * player.sendMessage(Component.text("Hello"));
 * player.addEffect(new StatusEffect(Key.key("minecraft:speed"), Duration.ofSeconds(10)));
 * }</pre>
 */
public interface OnlinePlayer extends Audience {
    /** The player's UUID. */
    @NotNull UUID getUniqueId();

    /** Gives the player a temporary status effect. */
    default @NotNull Delivery addEffect(@NotNull StatusEffect effect) {
        return Delivery.unsupported();
    }

    /** Spawns a burst of particles around the player. */
    default @NotNull Delivery spawnParticle(@NotNull Particle particle) {
        return Delivery.unsupported();
    }

    /** Runs {@code command}, without the leading slash, as the player. */
    default @NotNull Delivery runCommand(@NotNull String command) {
        return Delivery.unsupported();
    }

    /** Runs {@code command}, without the leading slash, as the console. */
    default @NotNull Delivery runConsoleCommand(@NotNull String command) {
        return Delivery.unsupported();
    }

    /** How many items accepted by {@code matcher} the player carries, or {@code null} when the platform cannot tell. */
    default @Nullable Integer countItems(@NotNull ItemMatcher matcher) {
        return null;
    }

    /** The experience level, or {@code null} when the platform cannot tell. */
    default @Nullable Double experienceLevel() {
        return null;
    }

    /** The total experience points, or {@code null} when the platform cannot tell. */
    default @Nullable Double experiencePoints() {
        return null;
    }

    /** Where the player is now, or {@code null} when the platform cannot tell. */
    default @Nullable Position position() {
        return null;
    }
}
