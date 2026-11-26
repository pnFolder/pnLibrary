package org.pnfolder.pnlibrary.core.settings;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** What changed when a setting was set or reset. */
public final class SettingChange {
    private final SettingSpec spec;
    private final String before;
    private final String after;

    /** Creates the record; the values are as text. */
    public SettingChange(@NotNull SettingSpec spec, @NotNull String before, @NotNull String after) {
        this.spec = spec;
        this.before = before;
        this.after = after;
    }

    /** The setting. */
    public @NotNull SettingSpec getSpec() {
        return spec;
    }

    /** The value before. */
    public @NotNull String getBefore() {
        return before;
    }

    /** The value after. */
    public @NotNull String getAfter() {
        return after;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SettingChange)) {
            return false;
        }
        SettingChange that = (SettingChange) other;
        return spec.equals(that.spec) && before.equals(that.before) && after.equals(that.after);
    }

    @Override
    public int hashCode() {
        return Objects.hash(spec, before, after);
    }

    @Override
    public String toString() {
        return "SettingChange(spec=" + spec + ", before=" + before + ", after=" + after + ")";
    }
}
