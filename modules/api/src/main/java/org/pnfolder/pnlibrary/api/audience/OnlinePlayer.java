package org.pnfolder.pnlibrary.api.audience;

import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.pointer.Pointers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.audience.delivery.Delivery;
import org.pnfolder.pnlibrary.api.world.ItemMatcher;
import org.pnfolder.pnlibrary.api.world.Particle;
import org.pnfolder.pnlibrary.api.world.Position;
import org.pnfolder.pnlibrary.api.world.StatusEffect;

/**
 * A player who is online: an Adventure {@link Audience} with a UUID. Platform modules implement it, so plugin code
 * never touches Bukkit, BungeeCord or Velocity player classes. Name, nickname, language and permissions are read from
 * {@link #pointers()} with the methods of {@link AudienceExtensions}.
 *
 * <p>Like every Adventure method, the ones below do nothing where the platform cannot do them: a player seen from a
 * proxy gets no effects and has no position. Delivery methods say so with {@link Delivery#unsupported()}, reads give
 * {@code null}.
 *
 * <pre>{@code
 * OnlinePlayer player = library.getAudiences().player(uuid);
 * player.sendMessage(Component.text("Hello, " + AudienceExtensions.name(player)));
 * player.addEffect(new StatusEffect(Key.key("minecraft:speed"), Duration.ofSeconds(10)));
 * Integer diamonds = player.countItems(new ItemMatcher(Key.key("diamond")));
 * }</pre>
 */
public interface OnlinePlayer extends Audience {
    /** The stable player UUID supplied by the server platform. */
    @NotNull UUID getUniqueId();

    /**
     * Who the player is: name, nickname, language, UUID and permission check, built with
     * {@link AudienceExtensions#playerPointers}. Read them with {@link AudienceExtensions}, the same way as for the console.
     */
    @Override
    @NotNull Pointers pointers();

    /** Gives the player a temporary status effect. */
    default @NotNull Delivery addEffect(@NotNull StatusEffect effect) {
        return Delivery.unsupported();
    }

    /** Spawns a burst of particles around the player. */
    default @NotNull Delivery spawnParticle(@NotNull Particle particle) {
        return Delivery.unsupported();
    }

    /** Runs {@code command}, a command line without the leading slash, as the player. */
    default @NotNull Delivery runCommand(@NotNull String command) {
        return Delivery.unsupported();
    }

    /** Runs {@code command}, a command line without the leading slash, as the console. */
    default @NotNull Delivery runConsoleCommand(@NotNull String command) {
        return Delivery.unsupported();
    }

    /** How many items accepted by {@code matcher} the player carries, or {@code null} when the platform cannot tell. */
    default @Nullable Integer countItems(@NotNull ItemMatcher matcher) {
        return null;
    }

    /** The experience level, or {@code null} when the platform cannot tell. */
    default @Nullable Double getExperienceLevel() {
        return null;
    }

    /** The total experience points, or {@code null} when the platform cannot tell. */
    default @Nullable Double getExperiencePoints() {
        return null;
    }

    /** Where the player is now, or {@code null} when the platform cannot tell. */
    default @Nullable Position getPosition() {
        return null;
    }
}
