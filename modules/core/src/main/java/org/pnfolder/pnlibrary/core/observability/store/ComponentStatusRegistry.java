package org.pnfolder.pnlibrary.core.observability.store;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.observability.report.ComponentStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Latest reported status of every plugin component. */
public final class ComponentStatusRegistry {
    private final Object lock = new Object();
    private final Map<List<String>, ComponentStatus> statuses = new LinkedHashMap<>();

    /** Replaces the status of the same plugin component and returns the new one. */
    @NotNull
    public ComponentStatus update(@NotNull ComponentStatus status) {
        synchronized (lock) {
            statuses.put(Arrays.asList(status.getPlugin(), status.getComponent()), status);
            return status;
        }
    }

    /** The latest status of every component, oldest component first. */
    @NotNull
    public List<ComponentStatus> snapshot() {
        synchronized (lock) {
            return new ArrayList<>(statuses.values());
        }
    }
}
