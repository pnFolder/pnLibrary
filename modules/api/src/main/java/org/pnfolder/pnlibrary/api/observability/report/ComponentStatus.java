package org.pnfolder.pnlibrary.api.observability.report;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Describes the latest known state of one runtime component.
 *
 * <p>A new value for the same plugin and component replaces the previous value. Status entries are intended for
 * current state; use an {@code Observation} when a chronological event must remain in the diagnostic history.
 */
public final class ComponentStatus {
    private final String plugin;
    private final String component;
    private final String state;
    private final String detail;
    private final Map<String, String> data;
    private final long updatedAt;

    /** A status without detail and data, stamped with the current time. */
    public ComponentStatus(@NotNull String plugin, @NotNull String component, @NotNull String state) {
        this(plugin, component, state, "");
    }

    /** A status without data, stamped with the current time. */
    public ComponentStatus(@NotNull String plugin, @NotNull String component, @NotNull String state, @NotNull String detail) {
        this(plugin, component, state, detail, Collections.emptyMap());
    }

    /** A status stamped with the current time. */
    public ComponentStatus(@NotNull String plugin, @NotNull String component, @NotNull String state, @NotNull String detail,
                           @NotNull Map<String, String> data) {
        this(plugin, component, state, detail, data, System.currentTimeMillis());
    }

    /**
     * Creates a status.
     *
     * @param plugin logical name of the plugin that owns the component
     * @param component stable component identifier, such as {@code database}
     * @param state short machine-readable state, such as {@code ready} or {@code degraded}
     * @param detail optional human-readable explanation of the current state
     * @param data additional bounded metadata for support reports
     * @param updatedAt creation time expressed as Unix epoch milliseconds
     */
    public ComponentStatus(@NotNull String plugin, @NotNull String component, @NotNull String state, @NotNull String detail,
                           @NotNull Map<String, String> data, long updatedAt) {
        this.plugin = Objects.requireNonNull(plugin);
        this.component = Objects.requireNonNull(component);
        this.state = Objects.requireNonNull(state);
        this.detail = Objects.requireNonNull(detail);
        this.data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
        this.updatedAt = updatedAt;
    }

    /** The logical name of the plugin that owns the component. */
    @NotNull
    public String getPlugin() {
        return plugin;
    }

    /** The stable component identifier. */
    @NotNull
    public String getComponent() {
        return component;
    }

    /** The short machine-readable state. */
    @NotNull
    public String getState() {
        return state;
    }

    /** The human-readable explanation of the current state; may be empty. */
    @NotNull
    public String getDetail() {
        return detail;
    }

    /** The additional bounded metadata. */
    @NotNull
    public Map<String, String> getData() {
        return data;
    }

    /** The creation time expressed as Unix epoch milliseconds. */
    public long getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ComponentStatus)) {
            return false;
        }
        ComponentStatus that = (ComponentStatus) other;
        return updatedAt == that.updatedAt && plugin.equals(that.plugin) && component.equals(that.component)
                && state.equals(that.state) && detail.equals(that.detail) && data.equals(that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(plugin, component, state, detail, data, updatedAt);
    }

    @NotNull
    @Override
    public String toString() {
        return "ComponentStatus(plugin=" + plugin + ", component=" + component + ", state=" + state + ", detail=" + detail
                + ", data=" + data + ", updatedAt=" + updatedAt + ")";
    }
}
