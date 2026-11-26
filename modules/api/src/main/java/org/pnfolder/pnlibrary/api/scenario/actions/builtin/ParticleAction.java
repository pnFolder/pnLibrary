package org.pnfolder.pnlibrary.api.scenario.actions.builtin;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult;
import org.pnfolder.pnlibrary.api.world.Particle;
import org.pnfolder.pnlibrary.api.world.WorldKeys;

import java.util.Objects;

/** Spawns particles around the player. */
public final class ParticleAction implements Action {
    private final String key;
    private final int count;
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final double speed;

    /** Creates the action with every setting at its default. */
    public ParticleAction() {
        this("flame", 1, 0.0, 0.0, 0.0, 0.0);
    }

    /** Creates it with the first 1 setting; the rest keep their defaults. */
    public ParticleAction(@NotNull String key) {
        this(key, 1, 0.0, 0.0, 0.0, 0.0);
    }

    /** Creates it with the first 2 settings; the rest keep their defaults. */
    public ParticleAction(@NotNull String key, int count) {
        this(key, count, 0.0, 0.0, 0.0, 0.0);
    }

    /** Creates it with the first 3 settings; the rest keep their defaults. */
    public ParticleAction(@NotNull String key, int count, double offsetX) {
        this(key, count, offsetX, 0.0, 0.0, 0.0);
    }

    /** Creates it with the first 4 settings; the rest keep their defaults. */
    public ParticleAction(@NotNull String key, int count, double offsetX, double offsetY) {
        this(key, count, offsetX, offsetY, 0.0, 0.0);
    }

    /** Creates it with the first 5 settings; the rest keep their defaults. */
    public ParticleAction(@NotNull String key, int count, double offsetX, double offsetY, double offsetZ) {
        this(key, count, offsetX, offsetY, offsetZ, 0.0);
    }

    /** Creates it with every setting given. */
    public ParticleAction(@NotNull String key, int count, double offsetX, double offsetY, double offsetZ, double speed) {
        this.key = key;
        this.count = count;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.speed = speed;
    }

    /** Particle key. */
    @NotNull
    public String getKey() {
        return key;
    }

    /** How many. */
    public int getCount() {
        return count;
    }

    /** Spread along X. */
    public double getOffsetX() {
        return offsetX;
    }

    /** Spread along Y. */
    public double getOffsetY() {
        return offsetY;
    }

    /** Spread along Z. */
    public double getOffsetZ() {
        return offsetZ;
    }

    /** Speed or particle-specific data. */
    public double getSpeed() {
        return speed;
    }

    @NotNull
    @Override
    public ActionResult run(@NotNull ActionContext context) {
        Particle particle = new Particle(WorldKeys.minecraftKey(key), count, offsetX, offsetY, offsetZ, speed);
        return OutputActions.warnUnless(context, context.getPlayer().spawnParticle(particle), "Particle " + key);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ParticleAction)) {
            return false;
        }
        ParticleAction that = (ParticleAction) other;
        return Objects.equals(key, that.key)
                && count == that.count
                && Double.compare(offsetX, that.offsetX) == 0
                && Double.compare(offsetY, that.offsetY) == 0
                && Double.compare(offsetZ, that.offsetZ) == 0
                && Double.compare(speed, that.speed) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, count, offsetX, offsetY, offsetZ, speed);
    }

    @NotNull
    @Override
    public String toString() {
        return "ParticleAction(key=" + key + ", count=" + count + ", offsetX=" + offsetX + ", offsetY=" + offsetY + ", offsetZ=" + offsetZ + ", speed=" + speed + ")";
    }
}
