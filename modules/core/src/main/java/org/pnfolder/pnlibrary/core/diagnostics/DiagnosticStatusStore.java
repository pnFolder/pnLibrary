package org.pnfolder.pnlibrary.core.diagnostics;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.core.diagnostics.redaction.DiagnosticValueSanitizer;

/** Latest component status values for one plugin. */
public final class DiagnosticStatusStore {
    private static final int MAXIMUM_STATE_LENGTH = 64;
    private static final int MAXIMUM_DETAIL_LENGTH = 2_048;

    private final DiagnosticValueSanitizer sanitizer;
    private final Map<String, Map<String, Object>> statuses = new ConcurrentHashMap<String, Map<String, Object>>();

    /** Creates a store whose values pass through {@code sanitizer}. */
    public DiagnosticStatusStore(@NotNull DiagnosticValueSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    /** Replaces the status of {@code component}. */
    public void update(@NotNull String component, @NotNull String state, @NotNull String detail,
                       @NotNull Map<String, Object> fields) {
        Map<String, Object> status = new LinkedHashMap<String, Object>();
        status.put("state", sanitizer.text(state, MAXIMUM_STATE_LENGTH));
        status.put("detail", sanitizer.text(detail, MAXIMUM_DETAIL_LENGTH));
        status.put("updatedUtc", Instant.now().toString());
        status.put("fields", sanitizer.map(fields));
        statuses.put(sanitizer.key(component), status);
    }

    /** Forgets the status of {@code component}. */
    public void remove(@NotNull String component) {
        statuses.remove(sanitizer.key(component));
    }

    /** A copy of every current status, keyed by component. */
    @NotNull
    public Map<String, Map<String, Object>> snapshot() {
        return new LinkedHashMap<String, Map<String, Object>>(statuses);
    }
}
