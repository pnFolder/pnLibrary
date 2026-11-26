package org.pnfolder.pnlibrary.api.world;

import java.time.Duration;
import java.util.Objects;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable description of a temporary status effect on a player.
 *
 * <p>The key is an Adventure key so the API never exposes Bukkit, BungeeCord, Velocity or version-specific Minecraft
 * types; platform adapters resolve it to the native effect.
 *
 * <pre>{@code
 * StatusEffect speed = new StatusEffect(Key.key("minecraft:speed"), Duration.ofSeconds(10), 1, true, true, true);
 * }</pre>
 */
public final class StatusEffect {
    private static final Duration DEFAULT_DURATION = Duration.ofSeconds(30);

    private final Key key;
    private final Duration duration;
    private final int amplifier;
    private final boolean ambient;
    private final boolean particles;
    private final boolean icon;

    /**
     * @param key effect key, such as {@code minecraft:speed}
     * @param duration how long the effect stays; must be positive
     * @param amplifier zero-based level, where {@code 0} is level I
     * @param ambient whether the platform marks the effect as ambient
     * @param particles whether the regular effect particles are visible
     * @param icon whether the client shows the effect icon
     */
    public StatusEffect(@NotNull Key key, @NotNull Duration duration, int amplifier, boolean ambient,
                        boolean particles, boolean icon) {
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("Effect duration must be positive");
        }
        if (amplifier < 0) {
            throw new IllegalArgumentException("Effect amplifier must not be negative");
        }
        this.key = Objects.requireNonNull(key, "key");
        this.duration = duration;
        this.amplifier = amplifier;
        this.ambient = ambient;
        this.particles = particles;
        this.icon = icon;
    }

    /** A level I effect of {@code key} that lasts 30 seconds. */
    public StatusEffect(@NotNull Key key) {
        this(key, DEFAULT_DURATION, 0, true, true, true);
    }

    /** A level I effect of {@code key} that lasts {@code duration}. */
    public StatusEffect(@NotNull Key key, @NotNull Duration duration) {
        this(key, duration, 0, true, true, true);
    }

    /** An effect of {@code key} at zero-based level {@code amplifier} that lasts {@code duration}. */
    public StatusEffect(@NotNull Key key, @NotNull Duration duration, int amplifier) {
        this(key, duration, amplifier, true, true, true);
    }

    /** The effect key, such as {@code minecraft:speed}. */
    public @NotNull Key getKey() {
        return key;
    }

    /** How long the effect stays. */
    public @NotNull Duration getDuration() {
        return duration;
    }

    /** The zero-based level, where {@code 0} is level I. */
    public int getAmplifier() {
        return amplifier;
    }

    /** Whether the platform marks the effect as ambient. */
    public boolean isAmbient() {
        return ambient;
    }

    /** Whether the regular effect particles are visible. */
    public boolean isParticles() {
        return particles;
    }

    /** Whether the client shows the effect icon. */
    public boolean isIcon() {
        return icon;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StatusEffect)) {
            return false;
        }
        StatusEffect that = (StatusEffect) other;
        return amplifier == that.amplifier && ambient == that.ambient && particles == that.particles
            && icon == that.icon && key.equals(that.key) && duration.equals(that.duration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, duration, amplifier, ambient, particles, icon);
    }

    @Override
    public String toString() {
        return "StatusEffect(key=" + key + ", duration=" + duration + ", amplifier=" + amplifier + ", ambient="
            + ambient + ", particles=" + particles + ", icon=" + icon + ")";
    }
}
