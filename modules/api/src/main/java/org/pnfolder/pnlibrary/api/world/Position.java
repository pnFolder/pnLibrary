package org.pnfolder.pnlibrary.api.world;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A point in a world of a game server.
 *
 * <pre>{@code
 * Position spawn = new Position("world", 0.0, 64.0, 0.0);
 * Double blocks = player.position() == null ? null : player.position().distanceTo(spawn);
 * }</pre>
 */
public final class Position {
    private final String world;
    private final double x;
    private final double y;
    private final double z;

    /**
     * @param world name of the world
     * @param x east-west coordinate
     * @param y height
     * @param z north-south coordinate
     */
    public Position(@NotNull String world, double x, double y, double z) {
        this.world = Objects.requireNonNull(world, "world");
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** The name of the world. */
    public @NotNull String getWorld() {
        return world;
    }

    /** The east-west coordinate. */
    public double getX() {
        return x;
    }

    /** The height. */
    public double getY() {
        return y;
    }

    /** The north-south coordinate. */
    public double getZ() {
        return z;
    }

    /** The distance in blocks to {@code other}, or {@code null} when the two points are in different worlds. */
    public @Nullable Double distanceTo(@NotNull Position other) {
        if (!world.equalsIgnoreCase(other.world)) {
            return null;
        }
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Position)) {
            return false;
        }
        Position that = (Position) other;
        return Double.compare(x, that.x) == 0 && Double.compare(y, that.y) == 0 && Double.compare(z, that.z) == 0
            && world.equals(that.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, y, z);
    }

    @Override
    public String toString() {
        return "Position(world=" + world + ", x=" + x + ", y=" + y + ", z=" + z + ")";
    }
}
