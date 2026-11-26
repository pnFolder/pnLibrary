package org.pnfolder.pnlibrary.api.scenario.actions.builtin;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult;
import org.pnfolder.pnlibrary.api.world.StatusEffect;
import org.pnfolder.pnlibrary.api.world.WorldKeys;

import java.time.Duration;
import java.util.Objects;

/** Gives the player a temporary potion effect. */
public final class EffectAction implements Action {
    private final String key;
    private final Duration duration;
    private final int amplifier;
    private final boolean ambient;
    private final boolean particles;
    private final boolean icon;

    /** Creates the action with every setting at its default. */
    public EffectAction() {
        this("speed", Duration.ofSeconds(30), 0, true, true, true);
    }

    /** Creates it with the first 1 setting; the rest keep their defaults. */
    public EffectAction(@NotNull String key) {
        this(key, Duration.ofSeconds(30), 0, true, true, true);
    }

    /** Creates it with the first 2 settings; the rest keep their defaults. */
    public EffectAction(@NotNull String key, @NotNull Duration duration) {
        this(key, duration, 0, true, true, true);
    }

    /** Creates it with the first 3 settings; the rest keep their defaults. */
    public EffectAction(@NotNull String key, @NotNull Duration duration, int amplifier) {
        this(key, duration, amplifier, true, true, true);
    }

    /** Creates it with the first 4 settings; the rest keep their defaults. */
    public EffectAction(@NotNull String key, @NotNull Duration duration, int amplifier, boolean ambient) {
        this(key, duration, amplifier, ambient, true, true);
    }

    /** Creates it with the first 5 settings; the rest keep their defaults. */
    public EffectAction(@NotNull String key, @NotNull Duration duration, int amplifier, boolean ambient, boolean particles) {
        this(key, duration, amplifier, ambient, particles, true);
    }

    /** Creates it with every setting given. */
    public EffectAction(@NotNull String key, @NotNull Duration duration, int amplifier, boolean ambient, boolean particles, boolean icon) {
        this.key = Objects.requireNonNull(key, "key");
        this.duration = duration;
        this.amplifier = amplifier;
        this.ambient = ambient;
        this.particles = particles;
        this.icon = icon;
    }

    /** Effect key. */
    @NotNull
    public String getKey() {
        return key;
    }

    /** How long it lasts. */
    @NotNull
    public Duration getDuration() {
        return duration;
    }

    /** Zero-based level, {@code 0} is level I. */
    public int getAmplifier() {
        return amplifier;
    }

    /** Whether it counts as ambient. */
    public boolean isAmbient() {
        return ambient;
    }

    /** Whether its particles show. */
    public boolean isParticles() {
        return particles;
    }

    /** Whether its icon shows. */
    public boolean isIcon() {
        return icon;
    }

    @NotNull
    @Override
    public ActionResult run(@NotNull ActionContext context) {
        StatusEffect effect = new StatusEffect(WorldKeys.minecraftKey(key), duration, amplifier, ambient, particles, icon);
        return OutputActions.warnUnless(context, context.getPlayer().addEffect(effect), "Effect " + key);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EffectAction)) {
            return false;
        }
        EffectAction that = (EffectAction) other;
        return Objects.equals(key, that.key)
                && Objects.equals(duration, that.duration)
                && amplifier == that.amplifier
                && ambient == that.ambient
                && particles == that.particles
                && icon == that.icon;
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, duration, amplifier, ambient, particles, icon);
    }

    @NotNull
    @Override
    public String toString() {
        return "EffectAction(key=" + key + ", duration=" + duration + ", amplifier=" + amplifier + ", ambient=" + ambient + ", particles=" + particles + ", icon=" + icon + ")";
    }
}
