package org.pnfolder.pnlibrary.core.settings;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** A setting with its value now; {@link #getChanged()} says whether an administrator set it, as opposed to the default. */
public final class SettingValue {
    private final SettingSpec spec;
    private final String current;
    private final boolean changed;

    /** Creates the pair. */
    public SettingValue(@NotNull SettingSpec spec, @NotNull String current, boolean changed) {
        this.spec = spec;
        this.current = current;
        this.changed = changed;
    }

    /** The setting. */
    public @NotNull SettingSpec getSpec() {
        return spec;
    }

    /** The value now, as text. */
    public @NotNull String getCurrent() {
        return current;
    }

    /** Whether an administrator set the value. */
    public boolean getChanged() {
        return changed;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SettingValue)) {
            return false;
        }
        SettingValue that = (SettingValue) other;
        return changed == that.changed && spec.equals(that.spec) && current.equals(that.current);
    }

    @Override
    public int hashCode() {
        return Objects.hash(spec, current, changed);
    }

    @Override
    public String toString() {
        return "SettingValue(spec=" + spec + ", current=" + current + ", changed=" + changed + ")";
    }
}
