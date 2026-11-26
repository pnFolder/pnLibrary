package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * A setting that does not work yet was changed: the plugin has to take it over ({@link ConfigApply#RELOAD}) or the
 * server has to restart ({@link ConfigApply#RESTART}).
 */
public final class ReloadRequest {
    private final ConfigId id;
    private final ConfigApply apply;
    private final List<ConfigSetting> settings;

    /**
     * Creates a request.
     *
     * @param id the file
     * @param apply the strongest requirement among the settings
     * @param settings the settings that were changed, with their new values
     */
    public ReloadRequest(@Nullable ConfigId id, @NotNull ConfigApply apply, @NotNull List<ConfigSetting> settings) {
        this.id = id;
        this.apply = apply;
        this.settings = settings;
    }

    /** The file. */
    @Nullable
    public ConfigId getId() {
        return id;
    }

    /** The strongest requirement among the settings. */
    @NotNull
    public ConfigApply getApply() {
        return apply;
    }

    /** The settings that were changed, with their new values. */
    @NotNull
    public List<ConfigSetting> getSettings() {
        return settings;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ReloadRequest)) {
            return false;
        }
        ReloadRequest that = (ReloadRequest) other;
        return Objects.equals(id, that.id) && apply == that.apply && settings.equals(that.settings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, apply, settings);
    }

    @Override
    public String toString() {
        return "ReloadRequest(id=" + id + ", apply=" + apply + ", settings=" + settings + ")";
    }
}
