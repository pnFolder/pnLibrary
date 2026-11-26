package org.pnfolder.pnlibrary.core.observability;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticLevel;
import org.pnfolder.pnlibrary.api.observability.observation.ObservationLevel;

import java.util.LinkedHashMap;
import java.util.Map;

/** Conversions between the diagnostics vocabulary and the observation vocabulary. */
public final class ObservationConversions {
    private ObservationConversions() {
    }

    /** The observation level that corresponds to a diagnostic level. */
    @NotNull
    public static ObservationLevel toObservationLevel(@NotNull DiagnosticLevel level) {
        switch (level) {
            case INFO:
                return ObservationLevel.INFO;
            case WARNING:
                return ObservationLevel.WARNING;
            case ERROR:
                return ObservationLevel.ERROR;
            default:
                throw new IllegalArgumentException("Unsupported diagnostic level: " + level);
        }
    }

    /** Turns every value into text; a missing value becomes {@code "null"}. */
    @NotNull
    public static Map<String, String> toStringValues(@NotNull Map<String, ?> fields) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : fields.entrySet()) {
            Object value = entry.getValue();
            values.put(entry.getKey(), value == null ? "null" : value.toString());
        }
        return values;
    }
}
