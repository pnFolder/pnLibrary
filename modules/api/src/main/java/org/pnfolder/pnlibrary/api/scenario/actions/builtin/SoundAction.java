package org.pnfolder.pnlibrary.api.scenario.actions.builtin;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionTarget;

import java.util.Objects;

/** Plays a sound. */
public final class SoundAction implements Action {
    private final String key;
    private final Sound.Source source;
    private final float volume;
    private final float pitch;
    private final ActionTarget target;

    /** Creates the action with every setting at its default. */
    public SoundAction() {
        this("minecraft:entity.experience_orb.pickup", Sound.Source.MASTER, 1f, 1f, ActionTarget.PLAYER);
    }

    /** Creates it with the first 1 setting; the rest keep their defaults. */
    public SoundAction(@NotNull String key) {
        this(key, Sound.Source.MASTER, 1f, 1f, ActionTarget.PLAYER);
    }

    /** Creates it with the first 2 settings; the rest keep their defaults. */
    public SoundAction(@NotNull String key, @NotNull Sound.Source source) {
        this(key, source, 1f, 1f, ActionTarget.PLAYER);
    }

    /** Creates it with the first 3 settings; the rest keep their defaults. */
    public SoundAction(@NotNull String key, @NotNull Sound.Source source, float volume) {
        this(key, source, volume, 1f, ActionTarget.PLAYER);
    }

    /** Creates it with the first 4 settings; the rest keep their defaults. */
    public SoundAction(@NotNull String key, @NotNull Sound.Source source, float volume, float pitch) {
        this(key, source, volume, pitch, ActionTarget.PLAYER);
    }

    /** Creates it with every setting given. */
    public SoundAction(@NotNull String key, @NotNull Sound.Source source, float volume, float pitch, @NotNull ActionTarget target) {
        this.key = key;
        this.source = source;
        this.volume = volume;
        this.pitch = pitch;
        this.target = target;
    }

    /** Namespaced sound key. */
    @NotNull
    public String getKey() {
        return key;
    }

    /** Volume category on the client. */
    @NotNull
    public Sound.Source getSource() {
        return source;
    }

    /** Relative volume. */
    public float getVolume() {
        return volume;
    }

    /** Pitch multiplier. */
    public float getPitch() {
        return pitch;
    }

    /** Who hears it. */
    @NotNull
    public ActionTarget getTarget() {
        return target;
    }

    @NotNull
    @Override
    public ActionResult run(@NotNull ActionContext context) {
        context.target(target).playSound(Sound.sound(Key.key(key), source, volume, pitch));
        return ActionResult.Done.INSTANCE;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SoundAction)) {
            return false;
        }
        SoundAction that = (SoundAction) other;
        return Objects.equals(key, that.key)
                && Objects.equals(source, that.source)
                && Float.compare(volume, that.volume) == 0
                && Float.compare(pitch, that.pitch) == 0
                && Objects.equals(target, that.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, source, volume, pitch, target);
    }

    @NotNull
    @Override
    public String toString() {
        return "SoundAction(key=" + key + ", source=" + source + ", volume=" + volume + ", pitch=" + pitch + ", target=" + target + ")";
    }
}
