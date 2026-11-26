package org.pnfolder.pnlibrary.api.world;

import java.util.Objects;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable description of a particle burst around a player. The key is an Adventure key for the same reason as
 * {@link StatusEffect#getKey()}.
 *
 * <pre>{@code
 * player.spawnParticle(new Particle(Key.key("minecraft:flame"), 20, 0.5, 1.0, 0.5, 0.0));
 * }</pre>
 */
public final class Particle {
    private final Key key;
    private final int count;
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final double speed;

    /**
     * @param key particle key, such as {@code minecraft:flame}
     * @param count how many particles to spawn; {@code 0} is a no-op
     * @param offsetX maximum spread along the X axis
     * @param offsetY maximum spread along the Y axis
     * @param offsetZ maximum spread along the Z axis
     * @param speed particle speed, or extra data for particle types that use it
     */
    public Particle(@NotNull Key key, int count, double offsetX, double offsetY, double offsetZ, double speed) {
        if (count < 0) {
            throw new IllegalArgumentException("Particle count must not be negative");
        }
        this.key = Objects.requireNonNull(key, "key");
        this.count = count;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.speed = speed;
    }

    /** One particle of {@code key} with no spread and no speed. */
    public Particle(@NotNull Key key) {
        this(key, 1, 0.0, 0.0, 0.0, 0.0);
    }

    /** {@code count} particles of {@code key} with no spread and no speed. */
    public Particle(@NotNull Key key, int count) {
        this(key, count, 0.0, 0.0, 0.0, 0.0);
    }

    /** The particle key, such as {@code minecraft:flame}. */
    public @NotNull Key getKey() {
        return key;
    }

    /** How many particles to spawn; {@code 0} is a no-op. */
    public int getCount() {
        return count;
    }

    /** The maximum spread along the X axis. */
    public double getOffsetX() {
        return offsetX;
    }

    /** The maximum spread along the Y axis. */
    public double getOffsetY() {
        return offsetY;
    }

    /** The maximum spread along the Z axis. */
    public double getOffsetZ() {
        return offsetZ;
    }

    /** The particle speed, or extra data for particle types that use it. */
    public double getSpeed() {
        return speed;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Particle)) {
            return false;
        }
        Particle that = (Particle) other;
        return count == that.count && Double.compare(offsetX, that.offsetX) == 0
            && Double.compare(offsetY, that.offsetY) == 0 && Double.compare(offsetZ, that.offsetZ) == 0
            && Double.compare(speed, that.speed) == 0 && key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, count, offsetX, offsetY, offsetZ, speed);
    }

    @Override
    public String toString() {
        return "Particle(key=" + key + ", count=" + count + ", offsetX=" + offsetX + ", offsetY=" + offsetY
            + ", offsetZ=" + offsetZ + ", speed=" + speed + ")";
    }
}
