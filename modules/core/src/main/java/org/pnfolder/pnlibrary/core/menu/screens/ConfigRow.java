package org.pnfolder.pnlibrary.core.menu.screens;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.ConfigApply;

/** One configuration file in the menu list. */
public final class ConfigRow {
    private final String id;
    private final String model;
    private final boolean loaded;
    private final boolean readOnly;
    private final int editable;
    private final ConfigApply pending;

    /**
     * @param editable how many settings of the file may be changed in game
     * @param pending what the changes made since the last read still wait for, or null
     */
    public ConfigRow(@NotNull String id, @NotNull String model, boolean loaded, boolean readOnly, int editable,
                     @Nullable ConfigApply pending) {
        this.id = id;
        this.model = model;
        this.loaded = loaded;
        this.readOnly = readOnly;
        this.editable = editable;
        this.pending = pending;
    }

    public @NotNull String getId() {
        return id;
    }

    public @NotNull String getModel() {
        return model;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public int getEditable() {
        return editable;
    }

    public @Nullable ConfigApply getPending() {
        return pending;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ConfigRow)) {
            return false;
        }
        ConfigRow that = (ConfigRow) other;
        return id.equals(that.id) && model.equals(that.model) && loaded == that.loaded && readOnly == that.readOnly
            && editable == that.editable && pending == that.pending;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, model, loaded, readOnly, editable, pending);
    }

    @Override
    public String toString() {
        return "ConfigRow(id=" + id + ", editable=" + editable + ")";
    }
}
