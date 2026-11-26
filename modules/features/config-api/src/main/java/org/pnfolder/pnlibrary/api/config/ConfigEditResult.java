package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** The result of changing one setting: {@link #getSetting()} holds the new value, {@link #getPrevious()} the old one. */
public final class ConfigEditResult {
    private final ConfigSetting setting;
    private final String previous;

    /** Creates a result. */
    public ConfigEditResult(@NotNull ConfigSetting setting, @Nullable String previous) {
        this.setting = setting;
        this.previous = previous;
    }

    /** The setting with its new value. */
    @NotNull
    public ConfigSetting getSetting() {
        return setting;
    }

    /** The old value as text, or {@code null}. */
    @Nullable
    public String getPrevious() {
        return previous;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ConfigEditResult && setting.equals(((ConfigEditResult) other).setting)
                && Objects.equals(previous, ((ConfigEditResult) other).previous);
    }

    @Override
    public int hashCode() {
        return Objects.hash(setting, previous);
    }

    @Override
    public String toString() {
        return "ConfigEditResult(setting=" + setting + ", previous=" + previous + ")";
    }
}
